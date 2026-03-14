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
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
public class BatchService {

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

    private String generateBatchId(LocalDate date) {
        String datePart = date.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long countSuffix = batchRepository.count() + 1;
        return "B-" + datePart + "-" + String.format("%03d", countSuffix);
    }
}
