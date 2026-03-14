package in.mariasorganics.inventory_tracker.service;

import in.mariasorganics.inventory_tracker.model.Stock;
import in.mariasorganics.inventory_tracker.repository.BatchRepository;
import in.mariasorganics.inventory_tracker.repository.StockRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Service
public class ProjectionService {

    private final BatchRepository batchRepository;
    private final StockRepository stockRepository;
    private final ConfigService configService;

    public ProjectionService(BatchRepository batchRepository, 
                             StockRepository stockRepository, 
                             ConfigService configService) {
        this.batchRepository = batchRepository;
        this.stockRepository = stockRepository;
        this.configService = configService;
    }

    public Map<String, Object> calculateProjection(LocalDate orderDate, Integer plannedBags, Integer projectionDays) {
        Map<String, Double> configs = configService.getConfigMap();
        double leadTimeDays = configs.getOrDefault("SUPPLIER_LEAD_TIME_DAYS", 15.0);
        double darkRoomCapacity = configs.getOrDefault("DARK_ROOM_CAPACITY", 900.0);
        double pelletPerBag = configs.getOrDefault("PELLET_KG_PER_BAG", 1.0);
        double spawnPerBag = configs.getOrDefault("SPAWN_USAGE_PER_BAG_G", 150.0);
        double dailyTarget = configs.getOrDefault("DAILY_PRODUCTION_TARGET", 18.0);
        double frequencyPerWeek = configs.getOrDefault("INOCULATIONS_PER_WEEK", 1.0);

        if (projectionDays == null) {
            projectionDays = configs.getOrDefault("MONTHLY_WORKING_DAYS", 25.0).intValue();
        }

        LocalDate arrivalDate = orderDate.plusDays((long) leadTimeDays);

        // 1. Space Throughput Calculation
        Long currentActiveBags = batchRepository.countActiveBags();
        if (currentActiveBags == null) currentActiveBags = 0L;

        // Exits between Now and Arrival (Lead Time)
        Long exitsInLeadTime = batchRepository.sumBagCountByStatusAndTargetExitDateBetween(LocalDate.now(), arrivalDate);
        if (exitsInLeadTime == null) exitsInLeadTime = 0L;

        // Exits during the Projection Window (after arrival)
        Long exitsInWindow = batchRepository.sumBagCountByStatusAndTargetExitDateBetween(arrivalDate.plusDays(1), arrivalDate.plusDays(projectionDays));
        if (exitsInWindow == null) exitsInWindow = 0L;

        double freeSlotsNow = darkRoomCapacity - currentActiveBags;
        double vacancyAtArrival = freeSlotsNow + exitsInLeadTime - (plannedBags != null ? plannedBags : 0);
        
        // Total Space Throughput in the Window (What we can physically fit)
        double totalThroughput = Math.max(0, vacancyAtArrival) + exitsInWindow;

        // 2. Stock Recommendation (Demand Based)
        double monthlyBags = dailyTarget * frequencyPerWeek * 4.33;
        double dailyBagsAvg = monthlyBags / 30.44;

        long daysUntilArrival = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), arrivalDate);
        double consumptionUntilArrival = Math.max(0, daysUntilArrival) * dailyBagsAvg;

        double availablePellets = getStockQuantity("PELLETS");
        double availableSpawn = getStockQuantity("SPAWN");

        double pelletsAtArrival = Math.max(0, availablePellets - (consumptionUntilArrival * pelletPerBag));
        double spawnAtArrival = Math.max(0, availableSpawn - (consumptionUntilArrival * spawnPerBag));

        // Unconstrained Demand
        double demandBags = projectionDays * dailyTarget;
        
        // Final Recommendation: min(Demand, Space Throughput)
        double recommendedBags = Math.min(demandBags, totalThroughput);

        double pelletsToOrder = (recommendedBags * pelletPerBag) - pelletsAtArrival;
        double spawnToOrder = (recommendedBags * spawnPerBag) - spawnAtArrival;

        Map<String, Object> results = new HashMap<>();
        results.put("orderDate", orderDate);
        results.put("arrivalDate", arrivalDate);
        results.put("projectionDays", projectionDays);
        results.put("currentActiveBags", currentActiveBags);
        results.put("bagsExiting", exitsInLeadTime);
        results.put("exitsInWindow", exitsInWindow);
        results.put("emptySlotsNow", freeSlotsNow);
        results.put("plannedBags", plannedBags);
        results.put("projectedVacancy", Math.max(0.0, vacancyAtArrival));
        results.put("totalThroughput", totalThroughput);
        results.put("pelletsToOrder", Math.round(Math.max(0.0, pelletsToOrder) * 100.0) / 100.0);
        results.put("spawnToOrder", Math.round(Math.max(0.0, spawnToOrder) * 100.0) / 100.0);
        results.put("pelletUnit", "KG");
        results.put("spawnUnit", "G");

        return results;
    }

    public int getSuggestedPlannedBags(LocalDate orderDate) {
        Map<String, Double> configs = configService.getConfigMap();
        double leadTimeDays = configs.getOrDefault("SUPPLIER_LEAD_TIME_DAYS", 15.0);
        double darkRoomCapacity = configs.getOrDefault("DARK_ROOM_CAPACITY", 900.0);
        double dailyTarget = configs.getOrDefault("DAILY_PRODUCTION_TARGET", 18.0);
        double frequencyPerWeek = configs.getOrDefault("INOCULATIONS_PER_WEEK", 1.0);
        double pelletPerBag = configs.getOrDefault("PELLET_KG_PER_BAG", 1.0);
        double spawnPerBag = configs.getOrDefault("SPAWN_USAGE_PER_BAG_G", 150.0);

        LocalDate arrivalDate = orderDate.plusDays((long) leadTimeDays);
        
        // 1. Space Constraint
        Long currentActive = batchRepository.countActiveBags();
        Long exitsInLeadTime = batchRepository.sumBagCountByStatusAndTargetExitDateBetween(LocalDate.now(), arrivalDate);
        double freeSlotsAtArrival = darkRoomCapacity - (currentActive != null ? currentActive : 0) + (exitsInLeadTime != null ? exitsInLeadTime : 0);

        // 2. Stock Constraint (Sustainability during lead time)
        double availablePellets = getStockQuantity("PELLETS");
        double availableSpawn = getStockQuantity("SPAWN");
        double stockSustaineableBags = Math.min(availablePellets / pelletPerBag, availableSpawn / spawnPerBag);

        // 3. Target Constaint (Planned production for the days)
        double weeks = leadTimeDays / 7.0;
        double targetBags = weeks * frequencyPerWeek * dailyTarget;

        // Suggested is the minimum of what we WANT, what we can FIT, and what we can AFFORD (stock-wise)
        return (int) Math.max(0, Math.floor(Math.min(targetBags, Math.min(freeSlotsAtArrival, stockSustaineableBags))));
    }

    private double getStockQuantity(String itemName) {
        return stockRepository.findByItemName(itemName.toUpperCase())
                .map(Stock::getAvailableQuantity)
                .orElse(0.0);
    }
}
