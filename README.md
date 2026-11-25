# AMI 用电数据查询与设备监控

用三台电表演示用电曲线查询、读数补正和设备状态监控。可以按站点当地日期查看一台表的曲线，也可以按用量范围筛选多台表的记录。

技术栈：Java 17、Spring Boot 3.5.7、MyBatis-Plus 3.5.14、MySQL 8.4、Redis、Caffeine。

## 核心流程

1. **查用电数据**。MyBatis 动态 SQL 按设备、时间窗、曲线类型和用量范围组合查询。用量指一个 15 分钟区间的输入电量（kWh）。结果按采样时间、设备和曲线类型排序，每页多取一条判断是否还有下一页。
2. **查日曲线**。按设备和站点当地日期先查 Caffeine，再查 Redis；都未命中时，将当地日期换算为 UTC 时间范围查 MySQL。Caffeine 缓存 30 秒，Redis 缓存 2 分钟，空结果仅在 Redis 保存 10 秒。
3. **补正读数**。按设备、曲线类型、采样时刻定位已有记录。更新提交后，清理该设备在采样所属日期的两层缓存。
4. **同步心跳**。同一设备按事件号去重。Spring 定时任务默认每 30 秒分批处理心跳，每批最多 200 条。更新设备状态和标记事件已处理在同一事务内完成，失败后下轮重试。

设备状态分为 `NEVER`（从未上报）、`ONLINE`（在线）和 `OFFLINE`（离线）。任务默认每 60 秒检查一次：在线设备超过 120 秒没有新心跳，且没有仍然有效的待处理心跳时，标记为离线。超时按平台接收时间计算。

## 项目结构

项目采用单模块 Spring MVC，Java 代码位于 `src/main/java/com/example/ami`。

| 目录 | 负责什么 |
| --- | --- |
| `controller`、`dto`、`enums` | HTTP 接口、请求和响应结构、曲线类型和设备状态 |
| `service` | 曲线查询、读数补正、心跳处理及事务 |
| `mapper`、`src/main/resources/mapper` | MyBatis 接口及对应的 SQL XML |
| `cache`、`job` | 日曲线缓存、心跳同步及离线检查 |
| `src/main/resources/db` | 建表、样例数据和查询校验脚本 |

## 启动

准备 JDK 17、Maven 3.9、MySQL 8.4 和 Redis。以下命令从项目根目录执行。

**1. 初始化数据库**

从项目根目录打开 MySQL 客户端，执行：

```sql
CREATE DATABASE IF NOT EXISTS ami_demo CHARACTER SET utf8mb4;
USE ami_demo;
SOURCE src/main/resources/db/01-schema.sql
SOURCE src/main/resources/db/02-sample.sql
SOURCE src/main/resources/db/03-check.sql
```

前两个脚本创建四张表并写入三台电表的样例，最后一个脚本查询结果供核对。重复导入样例会保留已补正的读数。

**2. 配置并运行**

默认连接本机 MySQL 的 `ami_demo` 库（3306）和 Redis（6379）。可在 PowerShell 中设置数据库账号，再构建并运行。其他连接参数见 [application.yml](src/main/resources/application.yml)。

```powershell
$env:AMI_DB_USERNAME = Read-Host 'MySQL 用户名'
$password = Read-Host 'MySQL 密码' -AsSecureString
$env:AMI_DB_PASSWORD = [System.Net.NetworkCredential]::new('', $password).Password

mvn clean package
java -jar .\target\ami-demo-0.0.1-SNAPSHOT.jar
```

使用 IDEA 时，在运行配置中设置相同的环境变量，启动 `AmiApplication`。

## 常用接口

默认地址为 `http://127.0.0.1:8080`。POST 和 PATCH 请求使用 `Content-Type: application/json`。

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| GET | `/actuator/health/readiness` | 检查应用、MySQL 和 Redis 是否就绪 |
| GET | `/devices/M001/daily-curves?date=2026-11-25` | 查看 M001 纽约当地当天的曲线，初始样例返回 10 条 |
| POST | `/curve-readings/search` | 跨设备分页检索，示例见下方 |
| PATCH | `/devices/M001/curve-readings` | 补正已有读数，例如 `{"curveType":"VOLTAGE_A","sampleTime":"2026-11-25T05:00:00Z","value":220.4}` |
| POST | `/devices/M003/heartbeats` | 上报心跳，例如 `{"eventId":"hb-001","reportedAt":"2026-11-25T05:15:00Z"}` |
| GET | `/devices/status?state=ONLINE&size=20` | 查询在线设备，下一页把返回的 `nextAfterId` 传给 `afterId` |
| GET | `/devices/status/summary` | 统计在线、离线和从未上报的设备数 |

**检索示例**：查询 M001 和 M002 当天区间用电量达到 1 kWh 的记录：

```json
{
  "deviceNos": ["M001", "M002"],
  "from": "2026-11-25T00:00:00-05:00",
  "to": "2026-11-26T00:00:00-05:00",
  "minKwh": 1,
  "page": 1,
  "size": 20
}
```

初始样例命中两条：M001 为 **1.25 kWh**，M002 为 **2.50 kWh**。补正接口返回 `204` 表示更新成功；心跳接口返回 `202` 表示事件已接收，设备状态在定时任务处理后更新。
