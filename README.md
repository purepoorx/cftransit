# CF Transit 优选

Cloudflare Transit IP 优选工具，从服务端管理的中转代理 IP 池中筛选低延迟、高带宽的优质节点。

## 架构

```
┌──────────────────────┐     ┌──────────────────────┐
│   Android 客户端      │────→│     Go 服务端         │
│   (Java + AAR)        │     │   (Gin + PostgreSQL)  │
├──────────────────────┤     └──────────┬───────────┘
│   Go 核心扫描引擎      │                │
│   (gomobile bind)     │     ┌──────────▼───────────┐
└──────────────────────┘     │   PostgreSQL 数据库    │
                             │   (proxies + ip_info)  │
                             └────────────────────────┘
```

**核心扫描流程：** 服务端 API 获取代理列表 → 随机抽样 50 个 → TCP RTT 测试取 Top 10 → HTTP 下载测速 → 达标即返回（最多 5 轮）

## 项目结构

```
cftransit/
├── core/                          # Go 扫描引擎（gomobile 导出）
│   ├── config.go                  # API 配置（BaseURL, Key）
│   ├── api.go                     # 服务端通信（拉取代理、测速 URL）
│   ├── progress.go                # 进度管理 + 取消机制
│   ├── rtt.go                     # TCP RTT 测试（并发）
│   ├── speed.go                   # HTTP 下载速度测试
│   ├── scan.go                    # 扫描编排（5 轮循环）
│   ├── export.go                  # 缓存管理
│   └── go.mod
├── server/                        # Go 服务端
│   ├── main.go                    # 入口（Gin 路由 + 优雅关闭）
│   ├── config.go                  # 配置加载（YAML + 环境变量）
│   ├── config.yaml                # 默认配置
│   ├── models/
│   │   ├── db.go                  # GORM 连接初始化
│   │   ├── proxy.go               # Proxy 模型（映射 proxies 表）
│   │   └── ip_info.go             # IPInfo 模型（映射 ip_info 表）
│   ├── middleware/
│   │   ├── apikey.go              # API Key 鉴权
│   │   └── cors.go                # CORS 中间件
│   ├── handlers/
│   │   ├── proxy.go               # GET /proxies（分页、过滤、随机采样）
│   │   ├── stats.go               # GET /stats（统计信息）
│   │   └── speedtest.go           # GET /speedtest/url
│   ├── go.mod
│   └── go.sum
├── android/                       # Android 客户端
│   ├── app/
│   │   ├── build.gradle
│   │   └── src/main/
│   │       ├── AndroidManifest.xml
│   │       ├── java/com/cftransit/app/
│   │       │   ├── MainActivity.java       # 扫描 + 历史 + 主题
│   │       │   └── SettingsActivity.java   # API 配置 + 连接测试
│   │       └── res/                        # 布局、颜色、主题、图标
│   ├── build.gradle
│   ├── settings.gradle
│   └── gradle.properties
└── scripts/                       # 构建脚本
    ├── env.sh                     # 编译环境配置
    ├── build-server.sh            # 服务端构建
    └── build-apk.sh               # APK 构建（gomobile + gradle）
```

## 技术栈

| 组件 | 技术 |
|------|------|
| 扫描引擎 | Go 1.25，package `transit` |
| Web 框架 | Gin |
| 数据库 | PostgreSQL（GORM 只读模式，不迁移） |
| 跨语言绑定 | gomobile（Go → AAR） |
| Android | Java + AndroidX AppCompat |
| 构建 | Gradle 8.x + NDK 26.x |

## 服务端

### 环境变量

| 变量名 | 说明 | 默认值 |
|--------|------|--------|
| `CFTRANSIT_DSN` | PostgreSQL 连接串 | config.yaml 中的值 |
| `CFTRANSIT_API_KEY` | API 认证密钥 | config.yaml 中的值 |
| `CFTRANSIT_PORT` | 监听端口 | 8080 |
| `CFTRANSIT_SPEEDTEST_URL` | 测速下载地址 | Cloudflare 官方 URL |

