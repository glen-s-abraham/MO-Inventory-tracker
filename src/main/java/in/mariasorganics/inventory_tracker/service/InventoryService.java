package in.mariasorganics.inventory_tracker.service;

import in.mariasorganics.inventory_tracker.model.Stock;
import in.mariasorganics.inventory_tracker.model.StockTransaction;
import in.mariasorganics.inventory_tracker.repository.StockRepository;
import in.mariasorganics.inventory_tracker.repository.StockTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class InventoryService {

    private final StockRepository stockRepository;
    private final StockTransactionRepository transactionRepository;
    private final ConfigService configService;

    public InventoryService(StockRepository stockRepository, 
                            StockTransactionRepository transactionRepository, 
                            ConfigService configService) {
        this.stockRepository = stockRepository;
        this.transactionRepository = transactionRepository;
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
        
        return Map.of(
            "PELLETS", dailyTarget * pelletPerBag * daysThreshold,
            "SPAWN", dailyTarget * spawnUsagePerBag * daysThreshold
        );
    }
}
