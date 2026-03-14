package in.mariasorganics.inventory_tracker.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "app_config")
public class AppConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "config_key", unique = true, nullable = false)
    private String configKey;

    @Column(name = "config_value", nullable = false)
    private Double configValue;

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    public AppConfig() {}

    public AppConfig(String configKey, Double configValue) {
        this.configKey = configKey;
        this.configValue = configValue;
        this.lastUpdated = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getConfigKey() { return configKey; }
    public void setConfigKey(String configKey) { this.configKey = configKey; }
    public Double getConfigValue() { return configValue; }
    public void setConfigValue(Double configValue) { this.configValue = configValue; this.lastUpdated = LocalDateTime.now(); }
    public LocalDateTime getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; }
}
