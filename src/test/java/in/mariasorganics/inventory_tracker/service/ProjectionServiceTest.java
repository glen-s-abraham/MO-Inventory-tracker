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
import static org.junit.jupiter.api.Assertions.assertTrue;
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
        pellets.setReservedQuantity(0.0);
        when(stockRepository.findByItemName("PELLETS")).thenReturn(Optional.of(pellets));

        Stock spawn = new Stock("SPAWN", "G");
        spawn.setPhysicalQuantity(0.0);
        spawn.setReservedQuantity(0.0);
        when(stockRepository.findByItemName("SPAWN")).thenReturn(Optional.of(spawn));

        // Act
        Map<String, Object> results = projectionService.calculateProjection(orderDate, plannedBags, projectionDays, null);

        // Check bounds instead of strict equals due to dynamic `LocalDate.now()` inside the service loop.
        // During 25 days, there are typically 17-19 weekdays.
        // Assuming 18 weekdays: 18 * 10 = 180 base bags * 1.05 contamination factor = 189.0
        // We will assert it's between minimum (17 * 10 * 1.05 = 178.5) and maximum (19 * 10 * 1.05 = 199.5)
        Double result = (Double) results.get("pelletsToOrder");
        assertTrue(result >= 170.0 && result <= 200.0, "Result " + result + " should be dynamically valid for non-weekend day production");
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
        pellets.setReservedQuantity(0.0);
        when(stockRepository.findByItemName("PELLETS")).thenReturn(Optional.of(pellets));

        // Act
        Map<String, Object> results = projectionService.calculateProjection(orderDate, 0, 25, null);

        // Assert
        assertEquals(0.0, results.get("pelletsToOrder"));
    }
}
