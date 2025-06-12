package com.github.hkjs96.ordersystem.adapter.in.scheduler;

import com.github.hkjs96.ordersystem.domain.entity.Delivery;
import com.github.hkjs96.ordersystem.domain.model.OrderStatus;
import com.github.hkjs96.ordersystem.domain.repository.DeliveryRepository;
import com.github.hkjs96.ordersystem.port.in.DeliveryUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 배송 상태 자동 처리 스케줄러
 * - SHIPMENT_PREPARING → SHIPPED (30분 후)
 * - SHIPPED → DELIVERED (2시간 후)
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        value = "ordersystem.scheduler.delivery.enabled",
        havingValue = "true",
        matchIfMissing = false
)
public class DeliveryStatusScheduler {

    private final DeliveryRepository deliveryRepository;
    private final DeliveryUseCase deliveryUseCase;

    /**
     * 배송 준비 → 배송 중 자동 처리
     * 30분 후 자동으로 배송 시작 처리
     */
    @Scheduled(fixedRate = 300000) // 5분마다 실행
    public void processShipmentUpdates() {
        log.debug("배송 시작 자동 처리 스케줄러 실행");

        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(30);

        List<Delivery> preparingDeliveries = deliveryRepository
                .findByStatusAndStartedAtBefore(OrderStatus.SHIPMENT_PREPARING, cutoffTime);

        log.info("배송 시작 대상: {}건", preparingDeliveries.size());

        for (Delivery delivery : preparingDeliveries) {
            try {
                deliveryUseCase.ship(delivery.getOrderId());
                log.info("자동 배송 시작 완료: orderId={}", delivery.getOrderId());
            } catch (Exception e) {
                log.error("자동 배송 시작 실패: orderId={}, error={}",
                        delivery.getOrderId(), e.getMessage());
            }
        }
    }

    /**
     * 배송 중 → 배송 완료 자동 처리
     * 2시간 후 자동으로 배송 완료 처리
     */
    @Scheduled(fixedRate = 600000) // 10분마다 실행
    public void processDeliveryCompletions() {
        log.debug("배송 완료 자동 처리 스케줄러 실행");

        LocalDateTime cutoffTime = LocalDateTime.now().minusHours(2);

        List<Delivery> shippedDeliveries = deliveryRepository
                .findByStatusAndShippedAtBefore(OrderStatus.SHIPPED, cutoffTime);

        log.info("배송 완료 대상: {}건", shippedDeliveries.size());

        for (Delivery delivery : shippedDeliveries) {
            try {
                deliveryUseCase.completeDelivery(delivery.getOrderId());
                log.info("자동 배송 완료: orderId={}", delivery.getOrderId());
            } catch (Exception e) {
                log.error("자동 배송 완료 실패: orderId={}, error={}",
                        delivery.getOrderId(), e.getMessage());
            }
        }
    }

    /**
     * 배송 상태 통계 로깅 (모니터링 목적)
     */
    @Scheduled(cron = "0 0 * * * *") // 1시간마다
    public void logDeliveryStatistics() {
        long preparingCount = deliveryRepository.countByStatus(OrderStatus.SHIPMENT_PREPARING);
        long shippedCount = deliveryRepository.countByStatus(OrderStatus.SHIPPED);
        long deliveredCount = deliveryRepository.countByStatus(OrderStatus.DELIVERED);

        log.info("배송 상태 통계 - 준비중: {}건, 배송중: {}건, 완료: {}건",
                preparingCount, shippedCount, deliveredCount);
    }
}