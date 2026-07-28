-- Container-only bootstrap for a brand-new, empty database.
-- Spring executes this before SchemaInitializer beans. IF NOT EXISTS makes
-- repeated starts safe and never changes existing authentication tables or rows.
CREATE TABLE IF NOT EXISTS users (
    user_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    username VARCHAR(50) NOT NULL COMMENT '用户名（登录账号）',
    email VARCHAR(100) NOT NULL COMMENT '邮箱',
    password_hash VARCHAR(255) NOT NULL COMMENT '密码哈希（bcrypt）',
    full_name VARCHAR(100) DEFAULT NULL COMMENT '姓名',
    role ENUM('admin', 'editor', 'viewer') NOT NULL DEFAULT 'viewer' COMMENT '用户角色',
    is_active BOOLEAN DEFAULT TRUE COMMENT '是否激活',
    last_login DATETIME DEFAULT NULL COMMENT '最后登录时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    can_download BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否允许下载',
    can_upload BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否允许上传数据',
    can_review_uploads BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否允许审核上传批次',
    can_sync_data BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否允许同步数据入库',
    PRIMARY KEY (user_id),
    UNIQUE KEY uk_users_username (username),
    UNIQUE KEY uk_users_email (email),
    KEY idx_users_role (role),
    KEY idx_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统用户表';

CREATE TABLE IF NOT EXISTS login_logs (
    log_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    login_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    ip_address VARCHAR(45) DEFAULT NULL COMMENT 'IP地址',
    user_agent TEXT COMMENT '浏览器信息',
    PRIMARY KEY (log_id),
    KEY idx_login_logs_user_id (user_id),
    CONSTRAINT fk_login_logs_user FOREIGN KEY (user_id)
        REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='登录日志表';
