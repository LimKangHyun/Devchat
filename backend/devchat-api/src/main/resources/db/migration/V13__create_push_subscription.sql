-- 웹 푸시(PWA 알림) 구독. 기기·브라우저마다 하나씩 생긴다.
-- endpoint는 브라우저 회사 푸시 서버의 주소라 기기를 구분하는 키로 쓴다.
CREATE TABLE IF NOT EXISTS `push_subscription` (
    `id`         bigint        NOT NULL AUTO_INCREMENT,
    `member_id`  bigint        NOT NULL,
    `endpoint`   varchar(1000) NOT NULL,
    `endpoint_hash` char(64)   NOT NULL,
    `p256dh`     varchar(255)  NOT NULL,
    `auth`       varchar(255)  NOT NULL,
    `created_at` datetime(6)   NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_push_subscription_endpoint_hash` (`endpoint_hash`),
    KEY `idx_push_subscription_member` (`member_id`),
    CONSTRAINT `fk_push_subscription_member` FOREIGN KEY (`member_id`) REFERENCES `member` (`member_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
