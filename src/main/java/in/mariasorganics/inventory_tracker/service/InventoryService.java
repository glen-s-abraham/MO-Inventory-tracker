package in.mariasorganics.inventory_tracker.service;

import in.mariasorganics.inventory_tracker.model.Batch;
import in.mariasorganics.inventory_tracker.model.Stock;
import in.mariasorganics.inventory_tracker.model.StockTransaction;
import in.mariasorganics.inventory_tracker.repository.BatchRepository;
import in.mariasorganics.inventory_tracker.repository.StockRepository;
import in.mariasorganics.inventory_tracker.repository.StockTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class InventoryService {

    public static class SimulationResult {
        public final int runwayDays;
        public final boolean spacePauseDetected;

        public SimulationResult(int runwayDays, boolean spacePauseDetected) {
            this.runwayDays = runwayDays;
            this.spacePauseDetected = spacePauseDetected;
        }
    }

    private final StockRepository stockRepository;
    private final StockTransactionRepository transactionRepository;
    private final BatchRepository batchRepository;
    private final ConfigService configService;

    public InventoryService(StockRepository stockRepository, 
                            StockTransactionRepository transactionRepository, 
                            BatchRepository batchRepository,
                            ConfigService configService) {
        this.stockRepository = stockRepository;
        this.transactionRepository = transactionRepository;
        this.batchRepository = batchRepository;
        this.configService = configService;
    }

    public List<Stock> getAllStock() {
        return stockRepository.findAll();
    }

    @Transactional
    public void logReceipt(String itemName, Double quantity, String unit) {
        // unit parameter is kept for compatibility but ignored for logic if we want strict grams/kg
        Stock stock = stockRepository.findByItemName(itemName.toUpperCase())
                .orElse(new Stock(itemName.toUpperCase(), "spawn".equalsIgnoreCase(itemName) ? "G" : "KG"));
        
        stock.addPhysical(quantity);
        stockRepository.save(stock);

        transactionRepository.save(new StockTransaction(itemName.toUpperCase(), quantity, 
                StockTransaction.TransactionType.RECEIPT, "Stock Receipt"));
    }

    @Transactional
    public void logAdjustment(String itemName, Double quantity, String reason) {
        Stock stock = stockRepository.findByItemName(itemName.toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("Item not found: " + itemName));
        
        stock.subtractPhysical(quantity);
        stockRepository.save(stock);

        transactionRepository.save(new StockTransaction(itemName.toUpperCase(), quantity, 
                StockTransaction.TransactionType.ADJUSTMENT, reason));
    }

    public Map<String, Double> getStockRequirements() {
        Map<String, Double> configs = configService.getConfigMap();
        double dailyTarget = configs.getOrDefault("DAILY_PRODUCTION_TARGET", 18.0);
        double pelletPerBag = configs.getOrDefault("PELLET_KG_PER_BAG", 1.0);
        double spawnUsagePerBag = configs.getOrDefault("SPAWN_USAGE_PER_BAG_G", 150.0);
        double daysThreshold = configs.getOrDefault("LOW_STOCK_THRESHOLD_DAYS", 5.0);
        
        // Safety requirement should be based on active sessions to ensure enough is on hand for the next 'N' production days.
        return Map.of(
            "PELLETS", dailyTarget * pelletPerBag * daysThreshold,
            "SPAWN", dailyTarget * spawnUsagePerBag * daysThreshold
        );
    }

    public Map<String, Object> getDashboardProjections() {
        Map<String, Double> configs = configService.getConfigMap();
        double dailyTarget = configs.getOrDefault("DAILY_PRODUCTION_TARGET", 18.0);
        double pelletPerBag = configs.getOrDefault("PELLET_KG_PER_BAG", 1.0);
        double spawnPerBag = configs.getOrDefault("SPAWN_USAGE_PER_BAG_G", 150.0);
        double leadTime = configs.getOrDefault("SUPPLIER_LEAD_TIME_DAYS", 15.0);
        double capacity = configs.getOrDefault("DARK_ROOM_CAPACITY", 900.0);
        double inoculationsPerWeek = configs.getOrDefault("INOCULATIONS_PER_WEEK", 1.0);

        double availablePellets = getStockQuantity("PELLETS");
        double availableSpawn = getStockQuantity("SPAWN");

        // 1. Space Metrics
        Long activeBags = batchRepository.countActiveBags();
        if (activeBags == null) activeBags = 0L;
        double freeSlotsNow = Math.max(0, capacity - activeBags);
        
        // Exits in different windows for UI and logic
        Long exitsLeadTime = batchRepository.sumBagCountByStatusAndTargetExitDateBetween(LocalDate.now(), LocalDate.now().plusDays((long)leadTime));
        if (exitsLeadTime == null) exitsLeadTime = 0L;
        Long exitsNextCycle = exitsLeadTime; // Already calculated for the dashboard window

        // 2. Optimal Rate Calculation (Space + Stock)
        // Space Throuput Rate = (Free Slots Now + Exits in LeadTime) / leadTime
        double spaceThroughputRate = (freeSlotsNow + exitsNextCycle) / leadTime;
        double stockRate = Math.min(availablePellets / pelletPerBag, availableSpawn / spawnPerBag) / leadTime;

        double optimalRate = Math.min(dailyTarget, Math.min(spaceThroughputRate, stockRate));
        optimalRate = Math.max(0, Math.floor(optimalRate));

        // 3. Projections (Double Constrained)
        SimulationResult standardRes = calculateCombinedRunway(availablePellets, availableSpawn, dailyTarget, inoculationsPerWeek, pelletPerBag, spawnPerBag, (double)capacity, (double)activeBags);
        SimulationResult optimizedRes = calculateCombinedRunway(availablePellets, availableSpawn, optimalRate, inoculationsPerWeek, pelletPerBag, spawnPerBag, (double)capacity, (double)activeBags);

        Map<String, Object> projections = new HashMap<>();
        projections.put("optimalRate", (int) optimalRate);
        projections.put("freeSlotsNow", (int) freeSlotsNow);
        projections.put("capacity", (int) capacity);
        projections.put("activeBags", activeBags.intValue());
        projections.put("exitsLeadTime", exitsLeadTime);
        projections.put("leadTime", (int) leadTime);
        projections.put("isCritical", standardRes.runwayDays <= leadTime);
        projections.put("occupancyPercentage", (int) (activeBags * 100.0 / capacity));
        projections.put("spacePauseDetected", standardRes.spacePauseDetected);
        
        projections.put("standard", Map.of(
            "runwayDays", standardRes.runwayDays,
            "nextOrderDate", LocalDate.now().plusDays((long) Math.max(0, standardRes.runwayDays - leadTime)),
            "rate", (int) dailyTarget
        ));
        
        projections.put("optimized", Map.of(
            "runwayDays", optimizedRes.runwayDays,
            "nextOrderDate", LocalDate.now().plusDays((long) Math.max(0, optimizedRes.runwayDays - leadTime)),
            "rate", (int) optimalRate
        ));

        return projections;
    }

    private SimulationResult calculateCombinedRunway(double pellets, double spawn, double rate, double frequencyPerWeek, double pelletPerBag, double spawnPerBag, double capacity, double activeBags) {
        if (rate <= 0 || frequencyPerWeek <= 0) return new SimulationResult(999, false);
        
        // 1. Prepare Exit Timeline
        List<Batch> batches = batchRepository.findByStatusOrderByInoculationDateAsc(Batch.BatchStatus.ACTIVE);
        Map<LocalDate, Long> exitsTimeline = new HashMap<>();
        for (Batch b : batches) {
            LocalDate exitDate = b.getTargetExitDate();
            if (exitDate != null) {
                exitsTimeline.put(exitDate, exitsTimeline.getOrDefault(exitDate, 0L) + b.getBagCount());
            }
        }

        // 2. Simulation Loop (365 Days)
        double currentPellets = pellets;
        double currentSpawn = spawn;
        double currentOccupancy = activeBags;
        int days = 0;
        boolean pauseDetected = false;
        LocalDate simDate = LocalDate.now();

        while (days < 365) {
            days++;
            simDate = simDate.plusDays(1);
            
            // A. Batch Exits (Process slots opening up today)
            Long exitingToday = exitsTimeline.get(simDate);
            if (exitingToday != null) {
                currentOccupancy = Math.max(0, currentOccupancy - exitingToday);
            }

            // B. Production Check
            // Rule: Production only happens if we meet the weekly frequency AND have room
            boolean isProductionDay = (days % 7) < (int)frequencyPerWeek;
            
            if (isProductionDay) {
                if (currentOccupancy + rate <= capacity) {
                    // We have room! Produce and consume stock.
                    double pelletRequirement = rate * pelletPerBag;
                    double spawnRequirement = rate * spawnPerBag;
                    
                    if (currentPellets >= pelletRequirement && currentSpawn >= spawnRequirement) {
                        currentPellets -= pelletRequirement;
                        currentSpawn -= spawnRequirement;
                        currentOccupancy += rate;
                    } else {
                        // Stock out! End simulation.
                        return new SimulationResult(days - 1, pauseDetected);
                    }
                } else {
                    // Room full! Skip production for today (Preserve stock, but production hits a wall)
                    pauseDetected = true;
                }
            }
        }
        
        return new SimulationResult(days, pauseDetected);
    }

    private double getStockQuantity(String itemName) {
        return stockRepository.findByItemName(itemName.toUpperCase())
                .map(s -> s.getPhysicalQuantity() - s.getReservedQuantity())
                .orElse(0.0);
    }
}
