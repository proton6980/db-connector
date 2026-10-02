# db-connector

本地数据库 MCP sidecar：把已配置的数据库（达梦 DM8、MySQL、人大金仓 KADB 数据仓库、Oracle）以 MCP tool 暴露给 Cursor / Claude Code / Grok CLI 等 AI 客户端，支持查询、DML、DDL、EXPLAIN + 元数据 + 全量审计。内置 Web 管理控制台，一次启动同时获得 MCP SSE 服务 + 可视化管理界面。

## 安全边界

- **HTTP/SSE 模式**：MCP 通信走 Streamable HTTP（含 SSE），Java 监听 `127.0.0.1:63306`，仅本地访问
- **写权限默认关闭、按连接放开**：`query_database` 只读；`execute_dml` / `execute_ddl` 需要先在 Web 控制台对该连接分别勾选允许 DML / DDL。存量连接升级后一律锁写，需显式开启
- **多层防御**：
  1. JSqlParser 预检：仅放行单条语句并按 SELECT/DML/DDL/EXPLAIN 分类，拒绝多语句/`SELECT INTO`/`EXPLAIN ANALYZE`
  2. UPDATE/DELETE 默认必须带 WHERE，全表操作须显式 `allowFullTable=true`
  3. 连接池隔离：只读池 `readOnly(true)`，写操作走独立写池（`minIdle=0` 懒创建）
  4. `setMaxRows(100)`（查询）+ `setQueryTimeout(10s)`；语句执行后自动提交、不可回滚
  5. **生产建议给目标库配最小权限账号**（只读或按需授权），驱动权限层兜底
  6. 全量审计：每次调用落 H2 文件库（SUCCESS / ERROR / BLOCKED，含工具名与影响行数），跨重启可查
- 凭证 AES-256-GCM 加密落库（密文格式 `v1:...`，版本前缀留轮换余地）
- 多用户 / 远程部署 / RBAC 是 Phase 4 的事，当前明确不做

## 安装

前提条件：**Node.js 16+**（不需要安装 JDK，JRE 随包自动提供）。

### npx（推荐）

```bash
npx db-connector-mcp
```

### 一键脚本

```bash
curl -fsSL https://raw.githubusercontent.com/proton6980/db-connector/main/npm/install.sh | sh
```

安装到 `~/.db-connector-mcp/db-connector`，自动加入 PATH。

### 从源码构建

前置条件：JDK 25 + Node.js 16+

```bash
git clone https://github.com/proton6980/db-connector.git
cd db-connector
./scripts/release.sh build
```

产物在 `npm/platforms/<当前平台>/`，包含 JRE、JAR 和启动器。

## 启动

```bash
npx db-connector-mcp
```

启动后同时提供两个服务：

| 服务 | 地址 | 说明 |
|------|------|------|
| MCP SSE Endpoint | `http://127.0.0.1:63306/mcp` | MCP 客户端连接此地址 |
| Web 管理控制台 | `http://127.0.0.1:63380` | 浏览器打开此地址管理连接、查询、审计 |

端口可通过环境变量配置：
DBCONNECTOR_PORT（Java 端口，默认 63306）和 DBCONNECTOR_WEB_PORT（前端端口，默认 63380）。若默认端口被占用，启动时将自动选择空闲端口。

## Web 管理控制台

浏览器打开 `http://127.0.0.1:63380` 即可使用内置管理控制台：

- **仪表盘**：近 24h 查询统计、错误率、Top SQL、连接池状态
- **连接管理**：CRUD 数据库连接、测试连通性、重载连接池
- **审计日志**：分页查询、多条件筛选、CSV 导出

## MCP Tools

### 连接管理

| Tool | 说明 |
|------|------|
| `list_connections` | 列出全部 active 连接（id、名称、类型、地址、库名） |
| `create_connection` | 创建新连接：id + name + dbType(DM/MYSQL/KINGBASE/ORACLE/H2) + host + port + username + password + ... |
| `update_connection` | 修改已有连接：按 connection（ID 或名称）定位，仅传需改字段 |
| `delete_connection` | 删除连接并关闭连接池，不可恢复 |
| `test_connection` | 测试连接可达性（JDBC `SELECT 1`），返回成功/失败及耗时 |

### SQL 执行

| Tool | 说明 |
|------|------|
| `query_database` | 只读查询：`connection` + `sql` + `params`（`:name` 命名参数，绑定不拼接） |
| `execute_dml` | 单条 INSERT/UPDATE/DELETE/MERGE，返回影响行数。需连接开启 DML；`allowFullTable=true` 放行无 WHERE 的全表操作 |
| `execute_ddl` | 单条 CREATE/ALTER/DROP/TRUNCATE。需连接开启 DDL |
| `explain_sql` | SELECT 执行计划：只传 SELECT 原文（工具自动加 EXPLAIN），禁止自带 EXPLAIN / ANALYZE |

### 元数据

| Tool | 说明 |
|------|------|
| `list_tables` | 表清单（schema、表注释） |
| `describe_table` | 字段/类型/可空/默认值/注释/主键/索引，`table` 可写 `TABLE` 或 `SCHEMA.TABLE` |
| `get_table_sample` | 表样例数据（≤5 行） |

