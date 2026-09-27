-- 数据库连接配置
CREATE TABLE IF NOT EXISTS db_connections (
    id          VARCHAR(36) PRIMARY KEY,
    name        VARCHAR(100) NOT NULL UNIQUE,  -- 连接别名，如 "生产-订单库"
    db_type     VARCHAR(20) NOT NULL,           -- DM / KINGBASE / MYSQL / ORACLE / REDIS
    host        VARCHAR(255) NOT NULL,
    port        INT NOT NULL,
    username    VARCHAR(100) NOT NULL,
    password    VARCHAR(500) NOT NULL,          -- AES-256 加密存储
    extra_params TEXT,                           -- JSON: {"ssl": true, "compatibleMode": "oracle"}
    pool_min    INT DEFAULT 2,
    pool_max    INT DEFAULT 10,
    is_active   BOOLEAN DEFAULT TRUE,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    database_name VARCHAR(100)
);

-- 账号权限映射（控制哪些Agent/用户能用哪些连接）
CREATE TABLE IF NOT EXISTS account_permissions (
    id          VARCHAR(36) PRIMARY KEY,
    account_id  VARCHAR(100) NOT NULL,          -- Agent ID / 用户名 / API Key Hash
    connection_id VARCHAR(36) NOT NULL,
    permission  VARCHAR(20) NOT NULL,           -- READ_ONLY / READ_WRITE / ADMIN
    allowed_tools TEXT,                          -- JSON数组: ["query","list_tables"]
    max_rows    INT DEFAULT 100,                -- 该账号单次查询最大返回行数
    expires_at  TIMESTAMP,
    FOREIGN KEY (connection_id) REFERENCES db_connections(id)
);

-- SQL 执行日志
CREATE TABLE IF NOT EXISTS sql_audit_log (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    connection_id VARCHAR(36) NOT NULL,
    account_id  VARCHAR(100) NOT NULL,
    sql_text    TEXT NOT NULL,
    params      TEXT,                            -- JSON，脱敏后
    status      VARCHAR(20) NOT NULL,           -- SUCCESS / ERROR / BLOCKED
    row_count   INT,
    duration_ms LONG,
    error_msg   TEXT,
    client_ip   VARCHAR(45),
    executed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 索引
CREATE INDEX IF NOT EXISTS idx_audit_conn ON sql_audit_log(connection_id, executed_at);
CREATE INDEX IF NOT EXISTS idx_audit_account ON sql_audit_log(account_id, executed_at);
CREATE INDEX IF NOT EXISTS idx_perm_account ON account_permissions(account_id);
