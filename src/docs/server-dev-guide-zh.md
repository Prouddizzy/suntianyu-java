# STM32 智能消毒机服务端开发指南（Java）

## 1. 文档用途
本文件面向服务端开发，目标是让你只看这一份文档就能完成：
- 对 Vue 提供 REST 接口
- 对 STM32（ESP-01S 透传）提供 TCP 设备网关
- 实现实时状态同步与命令下发
- 使用 Redis 作为唯一存储（当前阶段无需 MySQL）

## 2. 系统边界与职责
- Vue 前端：
  - 调用 REST 接口：启动/暂停/继续/停止/阈值设置/状态查询
  - 订阅 WebSocket（可选）接收状态推送
- Java 服务：
  - REST API 层
  - TCP 设备接入层（设备长连接）
  - 命令分发与 ACK 管理
  - Redis 状态存储
- STM32 + ESP-01S：
  - 设备状态上报
  - 执行控制命令并回 ACK
- Redis：
  - 最新状态缓存
  - 命令状态缓存
  - 可选 Pub/Sub

## 3. 技术栈建议（Java）
推荐组合（Spring 生态）：
- Spring Boot 3.x
- Spring Web（REST）
- Netty（TCP 长连接网关）
- Spring Data Redis（Lettuce）
- Jackson（JSON）
- Lombok（可选）

## 4. API 清单（与前端对齐）
基础前缀：`/api/v1`

1. `GET /disinfector/runtime-status`
2. `GET /disinfector/config`
3. `PUT /disinfector/thresholds`
4. `POST /disinfector/tasks/start`
5. `POST /disinfector/tasks/pause`
6. `POST /disinfector/tasks/stop`

统一响应：
```json
{
  "code": 0,
  "message": "ok",
  "data": {}
}
```

## 5. 关键业务规则
### 5.1 模式规则
- 加热模式：仅加热，按 duration 倒计时
- 消毒模式：仅消毒灯，按 duration 倒计时
- 智能模式：加热+消毒灯，按阈值自动控制加热/风扇，默认持续运行

### 5.2 安全规则
- `doorOpen=true` 时禁止启动（code=1002）
- pause/resume 仅对运行中任务有效

### 5.3 幂等规则
- 重复 start（运行中）返回 1003
- 空闲时 stop 返回 1004
- pause/resume 非法状态返回 1006

## 6. 设备通信协议（服务端必须支持）
传输层：TCP 长连接，UTF-8，单行 JSON + `\n` 分帧

上行（设备 -> 服务）
- `status_report`
- `ack`

下行（服务 -> 设备）
- `control_cmd`

状态上报示例：
```json
{"type":"status_report","deviceId":"cabinet-001","seq":1024,"ts":1710403200000,"temperature":31.2,"humidity":58.4,"doorOpen":false,"machineRunning":true,"paused":false,"mode":"智能模式","heaterOn":true,"disinfectionOn":true,"fanOn":false,"remainingSeconds":0,"faultCode":0}
```

控制命令示例：
```json
{"type":"control_cmd","cmdId":"c202603140001","deviceId":"cabinet-001","action":"start","mode":"智能模式","duration":20,"thresholds":{"tempLow":24,"tempHigh":34,"humidityLow":45,"humidityHigh":65}}
```

ACK 示例：
```json
{"type":"ack","cmdId":"c202603140001","deviceId":"cabinet-001","ok":true,"code":0,"message":"ok","ts":1710403201000}
```

## 7. Redis 设计（当前阶段）
### 7.1 Key 规划
- `disinfector:state:{deviceId}`（Hash）
  - 最新运行状态，接口 `/runtime-status` 直接读取
- `disinfector:config:{deviceId}`（Hash）
  - 温湿度阈值配置
- `disinfector:cmd:{cmdId}`（Hash）
  - 命令状态：pending/sent/ack/timeout
- `disinfector:last_seen:{deviceId}`（String）
  - 最近上报时间戳

### 7.2 字段建议
`state` Hash 字段：
- machineRunning, paused, selectedMode, duration, remainingSeconds
- temperature, humidity, doorOpen
- heaterOn, disinfectionOn, fanOn
- tempLow, tempHigh, humidityLow, humidityHigh
- faultCode, updatedAt

### 7.3 为什么只用 Redis 可行
- 你当前不需要历史数据
- 只关心“最新状态 + 实时控制”
- Redis 读写快，适合状态型业务

建议：开启 AOF 持久化。

