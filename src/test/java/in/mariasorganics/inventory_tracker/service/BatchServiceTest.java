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
}
