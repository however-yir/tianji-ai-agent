-- tj-aigc 初始迁移：chat_session 表
CREATE TABLE IF NOT EXISTS `chat_session` (
    `id`          BIGINT       NOT NULL COMMENT '数据id',
    `session_id`  VARCHAR(64)  NOT NULL COMMENT '会话id',
    `user_id`     BIGINT       NOT NULL COMMENT '用户id',
    `title`       VARCHAR(255) DEFAULT NULL COMMENT '会话标题',
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `creater`     BIGINT       DEFAULT NULL COMMENT '创建人',
    `updater`     BIGINT       DEFAULT NULL COMMENT '更新人',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_session_id` (`session_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='AI聊天会话';
