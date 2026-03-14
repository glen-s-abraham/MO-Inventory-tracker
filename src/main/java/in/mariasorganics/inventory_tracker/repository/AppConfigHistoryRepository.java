package in.mariasorganics.inventory_tracker.repository;

import in.mariasorganics.inventory_tracker.model.AppConfigHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppConfigHistoryRepository extends JpaRepository<AppConfigHistory, Long> {
}
