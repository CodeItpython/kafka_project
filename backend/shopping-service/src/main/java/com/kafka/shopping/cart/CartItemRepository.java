package com.kafka.shopping.cart;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    List<CartItem> findByUserEmailOrderByCreatedAtDesc(String userEmail);

    Optional<CartItem> findByUserEmailAndProductId(String userEmail, String productId);

    Optional<CartItem> findByIdAndUserEmail(Long id, String userEmail);

    @Modifying
    @Query("delete from CartItem c where c.userEmail = :email and c.id = :id")
    int deleteByIdAndUserEmail(@Param("id") Long id, @Param("email") String email);

    @Modifying
    @Query("delete from CartItem c where c.userEmail = :email")
    int deleteByUserEmail(@Param("email") String email);
}
