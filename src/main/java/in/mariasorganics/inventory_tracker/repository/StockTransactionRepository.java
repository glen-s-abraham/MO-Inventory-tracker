package in.mariasorganics.inventory_tracker.repository;

import in.mariasorganics.inventory_tracker.model.StockTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockTransactionRepository extends JpaRepository<StockTransaction, Long> {
}
