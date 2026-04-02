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

    public Map<String, Object> calculateProjection(LocalDate orderDate, Integer plannedBags, Integer projectionDays, Double overrideLeadTime) {
        Map<String, Double> configs = configService.getConfigMap();
        double leadTimeDays = (overrideLeadTime != null) ? overrideLeadTime : configs.getOrDefault("SUPPLIER_LEAD_TIME_DAYS", 15.0);
        double darkRoomCapacity = configs.getOrDefault("DARK_ROOM_CAPACITY", 900.0);
        double pelletPerBag = configs.getOrDefault("PELLET_KG_PER_BAG", 1.0);
        double spawnPerBag = configs.getOrDefault("SPAWN_USAGE_PER_BAG_G", 150.0);
        double dailyTarget = configs.getOrDefault("DAILY_PRODUCTION_TARGET", 18.0);
        double frequencyPerWeek = configs.getOrDefault("INOCULATIONS_PER_WEEK", 1.0);

        if (projectionDays == null) {
            projectionDays = configs.getOrDefault("MONTHLY_WORKING_DAYS", 25.0).intValue();
        }

        double inoculationPeriodDays = configs.getOrDefault("INOCULATION_PERIOD_DAYS", 18.0);

        LocalDate arrivalDate = orderDate.plusDays((long) leadTimeDays);

        // 1 & 2. Digital Twin Simulation (Space & Stock)
        Long currentActiveBags = batchRepository.countActiveBags();
        if (currentActiveBags == null) currentActiveBags = 0L;

        // Fetch exit timeline for simulation
        java.util.List<in.mariasorganics.inventory_tracker.model.Batch> activeBatches = batchRepository.findByStatusOrderByInoculationDateAsc(in.mariasorganics.inventory_tracker.model.Batch.BatchStatus.ACTIVE);
        Map<LocalDate, Long> exitsTimeline = new HashMap<>();
        for (in.mariasorganics.inventory_tracker.model.Batch b : activeBatches) {
            LocalDate exitDate = b.getTargetExitDate();
            if (exitDate != null) {
                exitsTimeline.put(exitDate, exitsTimeline.getOrDefault(exitDate, 0L) + b.getBagCount());
            }
        }

        double simPellets = getStockQuantity("PELLETS");
        double simSpawn = getStockQuantity("SPAWN");
        double simOccupancy = currentActiveBags;
        
        // Include planned bags (manual override) immediately if provided
        if (plannedBags != null && plannedBags > 0) {
            simOccupancy += plannedBags;
            simPellets -= (plannedBags * pelletPerBag);
            simSpawn -= (plannedBags * spawnPerBag);
            
            // Add these planned bags to the exits timeline so they free up space later
            LocalDate plannedExitDate = LocalDate.now().plusDays((long)inoculationPeriodDays);
            exitsTimeline.put(plannedExitDate, exitsTimeline.getOrDefault(plannedExitDate, 0L) + plannedBags);
        }



        LocalDate simDate = LocalDate.now();
        long exitsInLeadTime = 0;
        long productionBagsUntilArrival = 0;
        boolean useDailyOverride = (plannedBags != null && plannedBags > 0);

        // Phase 1: Simulate up to Arrival Date
        while (simDate.isBefore(arrivalDate) || simDate.isEqual(arrivalDate)) {
            // Process exits
            Long exitingToday = exitsTimeline.get(simDate);
            if (exitingToday != null) {
                simOccupancy = Math.max(0, simOccupancy - exitingToday);
                if (simDate.isBefore(arrivalDate)) {
                    exitsInLeadTime += exitingToday;
                }
            }

            // Process production (Mon-Fri) if it's before arrival ONLY if user did not override
            if (!useDailyOverride && simDate.isBefore(arrivalDate)) {
                java.time.DayOfWeek dow = simDate.getDayOfWeek();
                boolean isProductionDay = dow != java.time.DayOfWeek.SATURDAY && dow != java.time.DayOfWeek.SUNDAY;
                
                if (isProductionDay && simOccupancy + dailyTarget <= darkRoomCapacity) {
                    if (simPellets >= dailyTarget * pelletPerBag && simSpawn >= dailyTarget * spawnPerBag) {
                        simPellets -= dailyTarget * pelletPerBag;
                        simSpawn -= dailyTarget * spawnPerBag;
                        simOccupancy += dailyTarget;
                        productionBagsUntilArrival += dailyTarget;
                        
                        // Register future exit for simulated production
                        LocalDate simExitDate = simDate.plusDays((long)inoculationPeriodDays);
                        exitsTimeline.put(simExitDate, exitsTimeline.getOrDefault(simExitDate, 0L) + (long)dailyTarget);
                    }
                }
            }
            simDate = simDate.plusDays(1);
        }

        double vacancyAtArrival = darkRoomCapacity - simOccupancy;

        // Phase 2: Simulate Window (Find theoretical throughput and demand)
        LocalDate windowEndDate = arrivalDate.plusDays(projectionDays);
        long exitsInWindow = 0;
        long windowDemandBags = 0;
        double windowSimOccupancy = Math.max(0, simOccupancy); // Assume taking delivery doesn't occupy dark room space

        while (simDate.isBefore(windowEndDate) || simDate.isEqual(windowEndDate)) {
            Long exitingToday = exitsTimeline.get(simDate);
            if (exitingToday != null) {
                windowSimOccupancy = Math.max(0, windowSimOccupancy - exitingToday);
                exitsInWindow += exitingToday;
            }

            java.time.DayOfWeek dow = simDate.getDayOfWeek();
            boolean isProductionDay = dow != java.time.DayOfWeek.SATURDAY && dow != java.time.DayOfWeek.SUNDAY;
            
            if (isProductionDay) {
                // Demand is what we WANT to do regardless of physical constraints
                windowDemandBags += dailyTarget; 
            }
            simDate = simDate.plusDays(1);
        }

        // Space Throughput in Window = What we can fit + Exits that happen during the window
        double totalThroughput = Math.max(0, vacancyAtArrival) + exitsInWindow;
        
        // Final Recommendation: min(Demand, Total Space Throughput)
        double recommendedBags = Math.min(windowDemandBags, totalThroughput);

        // Account for expected contamination (shrinkage) mathematically
        double contaminationRate = configs.getOrDefault("EXPECTED_CONTAMINATION_RATE", 0.05);
        double expectedYieldMultiplier = 1.0 - Math.min(0.99, Math.max(0.0, contaminationRate));

        // To achieve recommended valid bags, we must inoculate more (Demand / ExpectedYield)
        double pelletsToOrder = ((recommendedBags * pelletPerBag) / expectedYieldMultiplier) - simPellets;
        double spawnToOrder = ((recommendedBags * spawnPerBag) / expectedYieldMultiplier) - simSpawn;

        Map<String, Object> results = new HashMap<>();
        results.put("orderDate", orderDate);
        results.put("arrivalDate", arrivalDate);
        results.put("projectionDays", projectionDays);
        results.put("currentActiveBags", currentActiveBags);
        results.put("bagsExiting", exitsInLeadTime);
        double freeSlotsNow = darkRoomCapacity - currentActiveBags;
        
        results.put("exitsInWindow", exitsInWindow);
        results.put("emptySlotsNow", freeSlotsNow);
        results.put("plannedBags", plannedBags);
        results.put("projectedVacancy", vacancyAtArrival); // Do not floor, let the UI show overruns
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

        // 3. Target Constaint (Planned production for the actual Mon-Fri days)
        double targetBags = 0.0;
        LocalDate d = LocalDate.now();
        while (d.isBefore(arrivalDate)) {
            java.time.DayOfWeek dow = d.getDayOfWeek();
            if (dow != java.time.DayOfWeek.SATURDAY && dow != java.time.DayOfWeek.SUNDAY) {
                targetBags += dailyTarget;
            }
            d = d.plusDays(1);
        }

        // The maximum attempts we can afford equals stock logic. No backward yield factor is needed
        // because we are calculating the affordable *attempts*, which already consume 1:1 stock.
        
        // Suggested is the minimum of what we WANT, what we can FIT, and what we can AFFORD (stock-wise)
        return (int) Math.max(0, Math.floor(Math.min(targetBags, Math.min(freeSlotsAtArrival, stockSustaineableBags))));
    }

    private double getStockQuantity(String itemName) {
        return stockRepository.findByItemName(itemName.toUpperCase())
                .map(Stock::getAvailableQuantity)
                .orElse(0.0);
    }
}
