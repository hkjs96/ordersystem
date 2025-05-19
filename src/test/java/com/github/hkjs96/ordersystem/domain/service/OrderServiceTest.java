package com.github.hkjs96.ordersystem.domain.service;

import com.github.hkjs96.ordersystem.domain.entity.Order;
import com.github.hkjs96.ordersystem.domain.model.OrderEvent;
import com.github.hkjs96.ordersystem.domain.model.OrderStatus;
import com.github.hkjs96.ordersystem.domain.repository.OrderRepository;
import com.github.hkjs96.ordersystem.dto.request.CreateOrderRequest;
import com.github.hkjs96.ordersystem.exception.PaymentException;
import com.github.hkjs96.ordersystem.exception.ShipmentException;
import com.github.hkjs96.ordersystem.port.out.InventoryRepositoryPort;
import com.github.hkjs96.ordersystem.port.out.PublishEventPort;
import com.github.hkjs96.ordersystem.dto.response.OrderResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private InventoryRepositoryPort inventoryPort;

    @Mock
    private PublishEventPort eventPort;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentService paymentService;

    @Mock
    private DeliveryService deliveryService;

    @InjectMocks
    private OrderService orderService;

    private CreateOrderRequest req;

    @BeforeEach
    void setUp() {
        req = new CreateOrderRequest(
                /* productId= */ 1L,
                /* quantity= */ 2
        );
    }

    @Test
    @DisplayName("재고 충분 시 주문 생성 성공 및 이벤트 발행")
    void createOrder_Success() {
        // given
        given(inventoryPort.isStockAvailable(req.productId(), req.quantity()))
                .willReturn(true);

        // 빌더 패턴을 사용하여 MockOrder 생성
        Order mockOrder = Order.builder()
                .id(123L)
                .status(OrderStatus.CREATED)
                .productId(req.productId())
                .quantity(req.quantity())
                .build();

        // save 메서드가 호출될 때 mockOrder를 반환하도록 설정
        given(orderRepository.save(any(Order.class)))
                .willReturn(mockOrder);

        // paymentService와 deliveryService 메서드가 정상적으로 동작하도록 설정
        // 반환값이 없는 void 메서드이므로 willDoNothing() 사용
        willDoNothing().given(paymentService).processPayment(eq(123L));
        willDoNothing().given(deliveryService).initiateShipment(eq(123L));

        // when
        OrderResponse resp = orderService.createOrder(req);

        // then
        assertNotNull(resp.orderId());
        assertEquals(123L, resp.orderId());
        assertEquals(OrderStatus.CREATED, resp.status());

        // 주문 저장 확인
        then(orderRepository)
                .should()
                .save(any(Order.class));

        // 이벤트 발행 확인
        then(eventPort)
                .should()
                .publishOrderEvent(argThat((OrderEvent ev) ->
                        ev.orderId().equals(123L) &&
                                ev.status() == OrderStatus.CREATED &&
                                ev.timestamp() != null
                ));

        // 재고 예약 확인
        then(inventoryPort)
                .should()
                .reserveStock(eq(req.productId()), eq(req.quantity()));

        // 결제 처리 확인
        then(paymentService)
                .should()
                .processPayment(eq(123L));

        // 배송 시작 확인
        then(deliveryService)
                .should()
                .initiateShipment(eq(123L));
    }

    @Test
    @DisplayName("재고 부족 시 주문 생성 예외")
    void createOrder_OutOfStock() {
        // given
        given(inventoryPort.isStockAvailable(req.productId(), req.quantity()))
                .willReturn(false);

        // when / then
        assertThrows(IllegalStateException.class,
                () -> orderService.createOrder(req),
                "out of stock 예외가 발생해야 합니다"
        );

        // 저장, 발행, 예약, 결제, 배송 모두 호출되지 않아야 함
        then(orderRepository).should(never()).save(any());
        then(eventPort).should(never()).publishOrderEvent(any());
        then(inventoryPort).should(never()).reserveStock(anyLong(), anyInt());
        then(paymentService).should(never()).processPayment(anyLong());
        then(deliveryService).should(never()).initiateShipment(anyLong());
    }

    @Test
    @DisplayName("결제 처리 실패 시 PaymentException 전파 및 PAYMENT_FAILED 이벤트 발행")
    void createOrder_PaymentFailure() {
        // given
        given(inventoryPort.isStockAvailable(req.productId(), req.quantity()))
                .willReturn(true);
        Order mockOrder = Order.builder().id(123L).status(OrderStatus.CREATED)
                .productId(req.productId()).quantity(req.quantity()).build();
        given(orderRepository.save(any(Order.class))).willReturn(mockOrder);
        willDoNothing().given(inventoryPort)
                .reserveStock(eq(req.productId()), eq(req.quantity()));
        doThrow(new RuntimeException("gateway down"))
                .when(paymentService).processPayment(123L);

        // when / then
        PaymentException ex = assertThrows(
                PaymentException.class,
                () -> orderService.createOrder(req),
                "결제 실패 시 PaymentException이 전파되어야 합니다"
        );
        assertThat(ex.getMessage()).contains("결제 실패: orderId=123");

        // PAYMENT_FAILED 이벤트 발행 확인
        then(eventPort).should().publishOrderEvent(argThat(ev ->
                ev.orderId().equals(123L) &&
                        ev.status() == OrderStatus.PAYMENT_FAILED
        ));

        // 배송 호출 안 함
        then(deliveryService).should(never()).initiateShipment(anyLong());
    }

    @Test
    @DisplayName("배송 시작 실패 시 ShipmentException 전파 및 CANCELLED 이벤트 발행")
    void createOrder_ShipmentFailure() {
        // given
        given(inventoryPort.isStockAvailable(req.productId(), req.quantity()))
                .willReturn(true);
        Order mockOrder = Order.builder().id(456L).status(OrderStatus.CREATED)
                .productId(req.productId()).quantity(req.quantity()).build();
        given(orderRepository.save(any(Order.class))).willReturn(mockOrder);
        willDoNothing().given(inventoryPort)
                .reserveStock(eq(req.productId()), eq(req.quantity()));
        willDoNothing().given(paymentService).processPayment(456L);
        doThrow(new RuntimeException("carrier error"))
                .when(deliveryService).initiateShipment(456L);

        // when / then
        ShipmentException ex = assertThrows(
                ShipmentException.class,
                () -> orderService.createOrder(req),
                "배송 실패 시 ShipmentException이 전파되어야 합니다"
        );
        assertThat(ex.getMessage()).contains("배송 시작 실패: orderId=456");

        // CANCELLED 이벤트 발행 확인
        then(eventPort).should().publishOrderEvent(argThat(ev ->
                ev.orderId().equals(456L) &&
                        ev.status() == OrderStatus.CANCELLED
        ));
    }
}
