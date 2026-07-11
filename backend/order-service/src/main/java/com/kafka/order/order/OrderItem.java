package com.kafka.order.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "order_items")
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false,
            foreignKey = @jakarta.persistence.ForeignKey(name = "fk_order_items_order"))
    private Order order;

    @Column(name = "product_id", nullable = false, length = 100)
    private String productId;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "link", length = 1000)
    private String link;

    @Column(name = "image", length = 1000)
    private String image;

    @Column(name = "price", nullable = false)
    private long price;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "mall_name", length = 200)
    private String mallName;

    @Column(name = "brand", length = 200)
    private String brand;

    public OrderItem(String productId, String title, String link, String image,
                     long price, int quantity, String mallName, String brand) {
        this.productId = productId;
        this.title = title;
        this.link = link;
        this.image = image;
        this.price = price;
        this.quantity = quantity;
        this.mallName = mallName;
        this.brand = brand;
    }

    void attachTo(Order order) {
        this.order = order;
    }
}
