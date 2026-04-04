-- 메뉴 등록
INSERT INTO menus (name, price, deleted, created_at, updated_at)
VALUES
    ('아메리카노', 4500, false, NOW(), NOW()),
    ('카페라떼', 5000, false, NOW(), NOW()),
    ('카푸치노', 5500, false, NOW(), NOW()),
    ('에스프레소', 4000, false, NOW(), NOW()),
    ('카라멜마키아또', 6000, false, NOW(), NOW());

-- 테스트용 유저 포인트 등록(userId = 1)
INSERT INTO user_points (user_id, point_balance, version, created_at, updated_at)
VALUES (1, 100000, 0, NOW(), NOW());