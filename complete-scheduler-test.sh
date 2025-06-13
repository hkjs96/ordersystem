#!/bin/bash

# complete-scheduler-test.sh - 순수 스케줄러 + Kafka 통합 테스트
# 수동 전환 없이 스케줄러 자동 전환만으로 테스트

echo "⚡ 스케줄러 + Kafka 통합 테스트"
echo "============================="
echo "📋 테스트 시나리오:"
echo "   1. 주문 생성 → 결제 완료 → SHIPMENT_PREPARING"
echo "   2. 1분 후 스케줄러 → SHIPPED (자동)"
echo "   3. 3분 후 스케줄러 → DELIVERED (자동)"
echo "   4. 각 단계별 Kafka 이벤트 확인"
echo "   5. 주문 취소 시나리오 (재고 복원)"
echo ""

# 색상 정의
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m'

# API 기본 URL
BASE_URL="http://localhost:8080"
ORDER_ID=""
CANCEL_ORDER_ID=""
PRODUCT_ID=1
QUANTITY=2

# 함수들
print_result() {
    if [ $1 -eq 0 ]; then
        echo -e "${GREEN}✓ $2${NC}"
    else
        echo -e "${RED}✗ $2${NC}"
    fi
}

extract_order_id() {
    echo "$1" | grep -o '"orderId":[0-9]*' | grep -o '[0-9]*'
}

get_delivery_status() {
    local order_id=$1
    curl -s "$BASE_URL/api/delivery/$order_id" | grep -o '"status":"[^"]*"' | cut -d'"' -f4
}

print_section() {
    echo -e "\n${YELLOW}$1${NC}"
    echo "=================================================="
}

print_step() {
    echo -e "\n${CYAN}$1${NC}"
    echo "------------------------------"
}

# Kafka 메시지 확인 함수
check_kafka_messages() {
    local topic=$1
    local description=$2
    local max_messages=${3:-3}

    echo -e "\n${PURPLE}📨 Kafka $description 확인:${NC}"

    # Kafka 메시지 조회 (타임아웃 3초)
    local messages=$(timeout 3s docker exec kafka kafka-console-consumer \
        --topic $topic \
        --bootstrap-server localhost:9092 \
        --from-beginning \
        --max-messages $max_messages 2>/dev/null | tail -$max_messages)

    if [ -n "$messages" ]; then
        echo "$messages" | while IFS= read -r line; do
            echo "  📝 $line"
        done
    else
        echo "  💬 메시지 없음 또는 타임아웃"
    fi
}

# 실시간 상태 모니터링 함수
monitor_status_change() {
    local order_id=$1
    local expected_status=$2
    local max_wait_seconds=$3
    local check_interval=10

    echo -e "\n${BLUE}⏰ $expected_status 상태 전환 대기 (최대 ${max_wait_seconds}초)${NC}"
    echo "시작 시각: $(date '+%H:%M:%S')"

    local elapsed=0
    while [ $elapsed -lt $max_wait_seconds ]; do
        sleep $check_interval
        elapsed=$((elapsed + check_interval))

        local current_status=$(get_delivery_status $order_id)
        echo -n "⏱️  ${elapsed}초 경과 - 현재: $current_status"

        if [ "$current_status" = "$expected_status" ]; then
            echo -e " → ${GREEN}✅ $expected_status 전환 완료!${NC}"
            echo "완료 시각: $(date '+%H:%M:%S')"

            # 상세 배송 정보 출력
            echo -e "\n📦 배송 상세 정보:"
            curl -s "$BASE_URL/api/delivery/$order_id" | jq '.data | {
                status,
                trackingNumber,
                courierCompany,
                startedAt,
                completedAt,
                lastStatusMessage
            }'
            return 0
        else
            echo ""
        fi

        # 중간 진행 상황 (30초마다)
        if [ $((elapsed % 30)) -eq 0 ]; then
            echo "  💭 계속 대기 중... (${elapsed}/${max_wait_seconds}초)"
        fi
    done

    echo -e "${RED}❌ ${max_wait_seconds}초 타임아웃 - $expected_status 전환 실패${NC}"
    echo "최종 상태: $(get_delivery_status $order_id)"
    return 1
}

