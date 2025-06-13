package com.github.hkjs96.ordersystem.adapter.in.web;

import com.github.hkjs96.ordersystem.common.ApiResponse;
import com.github.hkjs96.ordersystem.domain.model.OrderStatus;
import com.github.hkjs96.ordersystem.dto.response.DeliveryInfoResponse;
import com.github.hkjs96.ordersystem.port.in.DeliveryUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Delivery API", description = "배송 관리 API")
@RestController
@RequestMapping("/api/delivery")
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryUseCase deliveryUseCase;

    @Operation(summary = "배송 상태 수동 변경", description = "배송 상태를 수동으로 변경합니다 (테스트/관리 목적)")
    @PatchMapping("/{orderId}/status")
    public ResponseEntity<ApiResponse<Void>> updateDeliveryStatus(
            @PathVariable Long orderId,
            @RequestParam OrderStatus status) {

        try {
            switch (status) {
                case SHIPPED -> {
                    // 🔧 현재 상태 확인 후 처리
                    DeliveryInfoResponse currentStatus = deliveryUseCase.getDeliveryInfo(orderId);
                    if (currentStatus.status() == OrderStatus.SHIPPED) {
                        return ResponseEntity.ok(ApiResponse.success(null)); // 이미 배송 중 상태
                    }
                    deliveryUseCase.ship(orderId);
                }
                case DELIVERED -> {
                    // 🔧 현재 상태 확인 후 처리
                    DeliveryInfoResponse currentStatus = deliveryUseCase.getDeliveryInfo(orderId);
                    if (currentStatus.status() == OrderStatus.DELIVERED) {
                        return ResponseEntity.ok(ApiResponse.success(null)); // 이미 배송 완료 상태
                    }
                    deliveryUseCase.completeDelivery(orderId);
                }
                default -> throw new IllegalArgumentException("지원하지 않는 배송 상태: " + status);
            }

            return ResponseEntity.ok(ApiResponse.success(null));

        } catch (IllegalStateException e) {
            // 상태 전환 불가능한 경우 명확한 메시지 제공
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("상태 전환 실패: " + e.getMessage()));
        }
    }

    @Operation(summary = "배송 정보 조회", description = "주문의 배송 정보를 조회합니다")
    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<DeliveryInfoResponse>> getDeliveryInfo(
            @PathVariable Long orderId) {
        DeliveryInfoResponse deliveryInfo = deliveryUseCase.getDeliveryInfo(orderId);
        return ResponseEntity.ok(ApiResponse.success(deliveryInfo));
    }

    @Operation(summary = "배송 추적 정보 조회", description = "배송 추적 상세 정보를 조회합니다")
    @GetMapping("/{orderId}/tracking")
    public ResponseEntity<ApiResponse<DeliveryInfoResponse>> getTrackingInfo(
            @PathVariable Long orderId) {
        DeliveryInfoResponse trackingInfo = deliveryUseCase.getTrackingInfo(orderId);
        return ResponseEntity.ok(ApiResponse.success(trackingInfo));
    }
}