环境变量优先级高于 `config.yaml`。

### API 端点

所有端点需要 `X-API-Key` 请求头或 `api_key` 查询参数进行认证。

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/proxies` | 查询代理列表，支持分页和过滤 |
| GET | `/proxies?sample=50` | 随机采样 50 个代理 |
| GET | `/stats` | 代理总数、DC/Region 分组统计 |
| GET | `/speedtest/url` | 返回测速文件 URL |

**查询参数（/proxies）：**

| 参数 | 说明 |
|------|------|
| `page` | 页码（默认 1） |
| `per_page` | 每页数量（默认 50，最大 200） |
| `sample` | 随机采样数量（启用后忽略分页） |
| `dc` | 按数据中心过滤（如 SJC、LAX） |
| `region` | 按区域过滤（如 North America） |
| `tls` | 按 TLS 支持过滤（true/false） |

### 数据库表

**proxies** — 代理记录

| 列 | 类型 | 说明 |
|----|------|------|
| id | bigserial PK | 自增主键 |
| ip | inet | IP 地址 |
| port | integer | 端口 |
| self_egress | boolean | 是否自出口 |
| tls | boolean | TLS 支持 |
| dc | text | 数据中心代码 |
| region | text | 区域 |
| city | text | 城市 |
| latency_ms | integer | 延迟（ms） |
| source_port | integer | 源端口 |

**ip_info** — IP 地理信息（LEFT JOIN 补充）

| 列 | 类型 | 说明 |
|----|------|------|
| ip | inet PK | IP 地址 |
| city / region / country | text | 地理信息 |
| org / timezone | text | 组织和时区 |
| asn_number / asn_name | int / text | ASN 信息 |

### 启动

```bash
cd server
go build -o cftransit-server .
./cftransit-server
# 或设置环境变量覆盖
CFTRANSIT_PORT=9090 CFTRANSIT_API_KEY=mysecret ./cftransit-server
```

## 核心扫描引擎

扫描引擎编译为 AAR 供 Android 调用，导出的核心函数：

| 函数 | 说明 |
|------|------|
| `SetAPIConfig(baseURL, key)` | 设置服务端 API 地址和密钥 |
| `SetCacheDir(dir)` | 设置缓存目录 |
| `GetIPs(bandwidth)` | 启动扫描，返回 JSON 结果 |
| `GetProgress()` | 获取当前进度描述（轮询） |
| `CancelScan()` | 取消扫描 |
| `TestConnection()` | 测试服务端连接 |

**扫描参数：** `bandwidth` — 期望带宽（Mbps），内部转为 kB/s（×128）比较。

**结果 JSON：**

```json
{
  "ip": "1.2.3.4",
  "port": 443,
  "bandwidth": 5,
  "realBandwidth": 8,
  "maxSpeed": 1024,
  "latencyMs": 45,
  "dc": "SJC",
  "city": "San Jose",
  "region": "North America",
  "elapsed": 32,
  "error": ""
}
```

## Android 客户端

### 功能

- **扫描**：配置带宽目标，一键扫描优选 IP
- **进度**：实时显示扫描进度（500ms 轮询）
- **结果**：结构化展示 IP、带宽、延迟、数据中心等
- **历史**：保存最近 10 条扫描记录，支持删除
- **设置**：配置 API 地址和 Key，支持连接测试
- **主题**：跟随系统 / 浅色 / 深色

### 构建 APK

需要 Go、gomobile、JDK 17+、Android SDK/NDK、Gradle：

```bash
# 1. gomobile 生成 AAR
cd core
gomobile init
gomobile bind -target=android -androidapi 24 \
  -javapkg com.cftransit.app -trimpath \
  -ldflags="-s -w" \
  -o ../android/app/libs/cftransit.aar .

# 2. Gradle 打包 APK
cd ../android
# 确保 local.properties 中 sdk.dir 指向 Android SDK 路径
gradle assembleDebug
```

输出位于 `android/app/build/outputs/apk/debug/`，包含各 ABI 分包和 universal 包。

## 许可证

本项目仅供学习参考。
