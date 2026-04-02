package com.example.gigacoffee.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Common
    INVALID_INPUT(400, "잘못된 입력값입니다."),
    UNAUTHORIZED(401, "인증이 필요합니다."),
    FORBIDDEN(403, "접근 권한이 없습니다."),
    NOT_FOUND(404, "리소스를 찾을 수 없습니다."),
    INTERNAL_SERVER_ERROR(500, "서버 내부 오류가 발생했습니다."),

    // Menu
    MENU_NOT_FOUND(404, "존재하지 않는 메뉴입니다."),
    MENU_ALREADY_DELETED(400, "이미 삭제된 메뉴입니다."),

    // Order
    ORDER_NOT_FOUND(404, "존재하지 않는 주문입니다."),
    ORDER_NOT_CANCELLABLE(400, "취소할 수 없는 주문 상태입니다."),

    // Point
    INSUFFICIENT_POINT(400, "포인트 잔액이 부족합니다."),
    POINT_NOT_FOUND(404, "포인트 정보를 찾을 수 없습니다."),

    // User
    USER_NOT_FOUND(404, "존재하지 않는 사용자입니다."),
    USER_ALREADY_DELETED(400, "이미 탈퇴한 사용자입니다.");

    private final int status;
    private final String message;
}