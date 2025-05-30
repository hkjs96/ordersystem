package com.github.hkjs96.ordersystem.domain.service;

import com.github.hkjs96.ordersystem.adapter.out.OrderRepositoryPort;
import com.github.hkjs96.ordersystem.domain.entity.Delivery;
import com.github.hkjs96.ordersystem.domain.entity.Order;
import com.github.hkjs96.ordersystem.domain.model.OrderEvent;
import com.github.hkjs96.ordersystem.domain.model.OrderStatus;
import com.github.hkjs96.ordersystem.domain.repository.DeliveryRepository;
import com.github.hkjs96.ordersystem.dto.response.DeliveryInfoResponse;
import com.github.hkjs96.ordersystem.port.in.DeliveryUseCase;
import com.github.hkjs96.ordersystem.port.out.PublishEventPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class DeliveryService implements DeliveryUseCase {

    private final DeliveryRepository deliveryRepository;
    private final OrderRepositoryPort orderRepositoryPort;
    private final PublishEventPort eventPort;

    @Override
    public void initiateShipment(Long orderId) {
        log.info("배송 준비 시작: orderId={}", orderId);

        Delivery delivery = Delivery.builder()
                .orderId(orderId)
                .build();
        deliveryRepository.save(delivery);

        // 주문 상태 변경
        Order order = orderRepositoryPort.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("주문 미발견: " + orderId));

        // ① JPA 영속성 컨텍스트에 로드된 Order 객체의 상태만 변경해도
        //    flush 시점에 자동으로 UPDATE 쿼리가 나갑니다.
        //    따라서 아래 save() 호출은 사실 불필요합니다.
        order.changeStatus(OrderStatus.SHIPMENT_PREPARING);

        // orderRepository.save(order);  ← 제거 가능

        // ② 트랜잭션 커밋 후에만 이벤트를 내보내야
        //    롤백 시 이벤트 중복/잘못 발행을 방지할 수 있습니다.
        //    Spring 의 TransactionSynchronizationManager 를 활용하거나,
        //    도메인 이벤트 퍼블리셔(예: ApplicationEventPublisher + @TransactionalEventListener) 와 결합하세요.
        eventPort.publishOrderEvent(new OrderEvent(orderId, OrderStatus.SHIPMENT_PREPARING));

        log.info("배송 준비 완료: orderId={}, deliveryId={}", orderId, delivery.getId());
    }

    @Override
    public void ship(Long orderId) {
        log.info("배송 시작 처리: orderId={}", orderId);

        // 1) Delivery 조회 & 상태 검증
        Delivery delivery = deliveryRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalStateException("배송이 시작되지 않은 주문ID=" + orderId));

        if (delivery.getStatus() != OrderStatus.SHIPMENT_PREPARING) {
            throw new IllegalStateException("배송 준비 상태가 아님, 현재 상태=" + delivery.getStatus());
        }

        // 2) Delivery 엔티티 상태 변경
        delivery.markShipped();
        deliveryRepository.save(delivery);

        // 3) Order 상태 전이
        Order order = orderRepositoryPort.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("주문 미발견: " + orderId));
        order.changeStatus(OrderStatus.SHIPPED);
        orderRepositoryPort.save(order);

        // 4) Kafka 이벤트 발행
        eventPort.publishOrderEvent(new OrderEvent(orderId, OrderStatus.SHIPPED));

        log.info("배송 시작 완료: orderId={}, deliveryId={}", orderId, delivery.getId());
    }

    public void completeDelivery(Long orderId) {
        log.info("배송 완료 처리: orderId={}", orderId);

        Delivery delivery = deliveryRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalStateException("배송이 시작되지 않은 주문ID=" + orderId));

        if (delivery.getStatus() != OrderStatus.SHIPMENT_PREPARING) {
            throw new IllegalStateException("배송 준비 상태가 아님, 현재 상태=" + delivery.getStatus());
        }

        // 2) Delivery 엔티티 업데이트
        delivery.markDelivered();
        deliveryRepository.save(delivery);

        Order order = orderRepositoryPort.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("잘못된 주문ID=" + orderId));

        order.changeStatus(OrderStatus.DELIVERED);
        orderRepositoryPort.save(order);

        eventPort.publishOrderEvent(
                new OrderEvent(orderId, OrderStatus.DELIVERED, Instant.now())
        );

        log.info("배송 완료: orderId={}, deliveryId={}", orderId, delivery.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public DeliveryInfoResponse getDeliveryInfo(Long orderId) {
        log.debug("배송 정보 조회: orderId={}", orderId);

        Delivery delivery = deliveryRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("배송 정보 없음: orderId=" + orderId));

        return switch (delivery.getStatus()) {
            case SHIPMENT_PREPARING -> DeliveryInfoResponse.preparing(
                    delivery.getId(), orderId, delivery.getStartedAt()
            );
            case SHIPPED -> DeliveryInfoResponse.shipped(
                    delivery.getId(), orderId,
                    delivery.getTrackingNumber(), delivery.getCourierCompany(), // 🔧 엔티티 필드 사용
                    delivery.getStartedAt(),
                    LocalDateTime.now().plusDays(1) // 예상 도착일
            );
            case DELIVERED -> DeliveryInfoResponse.delivered(
                    delivery.getId(), orderId,
                    delivery.getTrackingNumber(), delivery.getCourierCompany(), // 🔧 엔티티 필드 사용
                    delivery.getStartedAt(), delivery.getCompletedAt()
            );
            default -> throw new IllegalStateException("알 수 없는 배송 상태: " + delivery.getStatus());
        };
    }

    @Override
    @Transactional(readOnly = true)
    public DeliveryInfoResponse getTrackingInfo(Long orderId) {
        log.debug("배송 추적 정보 조회: orderId={}", orderId);

        // 🔧 간소화: 기본 배송 정보와 동일하게 처리
        // 실제 택배사 API 연동 시에만 추가 구현
        return getDeliveryInfo(orderId);
    }
}
