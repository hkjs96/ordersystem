package com.github.hkjs96.ordersystem.adapter.in.web;

import com.github.hkjs96.ordersystem.adapter.out.cache.InventoryRepository;
import com.github.hkjs96.ordersystem.common.ApiResponse;
import com.github.hkjs96.ordersystem.port.out.InventoryRepositoryPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Inventory API", description = "재고 관리 및 모니터링 API")
@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryRepositoryPort inventoryPort;

    @Operation(summary = "재고 상태 조회", description = "상품의 DB/Redis 재고 상태를 조회합니다")
    @GetMapping("/{productId}/status")
    public ResponseEntity<ApiResponse<InventoryRepository.StockStatus>> getStockStatus(
            @PathVariable Long productId) {

        if (inventoryPort instanceof InventoryRepository inventoryRepository) {
            InventoryRepository.StockStatus status = inventoryRepository.getStockStatus(productId);
            return ResponseEntity.ok(ApiResponse.success(status));
        }

        return ResponseEntity.badRequest()
                .body(ApiResponse.error("재고 상태 조회 불가"));
    }

    @Operation(summary = "재고 동기화", description = "DB와 Redis 간 재고 정보를 동기화합니다")
    @PostMapping("/{productId}/sync")
    public ResponseEntity<ApiResponse<Void>> syncStock(@PathVariable Long productId) {

        if (inventoryPort instanceof InventoryRepository inventoryRepository) {
            inventoryRepository.syncWithDatabase(productId);
            return ResponseEntity.ok(ApiResponse.success(null));
        }

        return ResponseEntity.badRequest()
                .body(ApiResponse.error("재고 동기화 불가"));
    }

    @Operation(summary = "재고 가용성 확인", description = "특정 수량의 재고 가용성을 확인합니다")
    @GetMapping("/{productId}/available")
    public ResponseEntity<ApiResponse<Boolean>> checkStockAvailability(
            @PathVariable Long productId,
            @RequestParam int quantity) {

        boolean available = inventoryPort.isStockAvailable(productId, quantity);
        return ResponseEntity.ok(ApiResponse.success(available));
    }
}
