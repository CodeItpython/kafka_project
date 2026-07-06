package com.kafka.shopping.cart;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A Naver product saved to a user's cart. Because the product lives on Naver (external),
 * we store a snapshot of its display fields at add-time plus the link back to Naver.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "cart_items",
        uniqueConstraints = @UniqueConstraint(name = "uk_cart_items_user_product", columnNames = {"user_email", "product_id"}),
        indexes = @Index(name = "idx_cart_items_user", columnList = "user_email")
)
public class CartItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_email", nullable = false, length = 320)
    private String userEmail;

    @Column(name = "product_id", nullable = false, length = 100)
    private String productId;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(length = 1000)
    private String link;

    @Column(length = 1000)
    private String image;

    @Column(nullable = false)
    private long price;

    @Column(name = "mall_name", length = 200)
    private String mallName;

    @Column(length = 200)
    private String brand;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public CartItem(String userEmail, String productId, String title, String link, String image,
                    long price, String mallName, String brand, int quantity) {
        this.userEmail = userEmail;
        this.productId = productId;
        this.title = title;
        this.link = link;
        this.image = image;
        this.price = price;
        this.mallName = mallName;
        this.brand = brand;
        this.quantity = quantity;
    }

    public void increaseQuantity(int amount) {
        this.quantity += amount;
        this.updatedAt = Instant.now();
    }

    public void changeQuantity(int quantity) {
        this.quantity = Math.max(1, quantity);
        this.updatedAt = Instant.now();
    }
}
