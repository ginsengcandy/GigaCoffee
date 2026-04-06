/**
 * 메뉴 캐시 부하 테스트
 *
 * 검증 목표
 *  1. Redis 캐시 히트 시 p95 < 50ms 달성 여부
 *  2. 다수 VU가 동시에 캐시 미스를 일으킬 때 DB thundering herd 발생 여부
 *  3. GET /api/menus (Redis String), GET /api/menus/popular (Redis Sorted Set) 각각 측정
 *
 * 실행 방법 (캐시 미스 구간을 관찰하려면 먼저 캐시를 비워야 함)
 *  $ docker exec -it <redis-container> redis-cli DEL menus:all
 *  $ docker run --rm -v $(pwd)/k6:/k6 grafana/k6 run /k6/menu-cache-test.js
 *
 * 부하 단계
 *  Phase 1 [0 ~ 5s]    0 → 100 VU  : 빠른 진입 → 캐시 미스 thundering herd 유발
 *  Phase 2 [5 ~ 95s]   100 VU 유지 : 캐시 히트 구간 (p95 목표 측정)
 *  Phase 3 [95 ~ 105s] 100 → 0 VU  : cool-down
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Counter, Rate } from 'k6/metrics';

// ── 커스텀 메트릭 ──────────────────────────────────────────────────────────────
// 엔드포인트별로 분리해서 캐시 히트/미스 응답시간 분포를 각각 확인
const menuLatency    = new Trend('menu_list_latency_ms',    true); // true = ms 단위
const popularLatency = new Trend('menu_popular_latency_ms', true);
const menuErrors     = new Counter('menu_errors_total');
const menuErrorRate  = new Rate('menu_error_rate');

const BASE_URL = 'http://host.docker.internal:8080';

// ── 옵션 ──────────────────────────────────────────────────────────────────────
export const options = {
    stages: [
        { target: 100, duration: '5s'  }, // Phase 1: thundering herd 유발
        { target: 100, duration: '90s' }, // Phase 2: 캐시 히트 정상 부하
        { target: 0,   duration: '10s' }, // Phase 3: cool-down
    ],
    thresholds: {
        // 캐시 히트 구간(Phase 2) 기준 목표치
        // Phase 1의 캐시 미스 응답이 섞여도 전체 p95가 200ms 이내여야 함
        'menu_list_latency_ms':    ['p(95)<200', 'p(99)<500'],
        'menu_popular_latency_ms': ['p(95)<200', 'p(99)<500'],

        // Phase 2 진입 후 캐시 히트만 보고 싶다면 결과 summary에서
        // 초반 5초를 제외한 구간의 평균을 직접 확인할 것
        'http_req_failed':  ['rate<0.01'], // 전체 에러율 1% 미만
        'menu_error_rate':  ['rate<0.01'],
    },
};

// ── 메인 시나리오 ──────────────────────────────────────────────────────────────
export default function () {

    // ── 1. 메뉴 목록 조회 ─────────────────────────────────────────────────────
    // 캐시 키: menus:all (TTL 1일)
    // 첫 번째 미스 시 DB 조회 후 캐시 저장, 이후 요청은 Redis에서 반환
    const menuRes = http.get(`${BASE_URL}/api/menus`, {
        tags: { endpoint: 'menu_list' },
    });

    menuLatency.add(menuRes.timings.duration);

    const menuOk = check(menuRes, {
        '[menus] status 200':      (r) => r.status === 200,
        '[menus] success true':    (r) => r.json('success') === true,
        '[menus] data is array':   (r) => Array.isArray(r.json('data')),
    });

    if (!menuOk) {
        menuErrors.add(1);
        menuErrorRate.add(1);
    } else {
        menuErrorRate.add(0);
    }

    sleep(0.1);

    // ── 2. 인기 메뉴 조회 ─────────────────────────────────────────────────────
    // Kafka 이벤트 기반으로 Redis Sorted Set에 메뉴 인기도가 쌓임
    // 결제 이력이 없으면 빈 배열 반환 (에러 아님)
    const popularRes = http.get(`${BASE_URL}/api/menus/popular`, {
        tags: { endpoint: 'menu_popular' },
    });

    popularLatency.add(popularRes.timings.duration);

    check(popularRes, {
        '[popular] status 200':    (r) => r.status === 200,
        '[popular] success true':  (r) => r.json('success') === true,
        '[popular] data is array': (r) => Array.isArray(r.json('data')),
    });

    sleep(0.3);
}
