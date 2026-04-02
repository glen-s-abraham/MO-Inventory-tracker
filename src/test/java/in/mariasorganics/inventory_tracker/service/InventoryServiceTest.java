package in.mariasorganics.inventory_tracker.service;

import in.mariasorganics.inventory_tracker.model.Batch;
import in.mariasorganics.inventory_tracker.model.Stock;
import in.mariasorganics.inventory_tracker.repository.BatchRepository;
import in.mariasorganics.inventory_tracker.repository.StockRepository;
import in.mariasorganics.inventory_tracker.repository.StockTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class InventoryServiceTest {

    private InventoryService inventoryService;

    @Mock
    private StockRepository stockRepository;

    @Mock
    private StockTransactionRepository transactionRepository;

    @Mock
    private BatchRepository batchRepository;

    @Mock
    private ConfigService configService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        inventoryService = new InventoryService(stockRepository, transactionRepository, batchRepository, configService);
    }

    @Test
    void testGetDashboardProjections() {
        // Arrange
        when(configService.getConfigMap()).thenReturn(Map.of(
                "SUPPLIER_LEAD_TIME_DAYS", 15.0,
                "DARK_ROOM_CAPACITY", 900.0,
                "PELLET_KG_PER_BAG", 1.0,
                "SPAWN_USAGE_PER_BAG_G", 150.0,
                "DAILY_PRODUCTION_TARGET", 18.0,
                "EXPECTED_CONTAMINATION_RATE", 0.05
        ));

        // Capacity and Active bags setup
        when(batchRepository.countActiveBags()).thenReturn(800L); // 100 free slots initially
        
        // Exits mock
        when(batchRepository.sumBagCountByStatusAndTargetExitDateBetween(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(50L); // 50 bags exiting in the window

        // Batches mock for digital twin simulation
        Batch batch = new Batch("MO-1", LocalDate.now().minusDays(10), 50, LocalDate.now().plusDays(5));
        when(batchRepository.findByStatusOrderByInoculationDateAsc(Batch.BatchStatus.ACTIVE))
                .thenReturn(Collections.singletonList(batch));

        // Stock mocks (Lots of stock available to avoid stockout limits)
        Stock pellets = new Stock("PELLETS", "KG");
        pellets.setPhysicalQuantity(5000.0);
        when(stockRepository.findByItemName("PELLETS")).thenReturn(Optional.of(pellets));

        Stock spawn = new Stock("SPAWN", "G");
        spawn.setPhysicalQuantity(500000.0);
        when(stockRepository.findByItemName("SPAWN")).thenReturn(Optional.of(spawn));

        // Act
        Map<String, Object> projections = inventoryService.getDashboardProjections(null);

        // Assert
        assertNotNull(projections);
        
        int capacity = (Integer) projections.get("capacity");
        assertEquals(900, capacity);
        
        int activeBags = (Integer) projections.get("activeBags");
        assertEquals(800, activeBags);
        
        int freeSlotsNow = (Integer) projections.get("freeSlotsNow");
        assertEquals(100, freeSlotsNow);
        
        int optimalRate = (Integer) projections.get("optimalRate");
        // Space throughput: (100 free slots + 50 exits in 15 days) / 15 days = 150 / 15 = 10
        // Target is 18, so optimal should be restricted to 10
        assertEquals(10, optimalRate);
        
        Map<String, Object> optimized = (Map<String, Object>) projections.get("optimized");
        int optimizedRate = (Integer) optimized.get("rate");
        assertEquals(10, optimizedRate);
    }
}
