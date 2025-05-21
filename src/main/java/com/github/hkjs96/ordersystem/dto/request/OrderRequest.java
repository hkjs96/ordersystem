package com.github.hkjs96.ordersystem.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "주문 생성 요청 DTO")
public record OrderRequest(
        @NotNull @Schema(description = "상품 ID", example = "1")
        Long productId,

        @Min(1) @Schema(description = "수량", example = "3")
        int quantity
) {}