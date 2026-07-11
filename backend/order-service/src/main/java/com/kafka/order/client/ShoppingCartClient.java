package com.kafka.order.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Reads and clears the current user's cart on shopping-service. The user's JWT is
 * forwarded by {@link com.kafka.order.config.FeignAuthForwardingInterceptor}, so
 * shopping-service resolves the cart by that token's subject.
 */
@FeignClient(
        name = "shopping-cart",
        url = "${app.services.shopping-service.url:http://localhost:8893}",
        path = "/api/shopping/cart")
public interface ShoppingCartClient {
    @GetMapping
    CartResponse getCart();

    @DeleteMapping
    CartResponse clearCart();
}
