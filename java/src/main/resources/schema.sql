CREATE TABLE IF NOT EXISTS db_connections (
    id          VARCHAR(36) PRIMARY KEY,
    name        VARCHAR(100) NOT NULL UNIQUE,
    db_type     VARCHAR(20) NOT NULL,
    host        VARCHAR(255) NOT NULL,
    port        INT NOT NULL,
    username    VARCHAR(100) NOT NULL,
    password    VARCHAR(500) NOT NULL,
    database_name VARCHAR(100),
    extra_params TEXT,
    pool_min    INT DEFAULT 2,
    pool_max    INT DEFAULT 10,
    active      BOOLEAN DEFAULT TRUE,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS account_permissions (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id  VARCHAR(100) NOT NULL,
    connection_id VARCHAR(36) NOT NULL,
    role        VARCHAR(20) NOT NULL
);

CREATE TABLE IF NOT EXISTS sql_audit_log (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    connection_id VARCHAR(36) NOT NULL,
    account_id  VARCHAR(100) NOT NULL,
    sql_text    TEXT NOT NULL,
    params      TEXT,
    status      VARCHAR(20) NOT NULL,
    row_count   INT,
    duration_ms BIGINT,
    error_msg   TEXT,
    executed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_audit_conn ON sql_audit_log(connection_id, executed_at);
CREATE INDEX IF NOT EXISTS idx_audit_account ON sql_audit_log(account_id, executed_at);
CREATE INDEX IF NOT EXISTS idx_perm_account ON account_permissions(account_id);