package com.github.hkjs96.ordersystem.dto.response;

import com.github.hkjs96.ordersystem.domain.model.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "배송 정보 응답 DTO")
public record DeliveryInfoResponse(
        @Schema(description = "배송 ID", example = "1")
        Long deliveryId,

        @Schema(description = "주문 ID", example = "123")
        Long orderId,

        @Schema(description = "배송 상태", example = "SHIPPED")
        OrderStatus status,

        @Schema(description = "송장 번호", example = "1234567890123")
        String trackingNumber,

        @Schema(description = "택배사", example = "CJ대한통운")
        String courierCompany,

        @Schema(description = "배송 시작일시", example = "2025-05-30T10:30:00")
        LocalDateTime startedAt,

        @Schema(description = "배송 완료일시", example = "2025-05-31T14:20:00")
        LocalDateTime completedAt,

        @Schema(description = "예상 도착일", example = "2025-05-31T18:00:00")
        LocalDateTime estimatedArrival,

        @Schema(description = "최근 배송 상태 메시지", example = "상품이 배송 중입니다")
        String lastStatusMessage
) {

    /**
     * 배송 준비 상태용 생성자
     */
    public static DeliveryInfoResponse preparing(Long deliveryId, Long orderId, LocalDateTime startedAt) {
        return new DeliveryInfoResponse(
                deliveryId,
                orderId,
                OrderStatus.SHIPMENT_PREPARING,
                null,
                null,
                startedAt,
                null,
                null,
                "배송 준비 중입니다"
        );
    }

    /**
     * 배송 중 상태용 생성자
     */
    public static DeliveryInfoResponse shipped(Long deliveryId, Long orderId,
                                               String trackingNumber, String courierCompany,
                                               LocalDateTime startedAt, LocalDateTime estimatedArrival) {
        return new DeliveryInfoResponse(
                deliveryId,
                orderId,
                OrderStatus.SHIPPED,
                trackingNumber,
                courierCompany,
                startedAt,
                null,
                estimatedArrival,
                "상품이 배송 중입니다"
        );
    }

    /**
     * 배송 완료 상태용 생성자
     */
    public static DeliveryInfoResponse delivered(Long deliveryId, Long orderId,
                                                 String trackingNumber, String courierCompany,
                                                 LocalDateTime startedAt, LocalDateTime completedAt) {
        return new DeliveryInfoResponse(
                deliveryId,
                orderId,
                OrderStatus.DELIVERED,
                trackingNumber,
                courierCompany,
                startedAt,
                completedAt,
                null,
                "배송이 완료되었습니다"
        );
    }
}