查询/EXPLAIN 返回 `{columns, rows, rowCount, truncated, durationMs}`；DML 返回 `{operation, affectedRows, durationMs}`。被拦截/出错返回 LLM 友好文本，如 `[SQL_REJECTED] UPDATE/DELETE 缺少 WHERE；确需全表操作必须显式 allowFullTable=true`。

### Oracle 说明与已知限制

- 表单中的"数据库名"对 Oracle 填 **service name**（XE 21c 通常为 `XEPDB1`，企业版常见 `ORCLPDB`/`ORCL`）；URL 仅支持 service name 形式 `jdbc:oracle:thin:@//host:1521/service`，不支持 SID 与 TNS 形式
- `extraParams` 对 Oracle **不生效**（thin URL 不支持 `?k=v` 查询串），填写会被忽略
- Oracle 旧式外连接 `(+)` 可能被 JSqlParser 预检拒绝：请改写为 ANSI JOIN
- 数字位置绑定 `:1`（Oracle 原生风格）不支持，统一用 `:name` 风格
- 多语句、PL/SQL 匿名块（`BEGIN ... END;`/`/`）不在支持范围，安全预检会拦截

## MCP 客户端配置

加密密钥由程序自动管理：首次启动时随机生成，存入 `~/.db-connector-mcp/data/.crypto-key`（权限 `rw-------`），后续启动自动读取。**无需在配置文件中暴露密钥，Agent 不可见。**

### Cursor（`.cursor/mcp.json`）

```json
{
  "mcpServers": {
    "db-connector": {
      "url": "http://127.0.0.1:63306/mcp"
    }
  }
}
```

### Claude Code

```bash
claude mcp add db-connector --transport sse --url http://127.0.0.1:63306/mcp
```

### VS Code / Cline / Roo Code（`.vscode/mcp.json`）

```json
{
  "servers": {
    "db-connector": {
      "type": "sse",
      "url": "http://127.0.0.1:63306/mcp"
    }
  }
}
```

## 环境变量

| 变量 | 必填 | 说明 |
|------|------|------|
| `DBCONNECTOR_CRYPTO_KEY` | 否 | 凭证加密口令（任意字符串，SHA-256 派生 AES-256 密钥）。未设置时自动生成并保存到 `~/.db-connector-mcp/data/.crypto-key`，无需手动配置 |
| `DBCONNECTOR_DATA_DIR` | 否 | 数据目录（H2 库 + 加密密钥），默认 `~/.db-connector-mcp/data`（Windows 为 `%USERPROFILE%\.db-connector-mcp\data`）。固定到用户主目录，与启动时的当前目录无关 |
| `DBCONNECTOR_PORT` | 否 | Java 后端端口（默认 63306） |
| `DBCONNECTOR_WEB_PORT` | 否 | Web 控制台端口（默认 63380） |

## 技术栈

- Java 25 + Micronaut 5.2.0 + Micronaut MCP Server 2.1.0（Streamable HTTP 传输）
- Micronaut HTTP Server Netty（REST API + SSE endpoint）
- Temurin 25 JRE 随 npm 平台包发行（无需用户安装 JDK）
- 达梦驱动 `DmJdbcDriver11 8.1.4.125`（官方 JDBC 驱动，随项目分发）
- MySQL 驱动 `mysql-connector-j 26.7.0`（Micronaut BOM 统一管理版本）
- 人大金仓驱动 `kingbase8 9.0.1`（KADB 基于 Greenplum/PostgreSQL，兼容 PG 线路协议）
- Oracle 驱动 `ojdbc11 23.26.3.0.0`（Micronaut BOM 统一管理，Oracle 23ai thin driver）
- JSqlParser（SQL 预检）、HikariCP（动态连接池）、H2（元数据/审计存储）
- npm + Express（前端静态文件服务）

## 开发

```bash
mvn -f java/pom.xml verify                     # 全量测试
# DM 容器在场时的连通/元数据探针：
DM_URL=jdbc:dm://localhost:5236 DM_USER=SYSDBA DM_PASSWORD=SYSDBA001 mvn -f java/pom.xml test -Dtest='Dm*'
# MySQL 实例的全链路冒烟：
MYSQL_URL='jdbc:mysql://localhost:3306/testdb?useSSL=false&allowPublicKeyRetrieval=true' \
  MYSQL_USER=root MYSQL_PASSWORD=*** mvn -f java/pom.xml test -Dtest='MysqlToolsSmokeTest'
# Kingbase KADB 的全链路冒烟：
KINGBASE_URL='jdbc:kingbase8://localhost:5432/testdb' \
  KINGBASE_USER=system KINGBASE_PASSWORD=*** mvn -f java/pom.xml test -Dtest='KingbaseToolsSmokeTest'
# Oracle 的全链路冒烟（临时用户即临时 schema）：
ORACLE_URL='jdbc:oracle:thin:@//localhost:1521/XEPDB1' \
  ORACLE_ADMIN_USER=system ORACLE_ADMIN_PASSWORD=*** mvn -f java/pom.xml test -Dtest='OracleToolsSmokeTest'
```

## 版本管理

版本号统一在 `VERSION` 文件中管理，通过 `scripts/release.sh` 同步到所有文件：

```bash
./scripts/release.sh bump 0.5.0    # 升级版本并同步
./scripts/release.sh sync          # 仅同步当前版本
./scripts/release.sh build         # 构建当前平台的 JRE + JAR 包
./scripts/release.sh release       # 一键发布（sync + build + 指引）
```