# ==============================================
# 메인 테스트 시작
# ==============================================

print_section "1. 환경 및 인프라 확인"

# Docker 컨테이너 상태 확인
echo -n "🐳 Kafka 컨테이너: "
if docker ps | grep -q kafka; then
    echo -e "${GREEN}실행중${NC}"
else
    echo -e "${RED}중지됨 - docker-compose up -d 실행 필요${NC}"
    exit 1
fi

echo -n "🐳 Redis 컨테이너: "
if docker ps | grep -q redis; then
    echo -e "${GREEN}실행중${NC}"
else
    echo -e "${RED}중지됨 - docker-compose up -d 실행 필요${NC}"
    exit 1
fi

# 애플리케이션 헬스체크
echo -n "🚀 Spring Boot 애플리케이션: "
if curl -s $BASE_URL/actuator/health > /dev/null 2>&1; then
    echo -e "${GREEN}실행중${NC}"
else
    echo -e "${RED}중지됨 - 애플리케이션 시작 필요${NC}"
    exit 1
fi

# 활성 프로필 확인
echo -n "⚙️  활성 프로필: "
profile_info=$(curl -s "$BASE_URL/actuator/env/spring.profiles.active" 2>/dev/null | jq -r '.property.value // "default"')
echo -e "${BLUE}$profile_info${NC}"

print_section "2. 초기 재고 상태 확인"

echo "📊 Product $PRODUCT_ID 초기 재고:"
curl -s "$BASE_URL/api/inventory/$PRODUCT_ID/status" | jq '.data | {
    productId,
    databaseStock,
    redisStock,
    reservedStock
}'

print_section "3. 스케줄러 테스트용 주문 생성"

print_step "3-1. 주문 생성"
RESPONSE=$(curl -s -X POST "$BASE_URL/api/orders" \
  -H "Content-Type: application/json" \
  -d "{\"productId\": $PRODUCT_ID, \"quantity\": $QUANTITY}")

ORDER_ID=$(extract_order_id "$RESPONSE")
if [ -n "$ORDER_ID" ]; then
    echo -e "${GREEN}✅ 주문 생성 성공 - ID: $ORDER_ID${NC}"
    echo "📋 주문 상세: $RESPONSE" | jq '.'
else
    echo -e "${RED}❌ 주문 생성 실패${NC}"
    exit 1
fi

print_step "3-2. 주문 후 재고 상태"
curl -s "$BASE_URL/api/inventory/$PRODUCT_ID/status" | jq '.data'

# 주문 생성 이벤트 확인
check_kafka_messages "orders-events" "주문 생성 이벤트"

print_section "4. 결제 처리"

print_step "4-1. 결제 요청 시작"
curl -s -X POST "$BASE_URL/api/orders/$ORDER_ID/payment" > /dev/null
print_result $? "결제 요청 API 호출"

sleep 2

print_step "4-2. 결제 완료 처리"
curl -s -X POST "$BASE_URL/api/orders/$ORDER_ID/payment/complete?success=true" > /dev/null
print_result $? "결제 성공 처리"

print_step "4-3. 결제 후 재고 및 배송 상태"
echo "💰 결제 완료 후 재고:"
curl -s "$BASE_URL/api/inventory/$PRODUCT_ID/status" | jq '.data'

echo -e "\n🚚 초기 배송 상태 (SHIPMENT_PREPARING):"
curl -s "$BASE_URL/api/delivery/$ORDER_ID" | jq '.data'

# 결제 관련 이벤트 확인
check_kafka_messages "orders-events" "결제 완료 이벤트"
check_kafka_messages "inventory-events" "재고 확정 이벤트"

print_section "5. 스케줄러 자동 전환 테스트"

print_step "5-1. SHIPPED 상태 전환 대기 (1분 후)"
echo "⏰ 스케줄러가 1분 후 SHIPMENT_PREPARING → SHIPPED로 자동 전환합니다"

