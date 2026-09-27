# db-connector

本地数据库 MCP sidecar：把已配置的数据库（P1：达梦 DM8）以 MCP tool 暴露给 Grok CLI / OpenCode / Claude Code 等 AI 客户端，只读查询 + 元数据 + 全量审计。

## 安全边界（P1）

- **仅监听 `127.0.0.1:8080`**，不对外网/局域网暴露，无鉴权（单用户本地 sidecar）
- **只读多层防御**：
  1. JSqlParser 预检：仅放行单条 `SELECT` / `WITH(CTE)`，拒绝 DDL/DML/多语句/`SELECT INTO`
  2. JDBC 层：连接池 `readOnly(true)` + `setMaxRows(100)` + `setQueryTimeout(10s)`
  3. **生产建议给目标库配只读账号**（如 DM 的 `GRANT SELECT` 账号），驱动权限层兜底
  4. 全量审计：每次调用落 H2 文件库（SUCCESS / ERROR / BLOCKED），跨重启可查
- 凭证 AES-256-GCM 加密落库（密文格式 `v1:...`，版本前缀留轮换余地）
- 多用户 / 远程部署 / RBAC 是 Phase 4 的事，P1 明确不做

## 快速开始

```bash
# 1. 密钥（dev 可省略，默认开发密钥；生产必须注入）
export DBCONNECTOR_CRYPTO_KEY='your-secret'

# 2. 启动（连接配置在 application.yml 的 dbconnector.seed，启动时加密入库）
mvn spring-boot:run

# 3. 冒烟（前置：application.yml 种子里的 DM 容器在跑）
scripts/mcp-smoke.sh
```

元数据库为文件嵌入式 H2（`./data/dbconnector.mv.db`），已加入 `.gitignore`。

## MCP Tools

| Tool | 说明 |
|------|------|
| `list_connections` | 列出全部 active 连接（id、名称、类型、地址、库名） |
| `query_database` | 只读查询：`connection`（ID 或名称）+ `sql` + `params`（`:name` 命名参数，绑定不拼接） |
| `list_tables` | 表清单（schema、表注释） |
| `describe_table` | 字段/类型/可空/默认值/注释/主键/索引，`table` 可写 `TABLE` 或 `SCHEMA.TABLE` |
| `get_table_sample` | 表样例数据（≤5 行） |

查询返回 `{columns, rows, rowCount, truncated, durationMs}`；被拦截/出错返回 LLM 友好文本，如 `[SQL_REJECTED] 拒绝多语句执行，仅允许单条查询`。

## MCP 客户端配置

服务使用 Streamable HTTP 传输（MCP 2025-03-26 规范）。端口默认 `8080`，可通过 `server.port` 修改；以下以默认端口为例。

**Grok CLI**：

```bash
grok mcp add db-connector --transport sse --url "http://127.0.0.1:8080/mcp"
grok mcp doctor db-connector   # 诊断连接
```

**OpenCode**（在项目根目录创建 `opencode.json`，已加入 `.gitignore`）：

```json
{
  "mcp": {
    "db-connector": {
      "type": "remote",
      "url": "http://127.0.0.1:8080/mcp",
      "enabled": true
    }
  }
}
```

**Claude Code**：

```bash
claude mcp add --transport sse db-connector http://127.0.0.1:8080/mcp
```

## 技术栈

- Java 21 + Spring Boot 3.4 + Spring AI 1.1.0（MCP WebMVC，Streamable HTTP 传输）
- 达梦驱动 `DmJdbcDriver11 8.1.4.125`（官方 JDBC 驱动，随项目分发）
- JSqlParser（SQL 预检）、HikariCP（动态连接池）、H2（元数据/审计存储）、JaCoCo（覆盖率门禁 70% line）

## 开发

```bash
mvn verify                     # 全量测试 + JaCoCo 门禁
# DM 容器在场时的连通/元数据探针：
DM_URL=jdbc:dm://localhost:5236 DM_USER=SYSDBA DM_PASSWORD=SYSDBA001 mvn test -Dtest='Dm*'
```

- 驱动声明：DM 使用官方 `DmJdbcDriver11`；Kingbase/Oracle/MySQL adapter 留待后续阶段
- 审计策略：有界队列 + 每秒批量刷盘，队列满丢弃并计数（不反压查询路径）