## 8. 核心模块设计（代码结构建议）
建议包结构：
```text
com.project.disinfector
  ├─ controller
  │   └─ DisinfectorController
  ├─ service
  │   ├─ RuntimeStatusService
  │   ├─ CommandService
  │   ├─ ThresholdService
  │   └─ DeviceSessionService
  ├─ gateway
  │   ├─ TcpServerBootstrap
  │   ├─ DeviceChannelRegistry
  │   ├─ FrameDecoder (按\n分帧)
  │   └─ DeviceMessageHandler
  ├─ repository
  │   └─ RedisStateRepository
  ├─ model
  │   ├─ dto
  │   ├─ entity
  │   └─ enum
  └─ config
      ├─ RedisConfig
      └─ NettyConfig
```

## 9. REST 到设备命令的执行流程
以 `POST /tasks/start` 为例：
1. 校验参数（mode/duration/threshold）
2. 读取设备最新状态（Redis）
3. 校验业务状态（门开、已运行等）
4. 生成 `cmdId`
5. 写命令记录到 Redis（pending）
6. 通过设备通道下发 `control_cmd`
7. 更新命令状态为 sent
8. 等待 ACK（3~5 秒）
9. 返回结果：
   - ACK 成功：`code=0`
   - ACK 超时：返回失败或“已受理待确认”

## 10. ACK 与超时机制（必须实现）
- 每个命令都必须有 `cmdId`
- 维护 `cmdId -> CompletableFuture`（或 Promise）
- `ack` 到达后完成 Future
- 超时（3~5秒）后标记 timeout

建议策略：
- 接口可“同步等待 ACK”最多 3 秒
- 超时返回可识别错误码（如 2101）
- 前端继续轮询 runtime-status 获取最终状态

## 11. WebSocket 推送（可选但推荐）
如果要更实时：
- 服务端在处理 `status_report` 后，推送到 `/ws/disinfector/{deviceId}`
- 推送 payload 使用 runtime-status 的 data 结构

前端可先轮询，后续再切 WebSocket。

## 12. 配置项建议
```yaml
server:
  port: 8080

app:
  device:
    tcp-port: 9000
    ack-timeout-ms: 3000
    offline-timeout-ms: 10000

spring:
  data:
    redis:
      host: 127.0.0.1
      port: 6379
      database: 0
```

## 13. 最小联调验收清单
1. 设备 TCP 能稳定上线（可看到 deviceId、last_seen 更新）
2. `GET /runtime-status` 能返回真实设备最新值
3. start 命令能下发并收到 ACK
4. pause/resume 命令能切换状态并同步到前端
5. stop 命令能停止任务并状态归零
6. 更新阈值后，智能模式按新阈值生效
7. 设备断线后前端可识别离线状态

## 14. 错误码建议（服务端）
- 1001 参数非法
- 1002 门开禁止启动
- 1003 已在运行
- 1004 当前空闲
- 1005 阈值非法
- 1006 暂停/恢复状态非法
- 2001 设备离线
- 2101 命令 ACK 超时
- 2201 Redis 读写失败

## 15. 给后端 AI 的实战提示词（可直接复制）
```text
请用 Spring Boot + Netty + Redis 实现 STM32 智能消毒机后端：
- REST 接口：
  1) GET /api/v1/disinfector/runtime-status
  2) GET /api/v1/disinfector/config
  3) PUT /api/v1/disinfector/thresholds
  4) POST /api/v1/disinfector/tasks/start
  5) POST /api/v1/disinfector/tasks/pause
  6) POST /api/v1/disinfector/tasks/stop
- 设备侧通信：TCP 长连接，单行 JSON + \n 分帧，支持 status_report/control_cmd/ack
- 命令需 cmdId，3秒 ACK 超时机制
- Redis 保存最新状态与命令状态
- 返回统一 JSON: {code, message, data}
- 严格实现门开保护、模式规则、暂停继续、错误码
请输出可运行代码、配置文件、关键单元测试和集成测试样例。
```
## 16. 当前实现补充（2026-03）

### 16.1 设备参数透传

- `/runtime-status`
- `/runtime-status/recent`
- `/config`
- `/thresholds`
- `/tasks/start`
- `/tasks/pause`
- `/tasks/stop`
- `/stream`

以上接口都支持可选 `deviceId` 查询参数，未传时默认 `STM-001`。

### 16.2 WiFi 配置接口

新增：

```http
PUT /api/v1/disinfector/wifi-config?deviceId=STM-001
```

请求体：

```json
{
  "ssid": "LabWiFi",
  "password": "12345678"
}
```

下发到设备的命令格式：

```json
{"t":"cmd","c":"C002","id":"STM-001","a":"wifi","ws":"LabWiFi","wp":"12345678"}
```

返回：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "deviceId": "STM-001",
    "ssid": "LabWiFi",
    "appliedAfterRestart": true
  }
}
```

### 16.3 设备侧生效时机

- 后端收到 ACK 即认为“已成功保存到设备”
- 新 WiFi 仅在设备重启后生效
- 当前连接不断开，不强制立即切换网络
