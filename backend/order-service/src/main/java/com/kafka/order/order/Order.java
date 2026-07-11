package com.kafka.order.order;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "orders", indexes = {
        @Index(name = "idx_orders_user", columnList = "user_email"),
        @Index(name = "idx_orders_number", columnList = "order_number")
})
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_number", nullable = false, length = 40)
    private String orderNumber;

    @Column(name = "user_email", nullable = false, length = 320)
    private String userEmail;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "payment_method", nullable = false, length = 30)
    private String paymentMethod;

    @Column(name = "payment_tx_id", nullable = false, length = 60)
    private String paymentTxId;

    @Column(name = "total_count", nullable = false)
    private int totalCount;

    @Column(name = "total_amount", nullable = false)
    private long totalAmount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    public Order(String orderNumber, String userEmail, String status,
                 String paymentMethod, String paymentTxId, int totalCount, long totalAmount) {
        this.orderNumber = orderNumber;
        this.userEmail = userEmail;
        this.status = status;
        this.paymentMethod = paymentMethod;
        this.paymentTxId = paymentTxId;
        this.totalCount = totalCount;
        this.totalAmount = totalAmount;
        this.createdAt = Instant.now();
    }

    public void addItem(OrderItem item) {
        item.attachTo(this);
        this.items.add(item);
    }
}
