package in.mariasorganics.inventory_tracker.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "it_app_config_history")
public class AppConfigHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "config_key", nullable = false)
    private String configKey;

    @Column(name = "old_value")
    private Double oldValue;

    @Column(name = "new_value", nullable = false)
    private Double newValue;

    @Column(name = "change_timestamp")
    private LocalDateTime changeTimestamp;

    public AppConfigHistory() {}

    public AppConfigHistory(String configKey, Double oldValue, Double newValue) {
        this.configKey = configKey;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.changeTimestamp = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getConfigKey() { return configKey; }
    public void setConfigKey(String configKey) { this.configKey = configKey; }
    public Double getOldValue() { return oldValue; }
    public void setOldValue(Double oldValue) { this.oldValue = oldValue; }
    public Double getNewValue() { return newValue; }
    public void setNewValue(Double newValue) { this.newValue = newValue; }
    public LocalDateTime getChangeTimestamp() { return changeTimestamp; }
    public void setChangeTimestamp(LocalDateTime changeTimestamp) { this.changeTimestamp = changeTimestamp; }
}
