package com.github.hkjs96.ordersystem.config;

import java.math.BigDecimal;
import java.util.List;

import com.github.hkjs96.ordersystem.domain.entity.Product;
import com.github.hkjs96.ordersystem.domain.repository.ProductRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;


@Configuration
public class DataInitializer implements CommandLineRunner {

    private final ProductRepository productRepository;
    private final RedisTemplate<String, Integer> redisTemplate;

    public DataInitializer(
            ProductRepository productRepository,
            RedisTemplate<String, Integer> redisTemplate
    ) {
        this.productRepository = productRepository;
        this.redisTemplate     = redisTemplate;
    }

    @Override
    public void run(String... args) throws Exception {
        // 1) H2 에 샘플 상품 생성
        List<Product> samples = productRepository.saveAll(List.of(
                Product.builder()
                        .name("Sample Product A")
                        .price(BigDecimal.valueOf(10000))
                        .build(),
                Product.builder()
                        .name("Sample Product B")
                        .price(BigDecimal.valueOf(20000))
                        .build()
        ));

        // 2) Redis에 재고 초기값 세팅
        for (Product p : samples) {
            String key = "inventory:" + p.getId();
            redisTemplate.opsForValue().set(key, 50); // 초기 재고 50개
        }
    }
}
