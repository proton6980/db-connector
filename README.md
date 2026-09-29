# db-connector

本地数据库 MCP sidecar：把已配置的数据库（P1：达梦 DM8）以 MCP tool 暴露给 Cursor / Claude Code / Grok CLI 等 AI 客户端，只读查询 + 元数据 + 全量审计。内置 Web 管理控制台，一次启动同时获得 MCP SSE 服务 + 可视化管理界面。

## 安全边界

- **HTTP/SSE 模式**：MCP 通信走 Streamable HTTP（含 SSE），Java 监听 `127.0.0.1:63306`，仅本地访问
- **只读多层防御**：
  1. JSqlParser 预检：仅放行单条 `SELECT` / `WITH(CTE)`，拒绝 DDL/DML/多语句/`SELECT INTO`
  2. JDBC 层：连接池 `readOnly(true)` + `setMaxRows(100)` + `setQueryTimeout(10s)`
  3. **生产建议给目标库配只读账号**（如 DM 的 `GRANT SELECT` 账号），驱动权限层兜底
  4. 全量审计：每次调用落 H2 文件库（SUCCESS / ERROR / BLOCKED），跨重启可查
- 凭证 AES-256-GCM 加密落库（密文格式 `v1:...`，版本前缀留轮换余地）
- 多用户 / 远程部署 / RBAC 是 Phase 4 的事，当前明确不做

## 安装

### 方式一：一键脚本（推荐）

```bash
curl -fsSL https://raw.githubusercontent.com/proton6980/db-connector/main/npm/install.sh | sh
```

安装到 `~/.db-connector-mcp/db-connector-mcp`，自动加入 PATH。

### 方式二：npm

```bash
npm install db-connector-mcp
npx db-connector-mcp
```

### 方式三：手动下载

从 [GitHub Releases](https://github.com/proton6980/db-connector/releases) 下载对应平台的 Native 二进制。如果 Native 暂不可用，可使用 JAR 兜底（需 JDK 17+）。

| 平台 | Native 二进制 | JAR 兜底 |
|------|-------------|---------|
| macOS Apple Silicon | `db-connector-darwin-arm64` | `db-connector-0.4.0.jar` |
| macOS Intel | `db-connector-darwin-x64` | `db-connector-0.4.0.jar` |
| Linux x64 | `db-connector-linux-x64` | `db-connector-0.4.0.jar` |
| Windows x64 | `db-connector-windows-x64.exe` | `db-connector-0.4.0.jar` |

**Native 方式**（推荐）：
```bash
# 以 macOS ARM64 为例，其他平台替换为对应文件名
chmod +x db-connector-darwin-arm64
./db-connector-darwin-arm64
```

**JAR 方式**（Native 不可用时的兜底，需 JDK 17+）：
```bash
java -jar db-connector-0.4.0.jar
```

> JDK 下载：<https://adoptium.net/>

### 方式四：从源码构建

前置条件：GraalVM JDK 25（含 native-image）

```bash
git clone https://github.com/proton6980/db-connector.git
cd db-connector
./scripts/release.sh build
```

产物输出到 `dist/` 目录。

## 启动

```bash
npx db-connector-mcp
```

启动后同时提供两个服务：

| 服务 | 地址 | 说明 |
|------|------|------|
| MCP SSE Endpoint | `http://127.0.0.1:63306/mcp` | MCP 客户端连接此地址 |
| Web 管理控制台 | `http://127.0.0.1:68080` | 浏览器打开此地址管理连接、查询、审计 |

端口可通过环境变量配置：
DBCONNECTOR_PORT（Java 端口，默认 63306）和 DBCONNECTOR_WEB_PORT（前端端口，默认 68080）。若默认端口被占用，启动时将自动选择空闲端口。

## Web 管理控制台

浏览器打开 `http://127.0.0.1:68080` 即可使用内置管理控制台：

- **仪表盘**：近 24h 查询统计、错误率、Top SQL、连接池状态
- **连接管理**：CRUD 数据库连接、测试连通性、重载连接池
- **审计日志**：分页查询、多条件筛选、CSV 导出

## MCP Tools

### 连接管理

| Tool | 说明 |
|------|------|
| `list_connections` | 列出全部 active 连接（id、名称、类型、地址、库名） |
| `create_connection` | 创建新连接：id + name + dbType(DM/H2) + host + port + username + password + ... |
| `update_connection` | 修改已有连接：按 connection（ID 或名称）定位，仅传需改字段 |
| `delete_connection` | 删除连接并关闭连接池，不可恢复 |
| `test_connection` | 测试连接可达性（JDBC `SELECT 1`），返回成功/失败及耗时 |

### 查询与元数据

| Tool | 说明 |
|------|------|
| `query_database` | 只读查询：`connection`（ID 或名称）+ `sql` + `params`（`:name` 命名参数，绑定不拼接） |
| `list_tables` | 表清单（schema、表注释） |
| `describe_table` | 字段/类型/可空/默认值/注释/主键/索引，`table` 可写 `TABLE` 或 `SCHEMA.TABLE` |
| `get_table_sample` | 表样例数据（≤5 行） |

查询返回 `{columns, rows, rowCount, truncated, durationMs}`；被拦截/出错返回 LLM 友好文本，如 `[SQL_REJECTED] 拒绝多语句执行，仅允许单条查询`。

## MCP 客户端配置

加密密钥由程序自动管理：首次启动时随机生成，存入 `data/.crypto-key`（权限 `rw-------`），后续启动自动读取。**无需在配置文件中暴露密钥，Agent 不可见。**

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
| `DBCONNECTOR_CRYPTO_KEY` | 否 | 凭证加密口令（任意字符串，SHA-256 派生 AES-256 密钥）。未设置时自动生成并保存到 `data/.crypto-key`，无需手动配置 |
| `DBCONNECTOR_PORT` | 否 | Java 后端端口（默认 63306） |
| `DBCONNECTOR_WEB_PORT` | 否 | Web 控制台端口（默认 68080） |

## 技术栈

- Java 25 + Micronaut 5.2.0 + Micronaut MCP Server 2.1.0（Streamable HTTP 传输）
- Micronaut HTTP Server Netty（REST API + SSE endpoint）
- GraalVM Native Image（单文件分发，无需 JRE）
- 达梦驱动 `DmJdbcDriver11 8.1.4.125`（官方 JDBC 驱动，随项目分发）
- JSqlParser（SQL 预检）、HikariCP（动态连接池）、H2（元数据/审计存储）
- npm + Express（前端静态文件服务）

## 开发

```bash
mvn verify                     # 全量测试
# DM 容器在场时的连通/元数据探针：
DM_URL=jdbc:dm://localhost:5236 DM_USER=SYSDBA DM_PASSWORD=SYSDBA001 mvn test -Dtest='Dm*'
```

## 版本管理

版本号统一在 `VERSION` 文件中管理，通过 `scripts/release.sh` 同步到所有文件：

```bash
./scripts/release.sh bump 0.3.0    # 升级版本并同步
./scripts/release.sh sync          # 仅同步当前版本
./scripts/release.sh build         # 构建当前平台的 native image
./scripts/release.sh release       # 一键发布（sync + build + 指引）
```