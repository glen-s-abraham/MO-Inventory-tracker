package in.mariasorganics.inventory_tracker.service;

import in.mariasorganics.inventory_tracker.model.AppConfig;
import in.mariasorganics.inventory_tracker.model.AppConfigHistory;
import in.mariasorganics.inventory_tracker.repository.AppConfigRepository;
import in.mariasorganics.inventory_tracker.repository.AppConfigHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ConfigService {

    private final AppConfigRepository configRepository;
    private final AppConfigHistoryRepository historyRepository;

    public ConfigService(AppConfigRepository configRepository, AppConfigHistoryRepository historyRepository) {
        this.configRepository = configRepository;
        this.historyRepository = historyRepository;
    }

    public List<AppConfig> getAllConfigs() {
        return configRepository.findAll();
    }

    public Map<String, Double> getConfigMap() {
        List<AppConfig> configs = getAllConfigs();
        Map<String, Double> configMap = new HashMap<>();
        for (AppConfig config : configs) {
            configMap.put(config.getConfigKey(), config.getConfigValue());
        }
        return configMap;
    }

    @Transactional
    public void updateConfig(String key, Double newValue) {
        if (newValue == null || newValue <= 0) {
            throw new IllegalArgumentException("Parameter value must be greater than zero.");
        }

        Optional<AppConfig> existingOpt = configRepository.findByConfigKey(key);
        if (existingOpt.isPresent()) {
            AppConfig existing = existingOpt.get();
            Double oldValue = existing.getConfigValue();
            
            if (!oldValue.equals(newValue)) {
                existing.setConfigValue(newValue);
                configRepository.save(existing);
                
                AppConfigHistory history = new AppConfigHistory(key, oldValue, newValue);
                historyRepository.save(history);
            }
        } else {
            AppConfig newConfig = new AppConfig(key, newValue);
            configRepository.save(newConfig);
            
            AppConfigHistory history = new AppConfigHistory(key, null, newValue);
            historyRepository.save(history);
        }
    }

    public String checkDarkRoomCapacityWarning(Double newCapacity) {
        // Mocked ACTIVE_BAG_COUNT for now
        long activeBagCount = 0; 
        if (newCapacity < activeBagCount) {
            return "Warning: New Dark Room Capacity (" + newCapacity + ") is less than current active bags (" + activeBagCount + ").";
        }
        return null;
    }

    @Transactional
    public void initializeDefaults() {
        Map<String, Double> defaults = Map.of(
            "PELLET_KG_PER_BAG", 1.0,
            "SPAWN_USAGE_PER_BAG_G", 150.0,
            "DARK_ROOM_CAPACITY", 900.0,
            "DAILY_PRODUCTION_TARGET", 18.0,
            "LOW_STOCK_THRESHOLD_DAYS", 5.0,
            "INOCULATION_PERIOD_DAYS", 18.0,
            "SUPPLIER_LEAD_TIME_DAYS", 15.0
        );

        defaults.forEach((key, value) -> {
            if (configRepository.findByConfigKey(key).isEmpty()) {
                updateConfig(key, value);
            }
        });
    }
}
