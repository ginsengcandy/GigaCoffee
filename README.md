## API 목록
| 라벨     | API명 | 메서드 | Endpoint |
|--------|---|---|---|
| Must   | 커피 메뉴 목록 조회 | GET | /api/menus |
| Must   | 포인트 충전 | POST | /api/points/charge |
| Must   | 커피 주문(장바구니 생성) | POST | /api/orders |
| Must   | 포인트 결제(장바구니 결제) | POST | /api/orders/{orderId}/payment |
| Must   | 인기 메뉴 목록 조회 | GET | /api/menus/popular |
| Should | 최근 주문 내역 조회 (5개) | GET | /api/orders/recent |
| Should | 주문 취소(장바구니 삭제) | DELETE | /api/orders/{orderId} |
| Should | 회원 탈퇴 | DELETE | /api/users/me |
| Could  | 내 포인트 조회 | GET | /api/points |
| Could  | 커피 메뉴 등록 | POST | /api/admin/menus |
| Could  | 커피 메뉴 삭제 | DELETE | /api/admin/menus/{menuId} |
| Could  | 커피 메뉴 수정 | PUT | /api/admin/menus/{menuId} |

## Kafka 브로커 구성 결정

### 현재 구성: 단일 브로커

로컬 개발 환경에서는 브로커 1개로 구성했습니다.
브로커 1개로도 토픽 생성, 메시지 발행/소비, 컨슈머 그룹 동작을 모두 검증할 수 있으며,
다중 브로커 구성은 로컬 환경에서 불필요한 리소스 소비로 이어집니다.

### 브로커 확장 기준

아래 조건 중 하나라도 해당되면 브로커 증설을 검토합니다.

| 조건 | 설명 |
|---|---|
| 가용성 요구 | 브로커 장애 시 서비스 중단 없이 유지해야 하는 경우 |
| 처리량 한계 | 단일 브로커의 디스크 I/O 또는 네트워크 대역폭이 병목인 경우 |
| 복제 계수 상향 | replication factor를 2 이상으로 올려야 하는 경우 (브로커 수 >= replication factor) |

### 프로덕션 전환 시 고려사항

- 브로커 최소 3개 구성 (1개 장애 시 나머지 2개로 서비스 유지)
- `KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 3` 으로 상향
- 브로커마다 고유한 `KAFKA_NODE_ID` 와 `KAFKA_ADVERTISED_LISTENERS` 포트 설정 필요
- `KAFKA_CONTROLLER_QUORUM_VOTERS` 에 모든 컨트롤러 노드 등록 필요

## Dockerfile — 멀티스테이지 빌드

### 구성

빌드 스테이지와 실행 스테이지를 분리한 멀티스테이지 빌드를 사용합니다.

| 스테이지 | 베이스 이미지 | 역할 |
|---|---|---|
| builder | eclipse-temurin:21-jdk-jammy | Gradle 빌드 및 JAR 생성 |
| run | eclipse-temurin:21-jre-jammy | JAR 실행만 담당 |

### 결정 근거

JDK는 컴파일 시에만 필요하고 런타임에는 필요하지 않습니다.
최종 이미지에 JRE만 포함함으로써 이미지 크기를 절반 이하로 줄일 수 있습니다.

### 레이어 캐시 전략

의존성 다운로드를 소스 코드 복사보다 먼저 실행합니다.
```dockerfile
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .
RUN ./gradlew dependencies --no-daemon  # 의존성 레이어 캐시

COPY src src                            # 소스 변경 시 이 레이어부터 재실행
RUN ./gradlew bootJar --no-daemon
```

`build.gradle`이 변경되지 않으면 의존성 다운로드 레이어는 캐시를 재사용합니다.
소스 코드만 변경된 경우 불필요한 의존성 재다운로드 없이 빌드 시간을 단축할 수 있습니다.

## Dockerfile — 플랫폼 고정

### 배경

로컬 개발 환경(Apple Silicon, ARM64)과 GitHub Actions CI 환경(amd64) 간의
아키텍처 불일치로 인한 빌드 오류를 방지하기 위해 플랫폼을 명시적으로 고정합니다.

### 적용
```dockerfile
FROM --platform=linux/amd64 eclipse-temurin:21-jdk-jammy AS builder
FROM --platform=linux/amd64 eclipse-temurin:21-jre-jammy
```