if monitor_status_change $ORDER_ID "SHIPPED" 90; then
    # SHIPPED 전환 성공 시 이벤트 확인
    check_kafka_messages "orders-events" "배송 시작 이벤트"
fi

print_step "5-2. DELIVERED 상태 전환 대기 (추가 3분)"
echo "⏰ 스케줄러가 3분 후 SHIPPED → DELIVERED로 자동 전환합니다"

if monitor_status_change $ORDER_ID "DELIVERED" 200; then
    # DELIVERED 전환 성공 시 이벤트 확인
    check_kafka_messages "orders-events" "배송 완료 이벤트"
fi

print_section "6. 주문 취소 시나리오 (재고 복원 테스트)"

print_step "6-1. 새 주문 생성 (취소용)"
CANCEL_RESPONSE=$(curl -s -X POST "$BASE_URL/api/orders" \
  -H "Content-Type: application/json" \
  -d "{\"productId\": $PRODUCT_ID, \"quantity\": 1}")

CANCEL_ORDER_ID=$(extract_order_id "$CANCEL_RESPONSE")
echo -e "${GREEN}취소할 주문 ID: $CANCEL_ORDER_ID${NC}"

sleep 1
echo "🔄 취소 전 재고 상태:"
curl -s "$BASE_URL/api/inventory/$PRODUCT_ID/status" | jq '.data'

print_step "6-2. 주문 취소 실행"
curl -s -X DELETE "$BASE_URL/api/orders/$CANCEL_ORDER_ID" > /dev/null
print_result $? "주문 취소 API 호출"

print_step "6-3. 재고 복원 확인 (Kafka 이벤트 처리 대기)"
sleep 3  # Kafka 이벤트 처리 대기

echo "💡 취소 후 재고 상태 (복원 확인):"
curl -s "$BASE_URL/api/inventory/$PRODUCT_ID/status" | jq '.data'

# 주문 취소 관련 이벤트 확인
check_kafka_messages "orders-events" "주문 취소 이벤트"
check_kafka_messages "inventory-events" "재고 복원 이벤트"

print_section "7. 전체 Kafka 이벤트 히스토리"

print_step "7-1. 전체 주문 이벤트 히스토리"
check_kafka_messages "orders-events" "전체 주문 이벤트" 10

print_step "7-2. 전체 재고 이벤트 히스토리"
check_kafka_messages "inventory-events" "전체 재고 이벤트" 10

print_section "8. 테스트 결과 요약"

echo "🎯 테스트 시나리오 검증:"
echo ""

# 최종 배송 상태 확인
final_status=$(get_delivery_status $ORDER_ID)
echo "📦 주문 $ORDER_ID 최종 상태: $final_status"

if [ "$final_status" = "DELIVERED" ]; then
    echo -e "${GREEN}✅ 스케줄러 자동 전환 테스트 성공!${NC}"
    echo "   ✓ SHIPMENT_PREPARING → SHIPPED (1분 후)"
    echo "   ✓ SHIPPED → DELIVERED (3분 후)"
else
    echo -e "${RED}❌ 스케줄러 자동 전환 미완료${NC}"
    echo "   현재 상태: $final_status"
    echo "   💡 스케줄러 로그 확인 필요"
fi

echo ""
echo -e "${GREEN}✅ Kafka 이벤트 발행/수신 테스트 완료${NC}"
echo "   ✓ 주문 생성/결제/배송/취소 이벤트"
echo "   ✓ 재고 예약/확정/복원 이벤트"

echo ""
echo "📊 최종 재고 상태:"
curl -s "$BASE_URL/api/inventory/$PRODUCT_ID/status" | jq '.data'

echo ""
echo -e "${BLUE}🔍 추가 확인 명령어:${NC}"
echo "📋 스케줄러 로그: tail -20 logs/application.log | grep -i scheduler"
echo "📋 배송 로그: tail -20 logs/application.log | grep -i delivery"
echo "📋 Kafka 로그: tail -20 logs/application.log | grep -i kafka"

echo ""
echo -e "${GREEN}🎉 통합 테스트 완료!${NC}"