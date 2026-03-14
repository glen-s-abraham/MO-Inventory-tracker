package in.mariasorganics.inventory_tracker.model;

import jakarta.persistence.*;

@Entity
@Table(name = "it_stock")
public class Stock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item_name", unique = true, nullable = false)
    private String itemName;

    @Column(name = "physical_quantity", nullable = false)
    private Double physicalQuantity = 0.0;

    @Column(name = "reserved_quantity", nullable = false)
    private Double reservedQuantity = 0.0;

    @Column(name = "unit_of_measure", nullable = false)
    private String unitOfMeasure;

    public Stock() {}

    public Stock(String itemName, String unitOfMeasure) {
        this.itemName = itemName;
        this.unitOfMeasure = unitOfMeasure;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }
    public Double getPhysicalQuantity() { return physicalQuantity; }
    public void setPhysicalQuantity(Double physicalQuantity) { this.physicalQuantity = physicalQuantity; }
    public Double getReservedQuantity() { return reservedQuantity; }
    public void setReservedQuantity(Double reservedQuantity) { this.reservedQuantity = reservedQuantity; }
    public String getUnitOfMeasure() { return unitOfMeasure; }
    public void setUnitOfMeasure(String unitOfMeasure) { this.unitOfMeasure = unitOfMeasure; }

    public void addPhysical(Double amount) {
        this.physicalQuantity += amount;
    }

    public void subtractPhysical(Double amount) {
        this.physicalQuantity -= amount;
    }

    public void addReserved(Double amount) {
        this.reservedQuantity += amount;
    }

    public void subtractReserved(Double amount) {
        this.reservedQuantity -= amount;
    }

    @Transient
    public Double getAvailableQuantity() {
        return physicalQuantity - reservedQuantity;
    }
}
