package com.kafka.shopping.cart;

import com.kafka.shopping.cart.CartDtos.AddCartItemRequest;
import com.kafka.shopping.cart.CartDtos.CartResponse;
import com.kafka.shopping.cart.CartDtos.UpdateQuantityRequest;
import com.kafka.shopping.security.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Per-user cart (authenticated via the shared JWT; keyed by the caller's email). */
@RestController
@RequestMapping("/api/shopping/cart")
@RequiredArgsConstructor
public class CartController {
    private final CartService cartService;

    @GetMapping
    public ResponseEntity<CartResponse> list(@AuthenticationPrincipal AuthUser user) {
        return ResponseEntity.ok(cartService.list(user.getEmail()));
    }

    @PostMapping
    public ResponseEntity<CartResponse> add(
            @AuthenticationPrincipal AuthUser user,
            @Valid @RequestBody AddCartItemRequest request
    ) {
        return ResponseEntity.ok(cartService.add(user.getEmail(), request));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<CartResponse> updateQuantity(
            @AuthenticationPrincipal AuthUser user,
            @PathVariable Long id,
            @RequestBody UpdateQuantityRequest request
    ) {
        return ResponseEntity.ok(cartService.updateQuantity(user.getEmail(), id, request.quantity()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<CartResponse> remove(
            @AuthenticationPrincipal AuthUser user,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(cartService.remove(user.getEmail(), id));
    }

    @DeleteMapping
    public ResponseEntity<CartResponse> clear(@AuthenticationPrincipal AuthUser user) {
        return ResponseEntity.ok(cartService.clear(user.getEmail()));
    }
}
