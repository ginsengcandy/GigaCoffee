-- 테스트용 유저 등록
-- 비밀번호: password (BCrypt, cost=10)
INSERT INTO users (email, password, name, role, deleted, created_at, updated_at)
VALUES
    ('user@test.com',  '$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36WQoeG6Lruj3vjPGga31lW', '일반유저', 'USER',  false, NOW(), NOW()),
    ('admin@test.com', '$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36WQoeG6Lruj3vjPGga31lW', '관리자',   'ADMIN', false, NOW(), NOW()),
    ('julia@email.com', '$2a$10$yTYEksECkIGAyy84Lm39YO962YVMcZ6uMhjhK5JXchlcKcUEQG3ke', 'Julia', 'USER', false, NOW(), NOW()),
    ('paul@email.com', '$2a$10$yTYEksECkIGAyy84Lm39YO962YVMcZ6uMhjhK5JXchlcKcUEQG3ke', 'Paul', 'ADMIN', false, NOW(), NOW());

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