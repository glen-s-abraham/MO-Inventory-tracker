package in.mariasorganics.inventory_tracker.service;

import in.mariasorganics.inventory_tracker.model.Batch;
import in.mariasorganics.inventory_tracker.model.Stock;
import in.mariasorganics.inventory_tracker.model.StockTransaction;
import in.mariasorganics.inventory_tracker.repository.BatchRepository;
import in.mariasorganics.inventory_tracker.repository.StockRepository;
import in.mariasorganics.inventory_tracker.repository.StockTransactionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class BatchService {

    private static final Logger log = LoggerFactory.getLogger(BatchService.class);

    private final BatchRepository batchRepository;
    private final StockRepository stockRepository;
    private final StockTransactionRepository transactionRepository;
    private final ConfigService configService;

    public BatchService(BatchRepository batchRepository,
                        StockRepository stockRepository,
                        StockTransactionRepository transactionRepository,
                        ConfigService configService) {
        this.batchRepository = batchRepository;
        this.stockRepository = stockRepository;
        this.transactionRepository = transactionRepository;
        this.configService = configService;
    }

    public List<Batch> getAllBatches() {
        return batchRepository.findAllByOrderByInoculationDateDesc();
    }

    public List<Batch> getActiveBatches() {
        return batchRepository.findByStatusOrderByInoculationDateDesc(Batch.BatchStatus.ACTIVE);
    }

    public Page<Batch> getActiveBatchesPaginated(int page, int size) {
        return batchRepository.findByStatusOrderByInoculationDateDesc(Batch.BatchStatus.ACTIVE, PageRequest.of(page, size));
    }

    public Page<Batch> getCompletedBatchesPaginated(int page, int size) {
        return batchRepository.findByStatusOrderByCompletedAtDesc(Batch.BatchStatus.COMPLETED, PageRequest.of(page, size));
    }

    @Transactional
    public Batch createBatch(LocalDate inoculationDate, Integer bagCount) {
        Map<String, Double> configs = configService.getConfigMap();
        
        // 1. Occupancy Check
        double capacity = configs.getOrDefault("DARK_ROOM_CAPACITY", 900.0);
        Long activeBags = batchRepository.countActiveBags();
        if (activeBags == null) activeBags = 0L;
        
        if (activeBags + bagCount > capacity) {
            throw new IllegalStateException("Dark Room capacity exceeded. Current: " + activeBags + ", Requested: " + bagCount + ", Capacity: " + capacity);
        }

        // 2. Stock Deduction Logic
        double pelletPerBag = configs.getOrDefault("PELLET_KG_PER_BAG", 1.0);
        double spawnUsagePerBag = configs.getOrDefault("SPAWN_USAGE_PER_BAG_G", 150.0);
        
        double totalPelletsNeeded = bagCount * pelletPerBag;
        double totalSpawnNeeded = bagCount * spawnUsagePerBag;

        deductStock("PELLETS", totalPelletsNeeded, "Batch Production: " + bagCount + " bags");
        deductStock("SPAWN", totalSpawnNeeded, "Batch Production: " + bagCount + " bags");

        // 3. Batch Generation
        double inoculationPeriod = configs.getOrDefault("INOCULATION_PERIOD_DAYS", 18.0);
        LocalDate targetExitDate = inoculationDate.plusDays((long) inoculationPeriod);
        
        String batchId = generateBatchId(inoculationDate);
        
        Batch batch = new Batch(batchId, inoculationDate, bagCount, targetExitDate);
        return batchRepository.save(batch);
    }

    public Long getTotalActiveBags() {
        Long count = batchRepository.countActiveBags();
        return count == null ? 0L : count;
    }

    @Transactional
    public void checkoutBags(int totalToCheckout) {
        Long activeBags = batchRepository.countActiveBags();
        if (activeBags == null) activeBags = 0L;
        
        if (totalToCheckout > activeBags) {
            throw new IllegalStateException("Cannot checkout " + totalToCheckout + " bags. Only " + activeBags + " active bags in room.");
        }

        List<Batch> activeBatches = batchRepository.findByStatusOrderByInoculationDateAsc(Batch.BatchStatus.ACTIVE);
        int remaining = totalToCheckout;

        for (Batch batch : activeBatches) {
            if (remaining <= 0) break;

            if (batch.getBagCount() <= remaining) {
                // Full batch checkout
                int count = batch.getBagCount();
                batch.setStatus(Batch.BatchStatus.COMPLETED);
                batch.setCompletedAt(LocalDateTime.now());
                batchRepository.save(batch);
                remaining -= count;
            } else {
                // Partial batch checkout: Split it
                int count = remaining;
                
                // Create a completed record for the bags moved out
                Batch completedPortion = new Batch(
                    batch.getBatchId() + "-OUT-" + System.currentTimeMillis() % 1000, 
                    batch.getInoculationDate(), 
                    count, 
                    batch.getTargetExitDate()
                );
                completedPortion.setStatus(Batch.BatchStatus.COMPLETED);
                completedPortion.setCompletedAt(LocalDateTime.now());
                batchRepository.save(completedPortion);

                // Update original batch with remaining bags
                batch.setBagCount(batch.getBagCount() - count);
                batchRepository.save(batch);
                
                remaining = 0;
            }
        }
    }

    @Transactional
    public void deleteBatch(Long id) {
        log.info("Attempting to delete batch with ID: {}", id);
        Batch batch = batchRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Batch not found: " + id));
        
        if (batch.getStatus() == Batch.BatchStatus.COMPLETED) {
            log.warn("Attempted to delete a completed batch: {}", id);
            throw new IllegalStateException("Completed batches cannot be deleted for historical integrity.");
        }

        if (batch.getStatus() == Batch.BatchStatus.ACTIVE) {
            log.info("Restoring stock for active batch {} ({} bags)", batch.getBatchId(), batch.getBagCount());
            Map<String, Double> configs = configService.getConfigMap();
            double pelletPerBag = configs.getOrDefault("PELLET_KG_PER_BAG", 1.0);
            double spawnUsagePerBag = configs.getOrDefault("SPAWN_USAGE_PER_BAG_G", 150.0);
            
            addStockBack("PELLETS", batch.getBagCount() * pelletPerBag, "Batch Deletion: " + batch.getBatchId());
            addStockBack("SPAWN", batch.getBagCount() * spawnUsagePerBag, "Batch Deletion: " + batch.getBatchId());
        }
        
        batchRepository.delete(batch);
        log.info("Batch deleted successfully: {}", id);
    }

    @Transactional
    public void updateBatchCount(Long id, int newCount) {
        Batch batch = batchRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Batch not found: " + id));
        
        if (batch.getStatus() != Batch.BatchStatus.ACTIVE) {
            throw new IllegalStateException("Only ACTIVE batches can have their count adjusted.");
        }

        int diff = newCount - batch.getBagCount();
        if (diff == 0) return;

        Map<String, Double> configs = configService.getConfigMap();
        double pelletPerBag = configs.getOrDefault("PELLET_KG_PER_BAG", 1.0);
        double spawnUsagePerBag = configs.getOrDefault("SPAWN_USAGE_PER_BAG_G", 150.0);

        if (diff > 0) {
            // Adding bags
            double capacity = configs.getOrDefault("DARK_ROOM_CAPACITY", 900.0);
            Long activeBags = batchRepository.countActiveBags();
            if (activeBags + diff > capacity) {
                throw new IllegalStateException("Capacity exceeded. Remaining: " + (capacity - activeBags));
            }
            deductStock("PELLETS", diff * pelletPerBag, "Batch Adjustment (+): " + batch.getBatchId());
            deductStock("SPAWN", diff * spawnUsagePerBag, "Batch Adjustment (+): " + batch.getBatchId());
        } else {
            // Removing bags
            int toRemove = Math.abs(diff);
            addStockBack("PELLETS", toRemove * pelletPerBag, "Batch Adjustment (-): " + batch.getBatchId());
            addStockBack("SPAWN", toRemove * spawnUsagePerBag, "Batch Adjustment (-): " + batch.getBatchId());
        }

        batch.setBagCount(newCount);
        batchRepository.save(batch);
    }

    @Transactional
    public void revertToActive(Long id) {
        Batch batch = batchRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Batch not found: " + id));
        
        if (batch.getStatus() != Batch.BatchStatus.COMPLETED) {
            throw new IllegalStateException("Only COMPLETED batches can be reverted.");
        }

        Map<String, Double> configs = configService.getConfigMap();
        double capacity = configs.getOrDefault("DARK_ROOM_CAPACITY", 900.0);
        Long activeBags = batchRepository.countActiveBags();
        if (activeBags == null) activeBags = 0L;

        if (activeBags + batch.getBagCount() > capacity) {
            throw new IllegalStateException("Cannot revert. Room capacity reached.");
        }

        batch.setStatus(Batch.BatchStatus.ACTIVE);
        batch.setCompletedAt(null);
        batchRepository.save(batch);
    }

    private void deductStock(String itemName, Double quantity, String reason) {
        Stock stock = stockRepository.findByItemName(itemName.toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("Resource not found in inventory: " + itemName));
        
        if (stock.getPhysicalQuantity() < quantity) {
            throw new IllegalStateException("Insufficient stock for " + itemName + ". Available: " + stock.getPhysicalQuantity() + ", Required: " + quantity);
        }
        
        stock.subtractPhysical(quantity);
        stockRepository.save(stock);

        transactionRepository.save(new StockTransaction(itemName.toUpperCase(), quantity, 
                StockTransaction.TransactionType.CONSUMPTION, reason));
    }

    private void addStockBack(String itemName, Double quantity, String reason) {
        Stock stock = stockRepository.findByItemName(itemName.toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("Resource not found in inventory: " + itemName));
        
        stock.addPhysical(quantity);
        stockRepository.save(stock);

        transactionRepository.save(new StockTransaction(itemName.toUpperCase(), quantity, 
                StockTransaction.TransactionType.ADJUSTMENT, reason + " (Correction)"));
    }

    private String generateBatchId(LocalDate date) {
        String datePart = date.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String suffix = String.format("%04d", (int)(Math.random() * 10000));
        return "B-" + datePart + "-" + suffix;
    }
}
