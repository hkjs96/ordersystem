# 🛒 Mini E-commerce Order System

Spring Boot + DDD + Hexagonal Architecture를 활용한 미니 이커머스 주문 시스템

## 📋 목차
- [개요](#-개요)
- [기술 스택](#-기술-스택)
- [아키텍처](#-아키텍처)
- [주요 기능](#-주요-기능)
- [시작하기](#-시작하기)
- [API 문서](#-api-문서)
- [프로젝트 구조](#-프로젝트-구조)
- [주문 플로우](#-주문-플로우)
- [재고 관리 시스템](#-재고-관리-시스템)
- [배포](#-배포)
- [모니터링](#-모니터링)

## 🎯 개요

이 프로젝트는 실무에서 사용 가능한 수준의 이커머스 주문 시스템을 구현한 것입니다. DDD(Domain-Driven Design)와 헥사고날 아키텍처(Hexagonal Architecture)를 적용하여 확장 가능하고 유지보수가 용이한 구조로 설계되었습니다.

### 핵심 특징
- ✅ **완전한 주문 라이프사이클**: 주문 생성 → 결제 → 배송 → 완료/취소
- ✅ **하이브리드 재고 관리**: Redis(빠른 조회) + DB(영구 저장)
- ✅ **이벤트 기반 아키텍처**: Kafka를 통한 비동기 처리
- ✅ **자동화 배송 시스템**: 스케줄러 기반 상태 자동 전환
- ✅ **트랜잭션 안정성**: 분산 트랜잭션 고려한 설계

## 🛠 기술 스택

### Backend
- **Java 17** + **Spring Boot 3.x**
- **Spring Data JPA** (Hibernate)
- **H2 Database** (개발용, 운영시 PostgreSQL/MySQL 권장)

### Messaging & Cache
- **Apache Kafka** - 이벤트 스트리밍
- **Redis** - 캐싱 및 재고 관리

### Infrastructure
- **Docker & Docker Compose** - 컨테이너화
- **AWS EC2** - 배포 환경
- **GitHub Actions** - CI/CD (선택사항)

### Architecture
- **DDD** (Domain-Driven Design)
- **Hexagonal Architecture** (Port & Adapter Pattern)
- **Event-Driven Architecture**

## 🏗 아키텍처

### Hexagonal Architecture 구조
```
┌─────────────────────────────────────────────────────────┐
│                    Presentation Layer                    │
│          (REST Controllers, Schedulers, Events)          │
├─────────────────────────────────────────────────────────┤
│                    Application Layer                     │
│                  (Use Cases / Ports)                     │
├─────────────────────────────────────────────────────────┤
│                     Domain Layer                         │
│              (Entities, Services, Events)                │
├─────────────────────────────────────────────────────────┤
│                   Infrastructure Layer                   │
│        (JPA, Redis, Kafka, External Services)           │
└─────────────────────────────────────────────────────────┘
```

### 이벤트 흐름
```
Spring Events (도메인 내부)
└── OrderCancelledEvent → OrderCancelledListener
    
Kafka Events (시스템 간 통신)
└── OrderEvent → KafkaOrderEventListener
    ├── PAYMENT_COMPLETED → 배송 준비
    └── SHIPMENT_PREPARING → 배송 시작
```

## ✨ 주요 기능

### 1. 주문 관리
- 주문 생성 with 재고 검증
- 주문 취소 with 재고 복원
- 주문 상태 추적

### 2. 결제 처리
- 결제 요청 시작
- 결제 성공/실패 처리
- 실패 시 자동 재고 복원

### 3. 재고 관리 (하이브리드)
- Redis 기반 빠른 재고 확인
- DB 기반 실제 재고 차감
- 자동 동기화 및 복원

### 4. 배송 관리
- 자동 배송 상태 전환
    - 30분 후: SHIPMENT_PREPARING → SHIPPED
    - 2시간 후: SHIPPED → DELIVERED
- 송장번호 자동 생성
- 배송 추적 정보 제공

### 5. 모니터링
- 재고 상태 실시간 조회
- 배송 통계 자동 로깅
- 에러 추적 및 알림

## 🚀 시작하기

### 사전 요구사항
- Java 17 이상
- Docker & Docker Compose
- Git

### 1. 프로젝트 클론
```bash
git clone https://github.com/hkjs96/ordersystem.git
cd ordersystem
```

### 2. 인프라 실행 (Docker Compose)
```bash
# Redis와 Kafka 실행
docker-compose up -d
```

### 3. 애플리케이션 실행
```bash
# 개발 환경 실행
./gradlew bootRun

# 또는 JAR 빌드 후 실행
./gradlew clean build
java -jar build/libs/ordersystem-0.0.1-SNAPSHOT.jar
```

### 4. H2 Console 접속
- URL: http://localhost:8080/h2-console
- JDBC URL: `jdbc:h2:mem:ordersdb`
- Username: `sa`
- Password: (비어있음)

## 📚 API 문서

### Swagger UI
애플리케이션 실행 후: http://localhost:8080/swagger-ui.html

### 주요 API 엔드포인트

#### 주문 관리
| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/api/orders` | 새로운 주문 생성 |
| DELETE | `/api/orders/{orderId}` | 주문 취소 |

#### 결제 처리
| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/api/orders/{orderId}/payment` | 결제 요청 시작 |
| POST | `/api/orders/{orderId}/payment/complete` | 결제 완료 콜백 |

#### 배송 관리
| Method | Endpoint | 설명 |
|--------|----------|------|
| PATCH | `/api/delivery/{orderId}/status` | 배송 상태 수동 변경 |
| GET | `/api/delivery/{orderId}` | 배송 정보 조회 |
| GET | `/api/delivery/{orderId}/tracking` | 배송 추적 정보 조회 |

#### 재고 관리
| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/api/inventory/{productId}/status` | 재고 상태 조회 |
| POST | `/api/inventory/{productId}/sync` | DB-Redis 재고 동기화 |
| GET | `/api/inventory/{productId}/available?quantity={n}` | 재고 가용성 확인 |

### API 사용 예시

#### 1. 주문 생성
```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "productId": 1,
    "quantity": 2
  }'
```

#### 2. 결제 처리
```bash
# 결제 시작
curl -X POST http://localhost:8080/api/orders/1/payment

# 결제 완료 (성공)
curl -X POST http://localhost:8080/api/orders/1/payment/complete?success=true
```

#### 3. 배송 정보 조회
```bash
curl http://localhost:8080/api/delivery/1
```

## 📁 프로젝트 구조

```
src/main/java/com/github/hkjs96/ordersystem/
├── domain/                    # 도메인 레이어
│   ├── entity/               # JPA 엔티티
│   │   ├── Order.java
│   │   ├── Product.java
│   │   ├── Payment.java
│   │   └── Delivery.java
│   ├── service/              # 도메인 서비스
│   │   ├── OrderService.java
│   │   ├── PaymentService.java
│   │   └── DeliveryService.java
│   ├── event/                # 도메인 이벤트
│   │   └── OrderCancelledEvent.java
│   ├── model/                # 값 객체
│   │   ├── OrderStatus.java
│   │   └── OrderEvent.java
│   └── repository/           # 리포지토리 인터페이스
│
├── adapter/                   # 어댑터 레이어
│   ├── in/                   # 인바운드 어댑터
│   │   ├── web/             # REST 컨트롤러
│   │   ├── event/           # 이벤트 리스너
│   │   ├── messaging/       # Kafka 리스너
│   │   └── scheduler/       # 스케줄러
│   └── out/                  # 아웃바운드 어댑터
│       ├── cache/           # Redis 구현
│       ├── persistence/     # JPA 구현
│       ├── messaging/       # Kafka 발행
│       └── event/           # 이벤트 발행
│
├── port/                      # 포트 인터페이스
│   ├── in/                   # 인바운드 포트 (UseCase)
│   └── out/                  # 아웃바운드 포트
│
├── dto/                       # DTO
│   ├── request/
│   └── response/
│
├── config/                    # 설정
│   ├── RedisConfig.java
│   ├── KafkaConfig.java
│   └── SchedulingConfig.java
│
├── exception/                 # 예외
└── common/                    # 공통 유틸리티
```

## 🔄 주문 플로우

### 정상 플로우
```
1. 주문 생성 (CREATED)
   ↓
2. 결제 요청 (PAYMENT_REQUESTED)
   ↓
3. 결제 완료 (PAYMENT_COMPLETED)
   ↓
4. 배송 준비 (SHIPMENT_PREPARING) - 자동
   ↓
5. 배송 중 (SHIPPED) - 30분 후 자동
   ↓
6. 배송 완료 (DELIVERED) - 2시간 후 자동
```

### 상태 전이 규칙
- `CREATED` → `PAYMENT_COMPLETED`, `PAYMENT_FAILED`, `CANCELLED`
- `PAYMENT_COMPLETED` → `SHIPMENT_PREPARING`, `CANCELLED`
- `SHIPMENT_PREPARING` → `SHIPPED`, `CANCELLED`
- `SHIPPED` → `DELIVERED`
- `DELIVERED` → (종료 상태)

## 💾 재고 관리 시스템

### 하이브리드 방식의 장점
1. **빠른 응답**: Redis를 통한 밀리초 단위 재고 확인
2. **데이터 안정성**: DB에 실제 재고 영구 저장
3. **동시성 제어**: Redis 원자적 연산으로 동시 주문 처리
4. **자동 복구**: 장애 시 DB에서 Redis로 자동 동기화

### 재고 처리 흐름
```
주문 생성 시:
Redis 재고 확인 → Redis 재고 예약 → 주문 생성

결제 완료 시:
DB 재고 차감 → Redis 예약 정리 → 완료

주문 취소 시:
Redis 재고 복원 → 예약 취소 → 완료
```

## 🚢 배포

### AWS EC2 배포 가이드

#### 1. EC2 인스턴스 준비
```bash
# 권장 사양: t3.large (2 vCPU, 8GB RAM)
# OS: Amazon Linux 2 또는 Ubuntu 20.04
```

#### 2. 필수 소프트웨어 설치
```bash
# Java 17 설치
sudo yum install java-17-amazon-corretto -y

# Docker 설치
sudo yum install docker -y
sudo service docker start
sudo usermod -a -G docker ec2-user

# Docker Compose 설치
sudo curl -L https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m) -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose
```

#### 3. 애플리케이션 배포
```bash
# 프로젝트 클론
git clone https://github.com/hkjs96/ordersystem.git
cd ordersystem

# 인프라 실행
docker-compose up -d

# JAR 빌드
./gradlew clean build

# 백그라운드 실행
nohup java -jar build/libs/ordersystem-0.0.1-SNAPSHOT.jar > app.log 2>&1 &
```

#### 4. 방화벽 설정
```bash
# 보안 그룹에서 다음 포트 오픈
# - 8080: Spring Boot
# - 6379: Redis (내부용)
# - 9092: Kafka (내부용)
```

### Docker 이미지 빌드 (선택사항)
```dockerfile
FROM openjdk:17-jdk-slim
COPY build/libs/ordersystem-0.0.1-SNAPSHOT.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

## 📊 모니터링

### 헬스체크
```bash
# 애플리케이션 상태 확인
curl http://localhost:8080/actuator/health

# Redis 연결 확인
redis-cli ping

# Kafka 토픽 확인
docker exec -it kafka kafka-topics --list --bootstrap-server localhost:9092
```

### 로그 확인
```bash
# 애플리케이션 로그
tail -f app.log

# Docker 컨테이너 로그
docker-compose logs -f
```

### 주요 모니터링 지표
- **재고 동기화 상태**: `/api/inventory/{productId}/status`
- **배송 통계**: 1시간마다 자동 로깅
- **에러 발생**: GlobalExceptionHandler에서 추적

## 🧪 테스트

### 단위 테스트 실행
```bash
./gradlew test
```

### 통합 테스트 시나리오
1. 주문 생성 → 결제 → 배송 완료 (Happy Path)
2. 주문 생성 → 결제 실패 → 재고 복원
3. 주문 생성 → 주문 취소 → 재고 복원
4. 동시 주문 처리 (부하 테스트)

## 📄 라이센스

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 👨‍💻 개발자

- **GitHub**: [@hkjs96](https://github.com/hkjs96)

