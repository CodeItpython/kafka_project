package com.kafka.shopping.batch;

import com.kafka.shopping.catalog.ShoppingCategory;
import com.kafka.shopping.catalog.ShoppingDtos.ProductResponse;
import com.kafka.shopping.catalog.ShoppingService;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import org.springframework.batch.item.ItemReader;

/**
 * 카탈로그 색인 잡의 Reader. 카테고리를 순회하며 Naver에서 상품을 한 카테고리씩 가져와
 * 한 건씩 흘려보낸다({@code fetchRaw}이라 여기서는 색인하지 않는다 — 색인은 Writer가 담당).
 * StepScope로 매 실행마다 새 인스턴스가 생성되어 반복자가 초기화된다.
 */
public class NaverProductItemReader implements ItemReader<ProductResponse> {
    private static final int PER_CATEGORY = 100;

    private final ShoppingService shoppingService;
    private final Iterator<ShoppingCategory> categories = Arrays.asList(ShoppingCategory.values()).iterator();
    private Iterator<ProductResponse> current = Collections.emptyIterator();

    public NaverProductItemReader(ShoppingService shoppingService) {
        this.shoppingService = shoppingService;
    }

    @Override
    public ProductResponse read() {
        while (!current.hasNext() && categories.hasNext()) {
            ShoppingCategory category = categories.next();
            current = shoppingService.fetchRaw(category.query(), "sim", PER_CATEGORY, 1).iterator();
        }
        return current.hasNext() ? current.next() : null;
    }
}
