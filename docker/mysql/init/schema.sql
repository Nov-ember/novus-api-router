SET NAMES utf8mb4;

CREATE TABLE `user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `username` VARCHAR(20) NOT NULL,
    `nickname` VARCHAR(30) NULL,
    `gender` TINYINT NULL COMMENT '0 未知，1 男，2 女',
    `avatar` VARCHAR(2048) NULL,
    `email` VARCHAR(254) NOT NULL,
    `password_hash` VARCHAR(100) NOT NULL COMMENT 'BCrypt 哈希，不保存明文密码',
    `active_key_count` INT NOT NULL DEFAULT 0,
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_username` (`username`),
    UNIQUE KEY `uk_user_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `user_wallet` (
    `user_id` BIGINT NOT NULL,
    `available_balance` DECIMAL(20,8) NOT NULL DEFAULT 0 COMMENT '可用余额，元',
    `frozen_amount` DECIMAL(20,8) NOT NULL DEFAULT 0 COMMENT '冻结余额，元',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `api_key` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `name` VARCHAR(50) NOT NULL,
    `key_hash` CHAR(64) NOT NULL COMMENT 'SHA-256 十六进制摘要',
    `key_suffix` CHAR(4) NOT NULL,
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '1 有效，0 已吊销',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_api_key_hash` (`key_hash`),
    KEY `idx_api_key_user_status_created` (`user_id`, `status`, `create_time`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `bill` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL,
    `api_key_id` BIGINT NOT NULL,
    `model_alias` VARCHAR(255) NOT NULL COMMENT '请求使用的模型别名',
    `input_tokens` INT NULL,
    `cached_input_tokens` INT NULL COMMENT '包含在 input_tokens 内',
    `output_tokens` INT NULL,
    `frozen_amount` DECIMAL(20,8) NOT NULL COMMENT '本笔请求预留金额',
    `input_price_per_million` DECIMAL(16,8) NOT NULL COMMENT '普通输入价格快照，元每百万 token',
    `cached_input_price_per_million` DECIMAL(16,8) NOT NULL COMMENT '缓存输入价格快照，元每百万 token',
    `output_price_per_million` DECIMAL(16,8) NOT NULL COMMENT '输出价格快照，元每百万 token',
    `actual_amount` DECIMAL(20,8) NULL COMMENT '实际扣费，元',
    `status` VARCHAR(16) NOT NULL COMMENT 'RESERVED / SETTLED / RELEASED',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_bill_user_status_created` (`user_id`, `status`, `create_time`),
    KEY `idx_bill_api_key` (`api_key_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
