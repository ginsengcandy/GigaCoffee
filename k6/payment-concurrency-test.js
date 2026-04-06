/**
 * 결제 동시성 테스트 — 분산락(Redisson) 검증
 *
 * 검증 목표
 *  1. 동일 주문에 N개 동시 결제 시도 시 정확히 1건만 200 응답
 *  2. 나머지 N-1건은 400(ORDER_ALREADY_COMPLETED) 또는 500(LOCK_ACQUISITION_FAILED)
 *  3. payment_success_count == 1 이 아니면 분산락 버그로 판정
 *
 * 시나리오
 *  setup()   : 유저 1명 생성 → 포인트 충전 → 주문 1개 생성 → { token, orderId } 반환
 *  default() : 10 VU가 동일 orderId로 동시에 결제 시도 (각 1회)
 *
 * Redisson 락 설정 (PointService.makePayment)
 *  waitTime  = 3s  : 락 획득 대기 최대 시간
 *  leaseTime = 3s  : 락 보유 최대 시간
 *  → 락 대기 중 타임아웃 시 LOCK_ACQUISITION_FAILED(500) 반환
 *
 * 실행 방법
 *  docker run --rm -v "$(pwd)/k6:/k6" grafana/k6 run /k6/payment-concurrency-test.js
 */

import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

const BASE_URL = 'http://host.docker.internal:8080';

const JSON_HEADERS = { 'Content-Type': 'application/json' };

// ── 커스텀 메트릭 ──────────────────────────────────────────────────────────────
const paymentSuccess  = new Counter('payment_success_count');  // 200 응답 수
const paymentRejected = new Counter('payment_rejected_count'); // 400/500 응답 수

// ── 옵션 ──────────────────────────────────────────────────────────────────────
export const options = {
    scenarios: {
        concurrent_payment: {
            executor: 'shared-iterations',
            vus: 10,        // 동시 접속 VU 수
            iterations: 10, // vus == iterations → 각 VU가 정확히 1회 실행
            maxDuration: '30s',
        },
    },
    thresholds: {
        // 핵심 불변 조건: 중복 결제가 발생해서는 안 됨
        'payment_success_count':  ['count==1'], // 정확히 1건만 성공
        'payment_rejected_count': ['count==9'], // 나머지 9건은 예상된 거부
    },
};

// ── Setup: 테스트 데이터 준비 (VU 시작 전 1회 실행) ────────────────────────────
export function setup() {
    // 1. 메뉴 목록 조회 → 첫 번째 메뉴 선택
    const menusRes = http.get(`${BASE_URL}/api/menus`);
    const menus = menusRes.json('data');
    if (!menus || menus.length === 0) {
        throw new Error('사용 가능한 메뉴가 없습니다. DB에 메뉴 데이터를 먼저 추가하세요.');
    }
    const menu = menus[0];

    // 2. 테스트 유저 생성 (timestamp로 이메일 중복 방지)
    const email = `test-pay-${Date.now()}@test.com`;
    const signupRes = http.post(
        `${BASE_URL}/api/users/signup`,
        JSON.stringify({ email, password: 'Test1234!', name: '결제테스트유저' }),
        { headers: JSON_HEADERS }
    );
    if (signupRes.status !== 200) {
        throw new Error(`회원가입 실패 (${signupRes.status}): ${signupRes.body}`);
    }

    // 3. 로그인 → accessToken 발급
    const loginRes = http.post(
        `${BASE_URL}/api/users/login`,
        JSON.stringify({ email, password: 'Test1234!' }),
        { headers: JSON_HEADERS }
    );
    const token = loginRes.json('data.accessToken');
    if (!token) {
        throw new Error(`로그인 실패 (${loginRes.status}): ${loginRes.body}`);
    }
    const authHeaders = { headers: { ...JSON_HEADERS, 'Authorization': `Bearer ${token}` } };

    // 4. 포인트 충전 (주문 금액을 감당할 수 있도록 여유 있게)
    const chargeAmount = menu.price * 10;
    const chargeRes = http.post(
        `${BASE_URL}/api/points/charge`,
        JSON.stringify({ amount: chargeAmount }),
        authHeaders
    );
    if (chargeRes.status !== 200) {
        throw new Error(`포인트 충전 실패 (${chargeRes.status}): ${chargeRes.body}`);
    }

    // 5. 주문 생성 (이 주문에 10개의 VU가 동시에 결제를 시도함)
    const orderRes = http.post(
        `${BASE_URL}/api/orders`,
        JSON.stringify({ orderMenus: [{ menuId: menu.id, quantity: 1 }] }),
        authHeaders
    );
    if (orderRes.status !== 200) {
        throw new Error(`주문 생성 실패 (${orderRes.status}): ${orderRes.body}`);
    }
    const orderId = orderRes.json('data.id');

    console.log(`[Setup] menuId=${menu.id}, price=${menu.price}, orderId=${orderId}`);
    return { token, orderId };
}

// ── 메인 시나리오: 10 VU 동시 결제 시도 ─────────────────────────────────────────
export default function (data) {
    const { token, orderId } = data;

    // 결제 요청 — body 없음 (orderId는 path variable)
    const res = http.post(
        `${BASE_URL}/api/orders/${orderId}/payment`,
        null,
        { headers: { 'Authorization': `Bearer ${token}` } }
    );

    // 예상 응답 코드 검증
    check(res, {
        '[결제] 예상된 응답 코드 (200 / 400 / 500)': (r) =>
            r.status === 200 || r.status === 400 || r.status === 500,
    });

    if (res.status === 200) {
        paymentSuccess.add(1);
        console.log(`[결제 성공] VU ${__VU}: 200 OK`);
    } else {
        paymentRejected.add(1);
        const errorCode = res.json('message') || res.status;
        console.log(`[결제 거부] VU ${__VU}: ${res.status} — ${errorCode}`);
    }
}
