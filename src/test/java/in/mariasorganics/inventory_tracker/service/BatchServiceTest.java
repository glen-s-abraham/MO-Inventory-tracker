package in.mariasorganics.inventory_tracker.service;

import in.mariasorganics.inventory_tracker.model.Batch;
import in.mariasorganics.inventory_tracker.model.Stock;
import in.mariasorganics.inventory_tracker.repository.BatchRepository;
import in.mariasorganics.inventory_tracker.repository.StockRepository;
import in.mariasorganics.inventory_tracker.repository.StockTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BatchServiceTest {

    @Mock
    private BatchRepository batchRepository;
    @Mock
    private StockRepository stockRepository;
    @Mock
    private StockTransactionRepository transactionRepository;
    @Mock
    private ConfigService configService;

    @InjectMocks
    private BatchService batchService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        Map<String, Double> configs = Map.of(
            "DARK_ROOM_CAPACITY", 900.0,
            "PELLET_KG_PER_BAG", 1.0,
            "SPAWN_USAGE_PER_BAG_G", 150.0,
            "INOCULATION_PERIOD_DAYS", 18.0
        );
        when(configService.getConfigMap()).thenReturn(configs);
    }

    @Test
    void createBatch_ShouldSucceed_WhenStockAndCapacityAreAvailable() {
        LocalDate date = LocalDate.now();
        int bagCount = 10;
        
        Stock pellets = new Stock("PELLETS", "KG");
        pellets.setPhysicalQuantity(100.0);
        Stock spawn = new Stock("SPAWN", "G");
        spawn.setPhysicalQuantity(2000.0);

        when(batchRepository.countActiveBags()).thenReturn(100L);
        when(stockRepository.findByItemName("PELLETS")).thenReturn(Optional.of(pellets));
        when(stockRepository.findByItemName("SPAWN")).thenReturn(Optional.of(spawn));
        when(batchRepository.save(any(Batch.class))).thenAnswer(i -> i.getArguments()[0]);

        Batch result = batchService.createBatch(date, bagCount);

        assertNotNull(result);
        assertEquals(bagCount, result.getBagCount());
        assertEquals(date.plusDays(18), result.getTargetExitDate());
        assertEquals(90.0, pellets.getPhysicalQuantity()); // 100 - (10 * 1.0)
        assertEquals(500.0, spawn.getPhysicalQuantity()); // 2000 - (10 * 150.0)
        
        verify(batchRepository).save(any(Batch.class));
        verify(transactionRepository, times(2)).save(any());
    }

    @Test
    void createBatch_ShouldFail_WhenCapacityExceeded() {
        when(batchRepository.countActiveBags()).thenReturn(895L);
        
        IllegalStateException exception = assertThrows(IllegalStateException.class, 
            () -> batchService.createBatch(LocalDate.now(), 10));
        
        assertTrue(exception.getMessage().contains("capacity exceeded"));
    }

    @Test
    void createBatch_ShouldFail_WhenStockInsufficient() {
        Stock pellets = new Stock("PELLETS", "KG");
        pellets.setPhysicalQuantity(5.0); // Only 5kg, need 10kg
        
        when(batchRepository.countActiveBags()).thenReturn(0L);
        when(stockRepository.findByItemName("PELLETS")).thenReturn(Optional.of(pellets));

        IllegalStateException exception = assertThrows(IllegalStateException.class, 
            () -> batchService.createBatch(LocalDate.now(), 10));
        
        assertTrue(exception.getMessage().contains("Insufficient stock for PELLETS"));
    }

    @Test
    void checkoutBags_ShouldHandleFIFOCorrectly() {
        Batch b1 = new Batch("B1", LocalDate.now().minusDays(2), 10, LocalDate.now());
        Batch b2 = new Batch("B2", LocalDate.now().minusDays(1), 10, LocalDate.now());
        
        when(batchRepository.countActiveBags()).thenReturn(20L);
        when(batchRepository.findByStatusOrderByInoculationDateAsc(Batch.BatchStatus.ACTIVE))
                .thenReturn(java.util.List.of(b1, b2));

        // Checkout 15 bags
        batchService.checkoutBags(15);

        // B1 should be COMPLETED (10 bags)
        assertEquals(Batch.BatchStatus.COMPLETED, b1.getStatus());
        
        // B2 should have been split. The original B2 remains ACTIVE but with 5 bags.
        // Wait, my logic: 
        // B1 (10) <= 15 -> B1 COMPLETED, remaining = 5
        // B2 (10) > 5 -> Split B2: 
        //   - New completed record for 5 bags
        //   - B2 original becomes ACTIVE (10 - 5 = 5)
        
        assertEquals(Batch.BatchStatus.ACTIVE, b2.getStatus());
        assertEquals(5, b2.getBagCount());
        
        verify(batchRepository, times(3)).save(any(Batch.class)); // B1, NewSplit, B2
    }

    @Test
    void checkoutBags_ShouldFail_WhenOverCheckout() {
        when(batchRepository.countActiveBags()).thenReturn(10L);
        
        IllegalStateException exception = assertThrows(IllegalStateException.class, 
            () -> batchService.checkoutBags(15));
        
        assertTrue(exception.getMessage().contains("Only 10 active bags"));
    }

    @Test
    void deleteActiveBatch_ShouldRevertStock() {
        Batch b = new Batch("B1", LocalDate.now(), 10, LocalDate.now());
        b.setId(1L);
        Stock pellets = new Stock("PELLETS", "KG");
        pellets.setPhysicalQuantity(50.0);
        
        when(batchRepository.findById(1L)).thenReturn(Optional.of(b));
        when(stockRepository.findByItemName("PELLETS")).thenReturn(Optional.of(pellets));
        when(stockRepository.findByItemName("SPAWN")).thenReturn(Optional.of(new Stock("SPAWN", "G")));

        batchService.deleteBatch(1L);

        assertEquals(60.0, pellets.getPhysicalQuantity()); // 50 + 10
        verify(batchRepository).delete(b);
        verify(transactionRepository, times(2)).save(any());
    }

    @Test
    void updateBatchCount_ShouldIncreaseDeduction_WhenDiffPositive() {
        Batch b = new Batch("B1", LocalDate.now(), 10, LocalDate.now());
        b.setId(1L);
        Stock pellets = new Stock("PELLETS", "KG");
        pellets.setPhysicalQuantity(50.0);
        
        when(batchRepository.findById(1L)).thenReturn(Optional.of(b));
        when(batchRepository.countActiveBags()).thenReturn(100L);
        when(stockRepository.findByItemName("PELLETS")).thenReturn(Optional.of(pellets));
        Stock spawn = new Stock("SPAWN", "G");
        spawn.setPhysicalQuantity(2000.0);
        when(stockRepository.findByItemName("SPAWN")).thenReturn(Optional.of(spawn));

        batchService.updateBatchCount(1L, 15); // +5 bags

        assertEquals(45.0, pellets.getPhysicalQuantity()); // 50 - 5
        assertEquals(15, b.getBagCount());
    }

    @Test
    void revertToActive_ShouldFail_WhenCapacityFull() {
        Batch b = new Batch("B1", LocalDate.now(), 20, LocalDate.now());
        b.setStatus(Batch.BatchStatus.COMPLETED);
        b.setId(1L);

        when(batchRepository.findById(1L)).thenReturn(Optional.of(b));
        when(batchRepository.countActiveBags()).thenReturn(890L); // Capacity 900, adding 20 fails

        assertThrows(IllegalStateException.class, () -> batchService.revertToActive(1L));
    }
}
