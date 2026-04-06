import http from 'k6/http';

export const options = {
    vus: 5, // 몇명이서 내 서비스에 들어올 것인지
    duration: '10s', // 얼마나 오랫동안 계속해서 들어올 것인지
};

// 어디에 부하를 줄 것인가
export default function () {
    http.get("http://host.docker.internal:8080/actuator/health");
}

// K6를 도커로 실행할 것임
// localhost는 k6 컨테이너를 의미
// 따라서 내 컴퓨터 (진짜 localhost)로 요청을 보내려면 host.docker.internal로 써줘야 함