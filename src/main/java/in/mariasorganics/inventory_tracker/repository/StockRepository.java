package in.mariasorganics.inventory_tracker.repository;

import in.mariasorganics.inventory_tracker.model.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface StockRepository extends JpaRepository<Stock, Long> {
    Optional<Stock> findByItemName(String itemName);
}
