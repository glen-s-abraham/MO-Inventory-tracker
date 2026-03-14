package in.mariasorganics.inventory_tracker.repository;

import in.mariasorganics.inventory_tracker.model.AppConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AppConfigRepository extends JpaRepository<AppConfig, Long> {
    Optional<AppConfig> findByConfigKey(String configKey);
}