`eclipse-temurin`은 멀티 아키텍처 이미지를 지원하므로 자동 선택도 가능하지만,
CI 환경과 동일한 플랫폼을 명시함으로써 로컬과 CI 간의 빌드 결과 일관성을 보장합니다.

## application.yml — 환경변수 주입 패턴

### 설계 원칙

모든 설정값은 환경변수로 주입받도록 구성합니다.
민감 정보는 코드베이스에 포함되지 않으며, 환경에 따라 값을 교체할 수 있습니다.

### 패턴
```yaml
${변수명:기본값}
```

환경변수가 존재하면 해당 값을 사용하고, 없으면 기본값으로 폴백합니다.
로컬 개발 환경에서는 기본값으로 동작하고, 도커 및 프로덕션 환경에서는 환경변수를 주입받습니다.

### 적용 예시

| 설정 | 환경변수 | 기본값 | 비고 |
|---|---|---|---|
| DB 호스트 | DB_HOST | localhost | |
| DB 포트 | DB_PORT | 3306 | |
| Redis 호스트 | REDIS_HOST | localhost | |
| JWT 만료 시간 | JWT_EXPIRATION | 86400000 (24시간) | |
| DB 비밀번호 | DB_PASSWORD | 없음 | 반드시 환경변수로 주입 |
| JWT 시크릿 | JWT_SECRET | 없음 | 반드시 환경변수로 주입 |

DB_PASSWORD와 JWT_SECRET은 기본값을 의도적으로 지정하지 않았습니다.
값이 없으면 앱 기동이 실패하도록 강제함으로써 민감 정보가 누락된 채로 실행되는 상황을 방지합니다.

## Git 브랜칭 전략

### 브랜치 구성

