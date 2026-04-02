package in.mariasorganics.inventory_tracker.config;

import in.mariasorganics.inventory_tracker.model.Batch;
import in.mariasorganics.inventory_tracker.model.Stock;
import in.mariasorganics.inventory_tracker.model.StockTransaction;
import in.mariasorganics.inventory_tracker.repository.BatchRepository;
import in.mariasorganics.inventory_tracker.repository.StockRepository;
import in.mariasorganics.inventory_tracker.repository.StockTransactionRepository;
import in.mariasorganics.inventory_tracker.service.ConfigService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Component
@Profile("dev")
public class DatabaseSeeder implements CommandLineRunner {

    private final StockRepository stockRepository;
    private final BatchRepository batchRepository;
    private final StockTransactionRepository transactionRepository;
    private final ConfigService configService;

    public DatabaseSeeder(StockRepository stockRepository,
                          BatchRepository batchRepository,
                          StockTransactionRepository transactionRepository,
                          ConfigService configService) {
        this.stockRepository = stockRepository;
        this.batchRepository = batchRepository;
        this.transactionRepository = transactionRepository;
        this.configService = configService;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        // Only seed if empty
        if (stockRepository.count() == 0) {
            System.out.println("Seeding development data...");

            // Initialize app configs (if not already done)
            configService.initializeDefaults();

            // Seed Stock
            Stock pellets = new Stock("PELLETS", "KG");
            pellets.setPhysicalQuantity(500.0);
            stockRepository.save(pellets);

            Stock spawn = new Stock("SPAWN", "G");
            spawn.setPhysicalQuantity(15000.0);
            stockRepository.save(spawn);

            // Seed Stock Transactions
            transactionRepository.save(new StockTransaction("PELLETS", 500.0, StockTransaction.TransactionType.RECEIPT, "Initial dev seed"));
            transactionRepository.save(new StockTransaction("SPAWN", 15000.0, StockTransaction.TransactionType.RECEIPT, "Initial dev seed"));

            // Seed Batches (some active, some future)
            LocalDate today = LocalDate.now();
            Batch batch1 = new Batch("MO-TEST-01", today.minusDays(5), 10, today.plusDays(13));
            batchRepository.save(batch1);

            Batch batch2 = new Batch("MO-TEST-02", today.minusDays(2), 20, today.plusDays(16));
            batchRepository.save(batch2);

            Batch batch3 = new Batch("MO-TEST-03", today, 15, today.plusDays(18));
            batchRepository.save(batch3);

            // No dummy purchase orders seeded to allow for empty-pipeline testing.

            System.out.println("Development data seeded successfully.");
        } else {
            System.out.println("Database already contains data. Skipping seeding.");
        }
    }
}
