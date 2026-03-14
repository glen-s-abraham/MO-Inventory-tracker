package in.mariasorganics.inventory_tracker.service;

import in.mariasorganics.inventory_tracker.model.Stock;
import in.mariasorganics.inventory_tracker.repository.BatchRepository;
import in.mariasorganics.inventory_tracker.repository.StockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class ProjectionServiceTest {

    private ProjectionService projectionService;

    @Mock
    private BatchRepository batchRepository;

    @Mock
    private StockRepository stockRepository;

    @Mock
    private ConfigService configService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        projectionService = new ProjectionService(batchRepository, stockRepository, configService);
    }

    @Test
    void testCalculateProjection() {
        // Arrange
        LocalDate orderDate = LocalDate.now().plusDays(1); // Future order
        Integer plannedBags = 0;
        Integer projectionDays = 25;

        when(configService.getConfigMap()).thenReturn(Map.of(
                "SUPPLIER_LEAD_TIME_DAYS", 15.0,
                "DARK_ROOM_CAPACITY", 900.0,
                "PELLET_KG_PER_BAG", 1.0,
                "SPAWN_USAGE_PER_BAG_G", 150.0,
                "DAILY_PRODUCTION_TARGET", 10.0,
                "INOCULATIONS_PER_WEEK", 7.0,
                "MONTHLY_WORKING_DAYS", 25.0
        ));

        when(batchRepository.countActiveBags()).thenReturn(500L);
        when(batchRepository.sumBagCountByStatusAndTargetExitDateBetween(any(), any())).thenReturn(50L);

        Stock pellets = new Stock("PELLETS", "KG");
        pellets.setPhysicalQuantity(0.0);
        when(stockRepository.findByItemName("PELLETS")).thenReturn(Optional.of(pellets));

        Stock spawn = new Stock("SPAWN", "G");
        spawn.setPhysicalQuantity(0.0);
        when(stockRepository.findByItemName("SPAWN")).thenReturn(Optional.of(spawn));

        // Act
        Map<String, Object> results = projectionService.calculateProjection(orderDate, plannedBags, projectionDays);

        // Assert
        // Target = 25 days * 10 bags/day = 250 bags
        // Current Stock = 0, Depletion won't matter if it's 0 already
        // Pellets needed = 250 * 1.0 = 250.0
        assertEquals(250.0, results.get("pelletsToOrder"));
    }

    @Test
    void testNegativeOrderLogic() {
        // Arrange
        LocalDate orderDate = LocalDate.now();
        when(configService.getConfigMap()).thenReturn(Map.of(
                "SUPPLIER_LEAD_TIME_DAYS", 15.0,
                "DARK_ROOM_CAPACITY", 900.0,
                "PELLET_KG_PER_BAG", 1.0,
                "SPAWN_USAGE_PER_BAG_G", 150.0,
                "DAILY_PRODUCTION_TARGET", 10.0,
                "INOCULATIONS_PER_WEEK", 7.0,
                "MONTHLY_WORKING_DAYS", 25.0
        ));

        when(batchRepository.countActiveBags()).thenReturn(0L);
        when(batchRepository.sumBagCountByStatusAndTargetExitDateBetween(any(), any())).thenReturn(0L);

        // High stock (Enough for 250 bags)
        Stock pellets = new Stock("PELLETS", "KG");
        pellets.setPhysicalQuantity(500.0);
        when(stockRepository.findByItemName("PELLETS")).thenReturn(Optional.of(pellets));

        // Act
        Map<String, Object> results = projectionService.calculateProjection(orderDate, 0, 25);

        // Assert
        assertEquals(0.0, results.get("pelletsToOrder"));
    }
}
