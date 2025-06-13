package com.github.hkjs96.ordersystem.config;

import java.math.BigDecimal;
import java.util.List;

import com.github.hkjs96.ordersystem.domain.entity.Product;
import com.github.hkjs96.ordersystem.domain.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;


@Slf4j
@Configuration
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final ProductRepository productRepository;
    private final RedisTemplate<String, Integer> redisTemplate;

    @Override
    public void run(String... args) throws Exception {
        log.info("데이터 초기화 시작");

        // 🔧 1) H2에 샘플 상품 생성 (총 재고 포함)
        List<Product> samples = productRepository.saveAll(List.of(
                Product.builder()
                        .name("Sample Product A")
                        .price(BigDecimal.valueOf(10000))
                        .totalStock(50)  // 🔧 총 재고 설정
                        .stockManagementEnabled(true)
                        .build(),
                Product.builder()
                        .name("Sample Product B")
                        .price(BigDecimal.valueOf(20000))
                        .totalStock(30)  // 🔧 총 재고 설정
                        .stockManagementEnabled(true)
                        .build(),
                Product.builder()
                        .name("Sample Product C (무제한)")
                        .price(BigDecimal.valueOf(5000))
                        .totalStock(0)   // 재고 관리 비활성화 상품
                        .stockManagementEnabled(false)
                        .build()
        ));

        // 🔧 2) Redis에 현재 재고 초기화 (새로운 키 형식)
        for (Product p : samples) {
            String stockKey = "stock:" + p.getId();

            if (p.isStockManaged()) {
                redisTemplate.opsForValue().set(stockKey, p.getTotalStock());
                log.info("재고 관리 상품 초기화: id={}, name={}, totalStock={}",
                        p.getId(), p.getName(), p.getTotalStock());
            } else {
                redisTemplate.opsForValue().set(stockKey, Integer.MAX_VALUE);
                log.info("무제한 재고 상품 초기화: id={}, name={}",
                        p.getId(), p.getName());
            }
        }

        log.info("데이터 초기화 완료: {} 상품 생성", samples.size());
    }
}