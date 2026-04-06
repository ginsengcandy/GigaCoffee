/**
 * 주문 전체 플로우 처리량 & 병목 검증 테스트
 *
 * 검증 목표
 *  1. 전체 플로우(메뉴 조회 → 주문 생성 → 결제 → 최근 주문 조회) 처리량(RPS) 측정
 *  2. 각 단계별 p95 레이턴시로 병목 구간 식별
 *  3. 80 VU 지속 부하 하에서 에러율 유지 여부 검증
 *
 * 부하 단계
 *  Phase 1 [0~1m]  :  0 → 80 VU  (워밍업 — JVM JIT, DB 커넥션 풀 안정화)
 *  Phase 2 [1~5m]  : 80 VU 유지  (정상 지속 부하 — 처리량·병목 측정 핵심 구간)
 *  Phase 3 [5~7m]  : 80 VU 유지  (내구성 확인 — GC 압박·커넥션 고갈 여부)
 *  Phase 4 [7~8m]  : 80 → 0 VU  (cool-down)
 *
 * 실행 방법
 *  docker run --rm -v "$(pwd)/k6:/k6" grafana/k6 run /k6/order-flow-test.js
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Counter, Rate } from 'k6/metrics';

const BASE_URL    = 'http://host.docker.internal:8080';
const JSON_HEADERS = { 'Content-Type': 'application/json' };

// ── 커스텀 메트릭 (단계별 레이턴시 → 어느 단계가 병목인지 바로 식별) ─────────────
const menuLatency    = new Trend('flow_menu_latency_ms',    true);
const orderLatency   = new Trend('flow_order_latency_ms',   true);
const paymentLatency = new Trend('flow_payment_latency_ms', true);
const recentLatency  = new Trend('flow_recent_latency_ms',  true);

const flowSuccess   = new Counter('flow_success_total');  // 전 단계 정상 완료 수
const flowFailed    = new Counter('flow_failed_total');   // 중간 실패로 플로우 중단 수
const flowErrorRate = new Rate('flow_error_rate');

// ── 옵션 ──────────────────────────────────────────────────────────────────────
export const options = {
    stages: [
        { target: 80, duration: '1m' }, // Phase 1: 워밍업
        { target: 80, duration: '4m' }, // Phase 2: 정상 지속 부하
        { target: 80, duration: '2m' }, // Phase 3: 내구성 검증
        { target: 0,  duration: '1m' }, // Phase 4: cool-down
    ],
    thresholds: {
        // 단계별 p95 — 수치를 초과하는 항목이 병목 구간
        // 캐시 히트 기대 (Redis String, TTL 1일)
        'flow_menu_latency_ms':    ['p(95)<100'],
        // DB write (주문 + 주문 메뉴 insert)
        'flow_order_latency_ms':   ['p(95)<500'],
        // Redisson 락 획득 + 포인트 차감 + Kafka publish
        // VU마다 orderId가 다르므로 락 경합 없음 → DB/Kafka 순수 지연 측정
        'flow_payment_latency_ms': ['p(95)<1000'],
        // DB read (최근 주문 목록)
        'flow_recent_latency_ms':  ['p(95)<300'],

        'http_req_failed': ['rate<0.01'],  // HTTP 레벨 에러 1% 미만
        'flow_error_rate': ['rate<0.05'],  // 플로우 단절 5% 미만
    },
};

// ── Setup: VU당 계정 미리 생성 (80개) ───────────────────────────────────────────
// default()가 시작되기 전 1회만 실행되므로 토큰 재발급 없이 반복 재사용 가능
export function setup() {
    const users = [];

    for (let i = 0; i < 80; i++) {
        const email = `flow-vu${i}-${Date.now()}@test.com`;
        const pw    = 'Test1234!';

        // 1. 회원가입
        const signupRes = http.post(
            `${BASE_URL}/api/users/signup`,
            JSON.stringify({ email, password: pw, name: `플로우VU${i}` }),
            { headers: JSON_HEADERS }
        );
        if (signupRes.status !== 200) {
            console.error(`[Setup] VU${i} 회원가입 실패: ${signupRes.status} — ${signupRes.body}`);
            continue;
        }

        // 2. 로그인 → accessToken 발급
        const loginRes = http.post(
            `${BASE_URL}/api/users/login`,
            JSON.stringify({ email, password: pw }),
            { headers: JSON_HEADERS }
        );
        const token = loginRes.json('data.accessToken');
        if (!token) {
            console.error(`[Setup] VU${i} 로그인 실패: ${loginRes.status}`);
            continue;
        }

        // 3. 포인트 초기 충전 (반복 주문을 충당할 여유분)
        //    실측 VU당 반복 횟수 ≈ 289회, 메뉴 최대 단가 6,000원
        //    289 * 6,000 = 1,734,000 → 3,000,000으로 2배 여유 확보
        const chargeRes = http.post(
            `${BASE_URL}/api/points/charge`,
            JSON.stringify({ amount: 3_000_000 }),
            { headers: { ...JSON_HEADERS, 'Authorization': `Bearer ${token}` } }
        );
        if (chargeRes.status !== 200) {
            console.warn(`[Setup] VU${i} 포인트 충전 실패: ${chargeRes.status}`);
        }

        users.push({ token });
    }

    if (users.length === 0) {
        throw new Error('[Setup] 유저 생성에 모두 실패했습니다. 서버 상태를 확인하세요.');
    }
    console.log(`[Setup] ${users.length}개 유저 계정 준비 완료`);
    return { users };
}

// ── 메인 시나리오: 전체 주문 플로우 1사이클 ─────────────────────────────────────
export default function ({ users }) {
    // __VU(1-based)를 배열 인덱스로 변환 — 유저 부족 시 모듈러로 재사용
    const { token } = users[(__VU - 1) % users.length];
    const authHeaders = { ...JSON_HEADERS, 'Authorization': `Bearer ${token}` };

    // ── Step 1: 메뉴 목록 조회 ─────────────────────────────────────────────────
    const menuRes = http.get(`${BASE_URL}/api/menus`, {
        tags: { step: 'menu_list' },
    });
    menuLatency.add(menuRes.timings.duration);

    const menuOk = check(menuRes, {
        '[메뉴] status 200':    (r) => r.status === 200,
        '[메뉴] success true':  (r) => r.json('success') === true,
        '[메뉴] data is array': (r) => Array.isArray(r.json('data')),
    });
    if (!menuOk) {
        flowFailed.add(1);
        flowErrorRate.add(1);
        return;
    }

    const menus = menuRes.json('data');
    if (!menus || menus.length === 0) {
        console.warn('[메뉴] 응답 데이터 없음 — DB에 메뉴를 추가하세요.');
        flowFailed.add(1);
        flowErrorRate.add(1);
        return;
    }
    // 메뉴를 무작위로 선택해 특정 메뉴에 트래픽이 쏠리지 않도록 분산
    const menu = menus[Math.floor(Math.random() * menus.length)];

    sleep(0.2);

    // ── Step 2: 주문 생성 ──────────────────────────────────────────────────────
    const orderRes = http.post(
        `${BASE_URL}/api/orders`,
        JSON.stringify({ orderMenus: [{ menuId: menu.id, quantity: 1 }] }),
        { headers: authHeaders, tags: { step: 'order_create' } }
    );
    orderLatency.add(orderRes.timings.duration);

    const orderOk = check(orderRes, {
        '[주문] status 200':   (r) => r.status === 200,
        '[주문] orderId 존재': (r) => !!r.json('data.id'),
    });
    if (!orderOk) {
        flowFailed.add(1);
        flowErrorRate.add(1);
        console.warn(`[주문] 실패 VU=${__VU} status=${orderRes.status} body=${orderRes.body}`);
        return;
    }

    const orderId = orderRes.json('data.id');
    sleep(0.3);

    // ── Step 3: 결제 ───────────────────────────────────────────────────────────
    // 각 VU가 자신만의 orderId를 사용하므로 분산락 경합 없음
    // → payment 레이턴시 = 순수 DB write + Kafka publish 지연
    const payRes = http.post(
        `${BASE_URL}/api/orders/${orderId}/payment`,
        null,
        { headers: { 'Authorization': `Bearer ${token}` }, tags: { step: 'payment' } }
    );
    paymentLatency.add(payRes.timings.duration);

    const payOk = check(payRes, {
        '[결제] status 200': (r) => r.status === 200,
    });
    if (!payOk) {
        flowFailed.add(1);
        flowErrorRate.add(1);
        console.warn(`[결제] 실패 VU=${__VU} status=${payRes.status} body=${payRes.body}`);
        return;
    }

    sleep(0.2);

    // ── Step 4: 최근 주문 조회 ─────────────────────────────────────────────────
    const recentRes = http.get(`${BASE_URL}/api/orders/recent`, {
        headers: authHeaders,
        tags: { step: 'recent_orders' },
    });
    recentLatency.add(recentRes.timings.duration);

    check(recentRes, {
        '[최근주문] status 200':    (r) => r.status === 200,
        '[최근주문] success true':  (r) => r.json('success') === true,
        '[최근주문] data is array': (r) => Array.isArray(r.json('data')),
    });

    // 플로우 전 단계 완료
    flowSuccess.add(1);
    flowErrorRate.add(0);

    sleep(0.5);
}
