package com.kafka.shopping.cart;

import com.kafka.shopping.cart.CartDtos.AddCartItemRequest;
import com.kafka.shopping.cart.CartDtos.CartItemResponse;
import com.kafka.shopping.cart.CartDtos.CartResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CartService {
    private final CartItemRepository cartItemRepository;

    @Transactional(readOnly = true)
    public CartResponse list(String userEmail) {
        List<CartItemResponse> items = cartItemRepository.findByUserEmailOrderByCreatedAtDesc(userEmail)
                .stream()
                .map(CartItemResponse::from)
                .toList();
        int totalCount = items.stream().mapToInt(CartItemResponse::quantity).sum();
        long totalPrice = items.stream().mapToLong(item -> item.price() * item.quantity()).sum();
        return new CartResponse(items, totalCount, totalPrice);
    }

    @Transactional
    public CartResponse add(String userEmail, AddCartItemRequest request) {
        int quantity = request.quantity() == null || request.quantity() < 1 ? 1 : request.quantity();
        cartItemRepository.findByUserEmailAndProductId(userEmail, request.productId())
                .ifPresentOrElse(
                        existing -> existing.increaseQuantity(quantity),
                        () -> cartItemRepository.save(new CartItem(
                                userEmail,
                                request.productId(),
                                request.title(),
                                request.link(),
                                request.image(),
                                request.price(),
                                request.mallName(),
                                request.brand(),
                                quantity
                        ))
                );
        return list(userEmail);
    }

    @Transactional
    public CartResponse updateQuantity(String userEmail, Long id, int quantity) {
        if (quantity < 1) {
            return remove(userEmail, id);
        }
        CartItem item = cartItemRepository.findByIdAndUserEmail(id, userEmail)
                .orElseThrow(() -> new IllegalArgumentException("장바구니 항목을 찾을 수 없습니다."));
        item.changeQuantity(quantity);
        return list(userEmail);
    }

    @Transactional
    public CartResponse remove(String userEmail, Long id) {
        cartItemRepository.deleteByIdAndUserEmail(id, userEmail);
        return list(userEmail);
    }

    @Transactional
    public CartResponse clear(String userEmail) {
        cartItemRepository.deleteByUserEmail(userEmail);
        return list(userEmail);
    }
}
