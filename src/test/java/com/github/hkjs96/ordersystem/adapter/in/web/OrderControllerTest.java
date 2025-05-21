package com.github.hkjs96.ordersystem.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.hkjs96.ordersystem.dto.request.OrderRequest;
import com.github.hkjs96.ordersystem.dto.response.OrderResponse;
import com.github.hkjs96.ordersystem.domain.model.OrderStatus;
import com.github.hkjs96.ordersystem.exception.PaymentException;
import com.github.hkjs96.ordersystem.exception.ShipmentException;
import com.github.hkjs96.ordersystem.port.in.OrderUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.doThrow;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
class OrderControllerIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @MockitoBean
    private OrderUseCase orderUseCase;

    @Test
    @DisplayName("POST /api/orders — 주문 생성 성공")
    void createOrder_success() throws Exception {
        var dummy = new OrderResponse(1L, 1L, 2, OrderStatus.CREATED);
        given(orderUseCase.createOrder(any(OrderRequest.class)))
                .willReturn(dummy);

        var req = new OrderRequest(1L, 2);
        mvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(1))
                .andExpect(jsonPath("$.data.status").value("CREATED"));
    }

    @Test
    @DisplayName("POST /api/orders — 재고 부족 시 400 Bad Request")
    void createOrder_badRequest_onIllegalState() throws Exception {
        doThrow(new IllegalStateException("재고 부족"))
                .when(orderUseCase).createOrder(any(OrderRequest.class));

        var req = new OrderRequest(1L, 2);
        mvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("재고 부족"));
    }

    @Test
    @DisplayName("POST /api/orders — 결제 실패 시 502 Bad Gateway")
    void createOrder_badGateway_onPaymentException() throws Exception {
        doThrow(new PaymentException("결제 실패"))
                .when(orderUseCase).createOrder(any(OrderRequest.class));

        var req = new OrderRequest(1L, 2);
        mvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("결제 실패"));
    }

    @Test
    @DisplayName("POST /api/orders — 배송 실패 시 500 Internal Server Error")
    void createOrder_internalError_onShipmentException() throws Exception {
        doThrow(new ShipmentException("배송 실패"))
                .when(orderUseCase).createOrder(any(OrderRequest.class));

        var req = new OrderRequest(1L, 2);
        mvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("배송 실패"));
    }

    @Test
    @DisplayName("DELETE /api/orders/{id} — 주문 취소 성공")
    void cancelOrder_success() throws Exception {
        // 정상 호출 시 예외 없이 200 OK
        mvc.perform(delete("/api/orders/{id}", 5L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("DELETE /api/orders/{id} — 없는 주문 취소 시 404 Not Found")
    void cancelOrder_notFound() throws Exception {
        doThrow(new IllegalArgumentException("not found"))
                .when(orderUseCase).cancelOrder(999L);

        mvc.perform(delete("/api/orders/{id}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not found"));
    }
}
