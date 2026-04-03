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