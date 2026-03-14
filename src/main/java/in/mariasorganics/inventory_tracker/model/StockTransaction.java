package in.mariasorganics.inventory_tracker.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "it_stock_transaction")
public class StockTransaction {

    public enum TransactionType {
        RECEIPT, ADJUSTMENT, RESERVATION, CONSUMPTION
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item_name", nullable = false)
    private String itemName;

    @Column(nullable = false)
    private Double quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false)
    private TransactionType transactionType;

    private String reason;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    public StockTransaction() {
        this.timestamp = LocalDateTime.now();
    }

    public StockTransaction(String itemName, Double quantity, TransactionType transactionType, String reason) {
        this.itemName = itemName;
        this.quantity = quantity;
        this.transactionType = transactionType;
        this.reason = reason;
        this.timestamp = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }
    public Double getQuantity() { return quantity; }
    public void setQuantity(Double quantity) { this.quantity = quantity; }
    public TransactionType getTransactionType() { return transactionType; }
    public void setTransactionType(TransactionType transactionType) { this.transactionType = transactionType; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
