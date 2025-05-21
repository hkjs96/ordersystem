package com.github.hkjs96.ordersystem.adapter.in.web;

import com.github.hkjs96.ordersystem.common.ApiResponse;
import com.github.hkjs96.ordersystem.dto.request.OrderRequest;
import com.github.hkjs96.ordersystem.dto.response.OrderResponse;
import com.github.hkjs96.ordersystem.port.in.OrderUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Order API", description = "주문 생성 및 취소 API")
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Validated
public class OrderController {

    private final OrderUseCase orderUseCase;

    @Operation(summary = "주문 생성", description = "상품 ID와 수량으로 새 주문을 생성합니다.")
    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
            @RequestBody @Valid OrderRequest request
    ) {
        OrderResponse result = orderUseCase.createOrder(request);
        return ResponseEntity
                .ok(ApiResponse.success(result));
    }

    @Operation(summary = "주문 취소", description = "주어진 주문 ID를 취소 상태로 전환합니다.")
    @DeleteMapping("/{orderId}")
    public ResponseEntity<ApiResponse<Void>> cancelOrder(
            @PathVariable Long orderId
    ) {
        orderUseCase.cancelOrder(orderId);
        return ResponseEntity
                .ok(ApiResponse.success(null));
    }
}