package com.github.hkjs96.ordersystem.dto.response;

import com.github.hkjs96.ordersystem.domain.model.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "주문 응답 DTO")
public record OrderResponse(
        @Schema(description = "주문 ID", example = "123")
        Long orderId,

        @Schema(description = "상품 ID", example = "1001")
        Long productId,

        @Schema(description = "수량", example = "2")
        int quantity,

        @Schema(description = "주문 상태", example = "CREATED")
        OrderStatus status
) {}