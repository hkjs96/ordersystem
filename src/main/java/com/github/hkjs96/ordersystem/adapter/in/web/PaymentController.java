package com.github.hkjs96.ordersystem.adapter.in.web;

import com.github.hkjs96.ordersystem.common.ApiResponse;
import com.github.hkjs96.ordersystem.port.in.PaymentUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Payment API")
@RestController
@RequestMapping("/api/orders/{orderId}/payment")
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentUseCase paymentUseCase;

    @Operation(summary = "결제 요청 시작")
    @PostMapping
    public ResponseEntity<ApiResponse<Void>> initiate(@PathVariable Long orderId) {
        paymentUseCase.initiatePayment(orderId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(null));
    }

    @Operation(summary = "결제 콜백 처리")
    @PostMapping("/complete")
    public ResponseEntity<ApiResponse<Void>> complete(
            @PathVariable Long orderId,
            @RequestParam boolean success) {
        paymentUseCase.completePayment(orderId, success);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}