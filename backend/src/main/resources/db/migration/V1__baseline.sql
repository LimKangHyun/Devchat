-- ============================================================
-- V1__baseline.sql
-- DevChat 초기 스키마 베이스라인
-- ============================================================

CREATE TABLE `image_file` (
                              `image_id` bigint NOT NULL AUTO_INCREMENT,
                              `store_file_name` varchar(255) NOT NULL,
                              `upload_file_name` varchar(255) NOT NULL,
                              PRIMARY KEY (`image_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `member` (
                          `member_id` bigint NOT NULL AUTO_INCREMENT,
                          `email` varchar(255) DEFAULT NULL,
                          `join_at` datetime(6) DEFAULT NULL,
                          `nickname` varchar(255) NOT NULL,
                          `password` varchar(255) DEFAULT NULL,
                          `profile_image` varchar(255) NOT NULL,
                          `provider` enum('GITHUB','LOCAL') DEFAULT NULL,
                          `recent_room_id` bigint DEFAULT NULL,
                          `username` varchar(255) NOT NULL,
                          PRIMARY KEY (`member_id`),
                          UNIQUE KEY `UKgc3jmn7c2abyo3wf6syln5t2i` (`username`),
                          UNIQUE KEY `UKmbmcqelty0fbrvxp1q58dn57t` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `auth_token` (
                              `member_id` bigint NOT NULL,
                              `created_at` datetime(6) NOT NULL,
                              `github_access_token` varchar(512) DEFAULT NULL,
                              `refresh_token` varchar(512) NOT NULL,
                              PRIMARY KEY (`member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `chat_room` (
                             `room_id` bigint NOT NULL AUTO_INCREMENT,
                             `created_at` datetime(6) DEFAULT NULL,
                             `invite_code` varchar(255) DEFAULT NULL,
                             `last_sequence` bigint NOT NULL,
                             `name` varchar(255) NOT NULL,
                             `repository_url` varchar(255) DEFAULT NULL,
                             `webhook_id` bigint DEFAULT NULL,
                             PRIMARY KEY (`room_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `chat_message` (
                                `message_id` bigint NOT NULL AUTO_INCREMENT,
                                `code_language` varchar(255) DEFAULT NULL,
                                `content` text,
                                `created_at` datetime(6) NOT NULL,
                                `status` enum('DELETED','EDITED','NO_CHANGE') DEFAULT NULL,
                                `type` enum('CODE','EVENT','GIT','IMAGE','TEXT') DEFAULT NULL,
                                `chat_image_id` bigint DEFAULT NULL,
                                `room_id` bigint DEFAULT NULL,
                                `member_id` bigint DEFAULT NULL,
                                `sequence` bigint DEFAULT NULL,
                                PRIMARY KEY (`message_id`),
                                UNIQUE KEY `UKoe97nllv21drvrvg0s856q76n` (`chat_image_id`),
                                KEY `FKynfbnbqot8mpd1tquoc2s1w5` (`member_id`),
                                KEY `idx_chat_room_messageid_desc` (`room_id`, `message_id` DESC),
                                KEY `idx_chat_message_send_at` (`created_at`),
                                CONSTRAINT `FK9f6mwuygn32hodksh3xhtr0k2` FOREIGN KEY (`chat_image_id`) REFERENCES `image_file` (`image_id`),
                                CONSTRAINT `FKfvbc4wvhk51y0qtnjrbminxfu` FOREIGN KEY (`room_id`) REFERENCES `chat_room` (`room_id`),
                                CONSTRAINT `FKynfbnbqot8mpd1tquoc2s1w5` FOREIGN KEY (`member_id`) REFERENCES `member` (`member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `chat_message_search` (
                                       `id` bigint NOT NULL,
                                       `content` text NOT NULL,
                                       `room_id` bigint NOT NULL,
                                       PRIMARY KEY (`id`),
                                       FULLTEXT KEY `idx_content_ngram` (`content`) WITH PARSER `ngram`
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `chat_participant` (
                                    `chat_participant_id` bigint NOT NULL AUTO_INCREMENT,
                                    `is_active` bit(1) NOT NULL,
                                    `is_owner` bit(1) NOT NULL,
                                    `join_at` datetime(6) DEFAULT NULL,
                                    `last_read_sequence` bigint DEFAULT NULL,
                                    `room_id` bigint DEFAULT NULL,
                                    `member_id` bigint DEFAULT NULL,
                                    PRIMARY KEY (`chat_participant_id`),
                                    KEY `FKspomx9tqgkd3ykuqkh7g14b90` (`room_id`),
                                    KEY `FK6i9rcd4ojw7ih8tvi9wfsn4sx` (`member_id`),
                                    CONSTRAINT `FK6i9rcd4ojw7ih8tvi9wfsn4sx` FOREIGN KEY (`member_id`) REFERENCES `member` (`member_id`),
                                    CONSTRAINT `FKspomx9tqgkd3ykuqkh7g14b90` FOREIGN KEY (`room_id`) REFERENCES `chat_room` (`room_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `chat_room_alarm` (
                                   `member_id` bigint NOT NULL,
                                   `room_id` bigint NOT NULL,
                                   `enabled` bit(1) NOT NULL,
                                   PRIMARY KEY (`member_id`, `room_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `chat_message_index_status` (
                                             `message_id` bigint NOT NULL,
                                             `created_at` datetime(6) DEFAULT NULL,
                                             `indexed` bit(1) NOT NULL,
                                             `room_id` bigint NOT NULL,
                                             PRIMARY KEY (`message_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `fallback_sequence_recovery` (
                                              `room_id` bigint NOT NULL,
                                              `created_at` datetime(6) DEFAULT NULL,
                                              PRIMARY KEY (`room_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `dm_room` (
                           `id` bigint NOT NULL AUTO_INCREMENT,
                           `created_at` datetime(6) NOT NULL,
                           `member1_id` bigint NOT NULL,
                           `member2_id` bigint NOT NULL,
                           PRIMARY KEY (`id`),
                           UNIQUE KEY `uq_dm_users` (`member1_id`, `member2_id`),
                           KEY `FKocluwly6meitce3sjyopi7wbu` (`member2_id`),
                           CONSTRAINT `FKbj59tdawo7ddy4e9poqg0864a` FOREIGN KEY (`member1_id`) REFERENCES `member` (`member_id`),
                           CONSTRAINT `FKocluwly6meitce3sjyopi7wbu` FOREIGN KEY (`member2_id`) REFERENCES `member` (`member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `dm_message` (
                              `id` bigint NOT NULL AUTO_INCREMENT,
                              `content` text NOT NULL,
                              `sent_at` datetime(6) NOT NULL,
                              `type` enum('IMAGE','TEXT') DEFAULT NULL,
                              `room_id` bigint NOT NULL,
                              `sender_id` bigint NOT NULL,
                              PRIMARY KEY (`id`),
                              KEY `FKf3ugohib8ailcfqq8n6ijgxtc` (`room_id`),
                              KEY `FK2hprvh2kds87xoa1rmggmcdnm` (`sender_id`),
                              CONSTRAINT `FK2hprvh2kds87xoa1rmggmcdnm` FOREIGN KEY (`sender_id`) REFERENCES `member` (`member_id`),
                              CONSTRAINT `FKf3ugohib8ailcfqq8n6ijgxtc` FOREIGN KEY (`room_id`) REFERENCES `dm_room` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `post` (
                        `post_id` bigint NOT NULL AUTO_INCREMENT,
                        `closed` bit(1) NOT NULL,
                        `content` text NOT NULL,
                        `created_at` datetime(6) DEFAULT NULL,
                        `current_count` int NOT NULL,
                        `deadline` date DEFAULT NULL,
                        `max_count` int NOT NULL,
                        `mode` varchar(255) DEFAULT NULL,
                        `tag` varchar(255) DEFAULT NULL,
                        `tech_stacks` varchar(255) DEFAULT NULL,
                        `title` varchar(255) NOT NULL,
                        `view_count` bigint NOT NULL,
                        `member_id` bigint DEFAULT NULL,
                        `room_id` bigint DEFAULT NULL,
                        PRIMARY KEY (`post_id`),
                        KEY `FK83s99f4kx8oiqm3ro0sasmpww` (`member_id`),
                        KEY `FKpybbshjuby2xe268jmywoq0i0` (`room_id`),
                        CONSTRAINT `FK83s99f4kx8oiqm3ro0sasmpww` FOREIGN KEY (`member_id`) REFERENCES `member` (`member_id`),
                        CONSTRAINT `FKpybbshjuby2xe268jmywoq0i0` FOREIGN KEY (`room_id`) REFERENCES `chat_room` (`room_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `applicant` (
                             `id` bigint NOT NULL AUTO_INCREMENT,
                             `applied_at` datetime(6) DEFAULT NULL,
                             `rejected_at` datetime(6) DEFAULT NULL,
                             `status` enum('APPROVED','PENDING','REJECTED') DEFAULT NULL,
                             `member_id` bigint DEFAULT NULL,
                             `post_id` bigint DEFAULT NULL,
                             PRIMARY KEY (`id`),
                             KEY `FKmtvash6sm3fyyv8geg6u0od3n` (`member_id`),
                             KEY `FKhpsey9yn3cfw8s3kb3wplyvf6` (`post_id`),
                             CONSTRAINT `FKhpsey9yn3cfw8s3kb3wplyvf6` FOREIGN KEY (`post_id`) REFERENCES `post` (`post_id`),
                             CONSTRAINT `FKmtvash6sm3fyyv8geg6u0od3n` FOREIGN KEY (`member_id`) REFERENCES `member` (`member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `code_review` (
                               `review_id` bigint NOT NULL AUTO_INCREMENT,
                               `content` text,
                               `created_at` datetime(6) DEFAULT NULL,
                               `line_number` int DEFAULT NULL,
                               `member_id` bigint DEFAULT NULL,
                               `message_id` bigint DEFAULT NULL,
                               PRIMARY KEY (`review_id`),
                               KEY `FKkwyxcx28juq3tlq6c9jmh5e3t` (`member_id`),
                               KEY `FKnegosl4emhxm6voo3bj90107u` (`message_id`),
                               CONSTRAINT `FKkwyxcx28juq3tlq6c9jmh5e3t` FOREIGN KEY (`member_id`) REFERENCES `member` (`member_id`),
                               CONSTRAINT `FKnegosl4emhxm6voo3bj90107u` FOREIGN KEY (`message_id`) REFERENCES `chat_message` (`message_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `friend_request` (
                                  `id` bigint NOT NULL AUTO_INCREMENT,
                                  `rejected_count` int NOT NULL,
                                  `requested_at` datetime(6) NOT NULL,
                                  `response_at` datetime(6) DEFAULT NULL,
                                  `status` enum('ACCEPTED','PENDING','REJECTED') NOT NULL,
                                  `receiver_id` bigint NOT NULL,
                                  `sender_id` bigint NOT NULL,
                                  PRIMARY KEY (`id`),
                                  UNIQUE KEY `UK1o6k35asg93qa1wjg8chjd5rf` (`receiver_id`, `sender_id`),
                                  KEY `FKlplia5yaa4rqrmc15dqltgqdf` (`sender_id`),
                                  CONSTRAINT `FKjb5d7u2slfp3fy0912mq0r1rf` FOREIGN KEY (`receiver_id`) REFERENCES `member` (`member_id`),
                                  CONSTRAINT `FKlplia5yaa4rqrmc15dqltgqdf` FOREIGN KEY (`sender_id`) REFERENCES `member` (`member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `friends` (
                           `id` bigint NOT NULL AUTO_INCREMENT,
                           `created_at` datetime(6) NOT NULL,
                           `friend_id` bigint NOT NULL,
                           `owner_id` bigint NOT NULL,
                           PRIMARY KEY (`id`),
                           UNIQUE KEY `UKq7plg0bpvepipoc161jk3tt2a` (`owner_id`, `friend_id`),
                           KEY `idx_owner_friend` (`owner_id`, `friend_id`),
                           KEY `FKirkmx9dwtl2ovtm4oyun22iof` (`friend_id`),
                           CONSTRAINT `FKirkmx9dwtl2ovtm4oyun22iof` FOREIGN KEY (`friend_id`) REFERENCES `member` (`member_id`),
                           CONSTRAINT `FKlqtnxn1b7yrjwad03y2k2b7h0` FOREIGN KEY (`owner_id`) REFERENCES `member` (`member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `notification` (
                                `id` bigint NOT NULL AUTO_INCREMENT,
                                `created_at` datetime(6) DEFAULT NULL,
                                `is_read` bit(1) NOT NULL,
                                `reference_id` bigint DEFAULT NULL,
                                `type` enum('CODE_REVIEW','FRIEND_ACCEPTED','FRIEND_REJECTED','FRIEND_REQUESTED','NEW_DM','STUDY_APPLY','STUDY_APPROVED','STUDY_REJECTED','WE_ARE_FRIEND_NOW') DEFAULT NULL,
                                `receiver_member_id` bigint DEFAULT NULL,
                                `sender_member_id` bigint DEFAULT NULL,
                                PRIMARY KEY (`id`),
                                KEY `FK5ehgc9dg2vxqpgrjbwduoe8fr` (`receiver_member_id`),
                                KEY `FKnxdkyju0u9w6537jxpgp34ex3` (`sender_member_id`),
                                CONSTRAINT `FK5ehgc9dg2vxqpgrjbwduoe8fr` FOREIGN KEY (`receiver_member_id`) REFERENCES `member` (`member_id`),
                                CONSTRAINT `FKnxdkyju0u9w6537jxpgp34ex3` FOREIGN KEY (`sender_member_id`) REFERENCES `member` (`member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;