| 브랜치 | 역할 |
|---|---|
| main | 안정된 코드 유지. 직접 push 금지 |
| develop | 기능 통합 브랜치 |
| feature/* | 기능 단위 개발 |

### PR 흐름

```
feature/* → develop (PR + Squash merge)
develop   → main    (PR, 기능 완성 시)
```

### 네이밍 규칙

```
feature/menu-api
feature/order-api
feature/point-payment
feature/point-charge
feature/kafka-event
feature/redis-cache
```

### 전략 선정 이유

Git Flow의 전체 구조(main, develop, feature, release, hotfix)는 대형 팀 프로젝트에 적합하다.
그러나 배포 단계가 없는 1인 프로젝트에서 release와 hotfix 브랜치까지 운영하는 것은 불필요한 오버헤드다.
GitHub Flow는 구조가 단순하지만 개발 중인 코드와 안정된 코드가 분리되지 않아 관리가 어렵다.

따라서 Git Flow에서 release와 hotfix를 제거한 단순화된 구조를 채택했다.
main과 develop을 분리해 코드 안정성을 유지하면서, feature 브랜치 단위로 기능을 개발하고
Squash merge로 develop에 통합해 커밋 히스토리를 간결하게 유지한다.

## 커밋 컨벤션

### 커밋 타입

| 타입 | 설명 |
|---|---|
| feat | 새로운 기능 추가 |
| fix | 버그 수정 |
| refactor | 기능 변경 없는 코드 개선 |
| test | 테스트 코드 추가 및 수정 |
| docs | 문서 수정 |
| chore | 빌드, 설정 파일 수정 |
| style | 코드 포맷, 공백 등 스타일 수정 |

### 커밋 메시지 구조
타입: 제목 (50자 이내)
본문 (선택)

무엇을 왜 했는지 설명
어떻게는 코드로 확인 가능하므로 생략 가능


### 규칙

- 타입은 영어, 제목은 한국어로 작성한다
- 제목은 마침표 없이 50자 이내로 작성한다
- 하나의 커밋에는 하나의 변경사항만 담는다
- 본문이 필요한 경우 무엇을 왜 했는지 위주로 작성한다

### 예시
feat: 커피 메뉴 목록 조회 API 구현
feat: 포인트 결제 API 구현
분산 락(Redisson)으로 동시 결제 요청 방지
트랜잭션 커밋 후 Kafka 이벤트 발행
fix: 포인트 잔액 음수 허용 오류 수정
chore: docker-compose MySQL 유저 추가

## 정적 팩토리 메서드 네이밍 규칙

### 규칙

| 메서드 | 의미 | 사용 시점 |
|---|---|---|
| create | 새로운 객체 생성 | 도메인 엔티티를 새로 만들 때 |
| from | 단일 객체 변환 | 다른 객체 하나를 받아서 변환할 때 |
| of | 여러 값 조합 | 여러 파라미터를 받아서 생성할 때 |

### 적용 예시

엔티티 생성은 create로 통일한다.
```java
Order.create(userId, totalPrice)
OrderMenu.create(order, menu, quantity)
UserPoint.create(userId)
PointCharge.create(userId, amount, type)
PointPayment.create(userId, orderId, amount)
Menu.create(name, price)
```

단일 객체를 DTO로 변환할 때는 from을 사용한다.
```java
MenuResponse.from(menu)
OrderResponse.from(order)
OrderMenuResponse.from(orderMenu)
```

여러 파라미터를 조합해서 DTO를 만들 때는 of를 사용한다.
```java
PointPaymentResponse.of(userPoint, paymentAmount)
```

### 근거

메서드 이름만 봐도 객체가 어떤 방식으로 만들어지는지 의도를 파악할 수 있다.
create, from, of는 Java 표준 라이브러리와 실무에서 널리 쓰이는 관례를 따른 것으로,
팀원이 코드를 읽을 때 별도 설명 없이도 생성 방식을 이해할 수 있다.

## Kafka 이벤트 발행 결과 처리 전략

### 구현
```java
kafkaTemplate.send("payment.confirmed", event)
    .whenComplete((result, ex) -> {
        if (ex != null) {
            log.error("[Kafka] 결제 이벤트 발행 실패 - userId: {}, menuIds: {}",
                    event.getUserId(), event.getMenuIds(), ex);
        } else {
            log.info("[Kafka] 결제 이벤트 발행 성공 - userId: {}, menuIds: {}",
                    event.getUserId(), event.getMenuIds());
        }
    });
```

### 이유

`kafkaTemplate.send()`는 비동기로 동작하며 `CompletableFuture`를 반환한다.
콜백 없이 `send()`만 호출하면 발행 성공 여부를 알 수 없고,
브로커 연결 실패나 토픽 부재 같은 상황에서 이벤트가 유실돼도 감지할 수 없다.

`.whenComplete()`로 콜백을 등록하면 발행 실패 시 로그를 남겨 추적할 수 있고,
운영 환경에서 알림을 보내는 기반으로 활용할 수 있다.

### 비동기로 처리하는 이유

발행 결과를 동기로 기다리면 Kafka 브로커 응답 시간만큼 API 응답이 지연된다.
결제 API에서 Kafka 응답을 기다리는 것은 불필요한 지연이므로,
발행은 비동기로 하되 결과는 콜백으로 처리한다.

## 기술적 판단: Orders - Users 간 JPA 연관관계 미적용

### 결정

`Orders` 엔티티에서 `User` 엔티티를 `@ManyToOne`으로 연관짓지 않고 `userId`를 값으로만 저장한다.

### 근거

첫째, 도메인 경계 분리를 위해서다.
`order` 도메인이 `user` 도메인에 JPA 연관관계로 묶이면 `User` 엔티티 변경 시 `Orders`에도 영향이 생긴다.
`userId`만 저장하면 두 도메인이 독립적으로 유지되고, 마이크로서비스로 확장할 때도 유리하다.

둘째, 주문 이력 보존을 위해서다.
유저가 탈퇴(soft delete)하더라도 주문 이력은 독립적으로 보존돼야 한다.
JPA 연관관계로 묶으면 `users` 테이블에 강하게 결합되어 이 요구사항을 처리하기 복잡해진다.

셋째, 대용량 트래픽 환경에서의 성능을 고려했다.
DB 레벨의 FK 제약은 쓰기 성능에 부담이 될 수 있다.
애플리케이션 레벨에서 userId 유효성을 검증하는 방식으로 대체한다.

### 트레이드오프

JPA 연관관계가 없으므로 `User` 정보가 필요한 경우 별도 조회가 필요하다.
또한 DB 레벨의 참조 무결성이 보장되지 않으므로 애플리케이션 레벨에서 유효성 검증을 철저히 해야 한다.

## 기술적 판단: Orders - OrderProduct 간 @OneToMany 적용

### 결정

`Orders` 엔티티에서 `OrderProduct`를 `@OneToMany`로 연관짓는다.
```java
@OneToMany(mappedBy = "orders", cascade = CascadeType.ALL, orphanRemoval = true)
private List<OrderProduct> orderProducts = new ArrayList<>();
```

### 근거

첫째, OrderProduct의 생명주기가 Orders에 완전히 종속된다.
Orders 없이 OrderProduct는 존재할 수 없고, Orders가 삭제되면 OrderProduct도 함께 삭제돼야 한다.
`cascade = CascadeType.ALL`과 `orphanRemoval = true`로 이 생명주기를 자동으로 관리한다.

둘째, 주문 상품은 항상 주문과 함께 조회된다.
주문 상품만 단독으로 조회하는 시나리오가 없어 @OneToMany로 묶는 것이 자연스럽다.

셋째, @OneToMany 미적용 시 관리 포인트가 증가한다.
cascade와 orphanRemoval을 직접 관리해야 하고, 도메인 응집도가 낮아진다.

### N+1 방지

@OneToMany는 N+1 문제가 발생하기 쉬우므로 fetch join으로 방지한다.
```java
@Query("SELECT o FROM Orders o JOIN FETCH o.orderProducts WHERE o.id = :id")
Optional<Orders> findByIdWithProducts(@Param("id") Long id);
```

### 트레이드오프

실무에서는 @OneToMany 남용을 지양한다.
Orders → OrderProduct는 생명주기 종속성과 항상 함께 조회되는 특성이 있어 적용을 정당화할 수 있지만,
다른 도메인 간 관계에서는 userId처럼 값으로만 참조하는 방식을 유지한다.

## 기술적 판단: menus.name unique 제약 미적용

### 결정

`menus` 테이블의 `name` 컬럼에 unique 제약을 적용하지 않는다.

### 근거

첫째, soft delete 구조에서 unique 제약이 충돌한다.
삭제된 메뉴(`deleted = true`)와 같은 이름의 메뉴를 재등록할 때 unique 제약 위반이 발생한다.
조건부 unique로 우회할 수 있으나 `deleted = true`인 행이 여러 개 생기면 동일한 문제가 반복된다.

둘째, 명시적인 요구사항이 없다.
현재 요구사항에 메뉴 이름의 고유성을 강제해야 한다는 조건이 없다.
불필요한 제약은 추후 요구사항 변경 시 오히려 장애물이 될 수 있다.

셋째, 이름 중복이 필요한 시나리오가 존재한다.
동일한 메뉴명에 옵션이나 사이즈가 추가되는 경우 이름이 겹칠 수 있어
unique 제약이 비즈니스 확장을 제한할 수 있다.

### 대안

메뉴 이름 중복 방지가 필요하다면 DB 제약 대신 애플리케이션 레벨에서 검증하는 방식을 사용한다.
이 경우 soft delete 상태를 고려한 유연한 검증이 가능하다.

## 기술적 판단: OrderMenu.quantity 타입 선택 (int)

### 결정

`OrderMenu` 엔티티의 `quantity` 컬럼을 래퍼 타입 `Integer` 대신 원시 타입 `int`로 관리한다.

### 근거

수량은 null이 허용되면 안 되는 값이다.
수량이 없는 주문 상품은 비즈니스적으로 의미가 없으므로 null을 원천 차단하는 것이 맞다.

`int`는 기본적으로 null을 허용하지 않아 `@Column(nullable = false)`의 의도와 일치한다.
반면 `Integer`를 사용하면 null 허용 가능성이 생기고, `@NotNull` 검증을 별도로 추가해야 한다.

### null 안전성 보완

`int`의 기본값은 0이므로 값이 누락됐을 때 0으로 저장될 위험이 있다.
DTO에 `@Positive` 검증을 적용해 0 이하의 값을 차단한다.

```java
@Positive(message = "수량은 0보다 커야 합니다.")
private int quantity;
```

## 기술적 판단: 결제 이벤트 처리에 Kafka 도입

### 결정

결제 확정 이벤트 처리에 Spring 내부 이벤트 대신 Kafka를 사용한다.

### 요구사항

다중 서버 환경에서 다수의 인스턴스로 동작하더라도 기능에 문제가 없어야 한다.

### Spring 내부 이벤트의 한계

Spring 내부 이벤트는 같은 JVM 안에서만 동작한다.
다중 서버 환경에서는 이벤트를 발행한 인스턴스에서만 처리가 일어나고,
나머지 인스턴스는 이벤트를 받지 못한다.
서버 A에서 결제 완료
→ 서버 A의 내부 이벤트 발행
→ 서버 A의 리스너만 처리
→ 서버 B, C는 이벤트를 받지 못함

인기 메뉴 랭킹 업데이트나 데이터 수집 플랫폼 전송이
일부 인스턴스에서만 동작하는 상황이 발생할 수 있다.

### Kafka를 선택한 근거

첫째, 다중 서버 환경에서 이벤트 처리를 보장한다.
Kafka 브로커가 이벤트를 중앙에서 관리하기 때문에
어느 인스턴스에서 이벤트를 발행해도 Consumer Group이 항상 처리할 수 있다.
서버 A, B, C 중 어느 서버에서 결제가 완료되든
→ Kafka 브로커에 이벤트 발행
→ Consumer Group이 브로커에서 이벤트를 가져가 처리
→ 어느 인스턴스에서 발행했는지 무관하게 처리 보장

둘째, 이벤트 유실을 방지한다.
이벤트가 브로커에 저장되기 때문에 앱이 죽어도 유실되지 않고,
Consumer가 재기동 후 다시 처리할 수 있다.

셋째, 팬아웃 구조를 자연스럽게 지원한다.
결제 확정 이벤트 하나에 인기 메뉴 랭킹 업데이트, 데이터 수집 플랫폼 전송 두 가지 처리가 필요한데,
각각을 독립적인 Consumer Group으로 분리해 처리할 수 있다.

넷째, 수평 확장이 용이하다.
처리량이 늘어날 때 Consumer만 수평 확장하면 되는 구조다.

### 트레이드오프

별도 인프라(Kafka 브로커)가 필요하고 운영 복잡도가 올라간다.
이벤트 처리 실패 시 재처리 전략을 직접 구현해야 한다.

### 이벤트 발행 시점

트랜잭션 커밋 이후에 이벤트를 발행한다.
트랜잭션 안에서 발행하면 DB 롤백 시 이벤트는 이미 나간 상태가 되어 데이터 불일치가 발생한다.
TransactionSynchronizationManager의 afterCommit() 콜백을 사용해 커밋 완료 후에만 발행되도록 보장한다.

## 기술적 판단: 테스트 환경 Redis 처리 전략

### 결정

테스트 환경에서 Redis 빈을 `@MockitoBean`으로 대체한다.

### 배경

`MenuRankingService`와 `MenuRankingConsumer`가 `StringRedisTemplate`을 주입받고,
`PointService`가 `RedissonClient`를 주입받는다.
테스트 `application.yaml`에서 Redis 관련 자동 구성을 exclude하면 두 빈이 컨텍스트에 등록되지 않아
`contextLoads()`가 실패한다.

### 두 가지 대안 비교

| 방식 | application-test.yml에 Redis 설정 추가 | @MockitoBean으로 대체 |
|---|---|---|
| 인프라 의존 | 실제 Redis 또는 Testcontainers 필요 | 없음 |
| CI 영향 | 별도 컨테이너 설정 필요 | 없음 |
| 테스트 목적 부합 | contextLoads()는 빈 구성 확인용, Redis 동작 검증 불필요 | 충분 |
| 기존 패턴 일관성 | RedissonClient는 이미 Mock으로 처리 중 | 동일 방식으로 통일 |

### 적용

```java
@MockitoBean
RedissonClient redissonClient;

@MockitoBean
StringRedisTemplate stringRedisTemplate;
```

`contextLoads()`는 Redis 동작을 검증하는 테스트가 아니므로 실제 빈이 불필요하다.
Redis 실제 동작이 필요한 테스트는 별도로 분리해 작성한다.

## 기술적 판단: PointService 단위 테스트 설계

### 결정

Repository와 RedissonClient를 Mockito로 모킹하고, `@ExtendWith(MockitoExtension.class)` 기반의 단위 테스트로 작성한다.

### TransactionSynchronizationManager 정적 메서드 처리

`makePayment()`의 성공 경로에는 `TransactionSynchronizationManager.registerSynchronization()`이 포함된다.
이 메서드는 활성 트랜잭션이 없으면 `IllegalStateException`을 던지는데,
`@ExtendWith(MockitoExtension.class)` 환경에서는 실제 트랜잭션이 존재하지 않는다.

Mockito의 `mockStatic(TransactionSynchronizationManager.class)`로 정적 호출을 모킹해서 해결한다.
Kafka 이벤트 발행 테스트에서는 `ArgumentCaptor`로 등록된 `TransactionSynchronization`을 캡처한 후
`afterCommit()`을 직접 호출해 커밋 이후 이벤트 발행 여부를 검증한다.

```java
ArgumentCaptor<TransactionSynchronization> syncCaptor =
        ArgumentCaptor.forClass(TransactionSynchronization.class);

try (MockedStatic<TransactionSynchronizationManager> txManager =
             mockStatic(TransactionSynchronizationManager.class)) {
    pointService.makePayment(USER_ID, ORDER_ID);

    txManager.verify(() ->
            TransactionSynchronizationManager.registerSynchronization(syncCaptor.capture()));

    syncCaptor.getValue().afterCommit(); // 커밋 후 이벤트 발행 트리거
    verify(paymentEventProducer).sendPaymentConfirmed(any());
}
```

### 동시성 테스트 전략

동시성 케이스는 성격에 따라 모킹 방식을 다르게 적용한다.

| 시나리오 | 전략 | 기대 결과 |
|---|---|---|
| 락 경쟁 | `AtomicBoolean.compareAndSet`으로 첫 번째 `tryLock`만 `true` 반환 | 1성공 + LOCK_ACQUISITION_FAILED |
| 잔액 소진 | `Semaphore(1)`로 스레드가 순차적으로 임계 구역 진입 | 1성공 + INSUFFICIENT_POINT |

잔액 소진 테스트에서 `Semaphore`를 쓰는 이유:
두 스레드가 동시에 `UserPoint.deduct()`에 진입하면 비원자적 연산으로 인해 둘 다 잔액 체크를 통과할 수 있다.
`Semaphore(1)`을 `tryLock` 응답에 연결하면 실제 분산락처럼 순차 진입을 보장해 테스트를 결정적으로 만든다.

```java
Semaphore mutex = new Semaphore(1);
given(rLock.tryLock(anyLong(), anyLong(), any()))
        .willAnswer(inv -> mutex.tryAcquire(3, TimeUnit.SECONDS));
doAnswer(inv -> { mutex.release(); return null; }).when(rLock).unlock();
```

### lenient() 스텁 사용 이유

동시성 테스트에서는 어느 스레드가 먼저 락을 획득하는지 비결정적이다.
락 획득에 실패한 스레드는 즉시 예외를 던지고 이후 스텁(`orderRepository`, `userPointRepository` 등)을 호출하지 않는다.
`MockitoExtension`의 strict stubbing은 이를 미사용 스텁으로 간주해 `UnnecessaryStubbingException`을 던진다.

`lenient()`로 선언하면 호출 여부와 무관하게 스텁을 유지할 수 있어 비결정적 실행 순서에서도 테스트가 안정적으로 동작한다.

## 기술적 판단: MenuRankingServiceTest — @BeforeEach 스텁과 UnnecessaryStubbingException

### 문제

`@BeforeEach`에 `opsForZSet()` 스텁을 등록했으나, Redis에 데이터가 없어 즉시 반환하는 테스트에서 `opsForZSet()`이 한 번도 호출되지 않아 `UnnecessaryStubbingException`이 발생했다.

```java
// getPopularMenus() 내부
if (existingKeys.isEmpty()) {
    return Collections.emptyList(); // opsForZSet()이 호출되기 전에 반환
}
```

`@ExtendWith(MockitoExtension.class)`의 strict stubbing은 등록된 스텁이 한 번도 사용되지 않으면 예외를 던진다.

### 두 가지 해결 방법 비교

| | 각 테스트에 직접 추가 | @BeforeEach에서 lenient() 사용 |
|---|---|---|
| 코드량 | 예외 테스트 1개를 제외한 모든 테스트에 반복 추가 | @BeforeEach 1줄로 끝남 |
| 명시성 | 각 테스트가 자기완결적 | 암묵적 |
| strict stubbing 유지 | 완전히 유지 | 해당 스텁에 한해 완화 |

### 결정

`@BeforeEach`에서 `lenient()`로 선언한다.

```java
@BeforeEach
void setUp() {
    lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
}
```

### 이유

`opsForZSet()`은 "데이터 없음" 케이스를 제외한 모든 테스트에서 사용된다.
12개 테스트에 동일한 한 줄을 반복 추가하는 것은 잡음만 늘릴 뿐이다.

`lenient()`는 strict stubbing을 전면 해제하지 않는다.
해당 스텁 하나에만 적용되므로, 나머지 스텁에 대한 `UnnecessaryStubbingException` 감지는 그대로 유지된다.
이미 `PointServiceTest`에서도 동시성 테스트의 비결정적 스텁에 동일한 이유로 `lenient()`를 사용하고 있어 패턴이 일관된다.