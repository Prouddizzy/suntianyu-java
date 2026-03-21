# STM32 智能消毒机后端服务 (Java)

## 项目简介
本项目是 STM32 智能消毒机的后端服务，基于 Spring Boot 3.x + Netty + Redis 构建。
实现了与前端的 REST API 交互，以及与 STM32 设备的 TCP 长连接通信。

## 技术栈
- **核心框架**: Spring Boot 3.2.3
- **TCP网关**: Netty 4.x
- **缓存/状态**: Redis (Spring Data Redis)
- **JSON处理**: Jackson
- **构建工具**: Maven

## 快速开始

### 1. 启动 Redis
确保本地 Redis 已启动，端口 6379，无密码（或修改 `application.properties`）。

### 2. 启动后端服务
运行 `Stm32SmartDisinfectorJavaApplication.java` 的 `main` 方法。
- HTTP 服务端口: 8080
- TCP 设备端口: 9000

### 3. 运行模拟设备 (Mock Device)
为了在没有真实硬件的情况下测试，可以运行 `src/test/java/com/suntianyu/stm32smartdisinfectorjava/MockDeviceClient.java`。
它会模拟一个设备连接到 9000 端口，并定期上报状态，响应控制命令。

## API 接口说明

| 方法 | URL | 说明 |
| --- | --- | --- |
| GET | `/api/v1/disinfector/runtime-status` | 获取设备实时运行状态 |
| GET | `/api/v1/disinfector/config` | 获取温湿度阈值配置 |
| PUT | `/api/v1/disinfector/thresholds` | 更新阈值配置 |
| POST | `/api/v1/disinfector/tasks/start` | 启动任务 (需传 mode, duration) |
| POST | `/api/v1/disinfector/tasks/pause` | 暂停/恢复任务 (action: pause/resume) |
| POST | `/api/v1/disinfector/tasks/stop` | 停止任务 |

## 设备通信协议
- 格式: JSON + `\n` (换行符) 分帧
- 心跳/上报: `status_report`
- 控制下发: `control_cmd`
- 响应: `ack`

## 目录结构
```
src/main/java
  ├─ controller   // REST 接口
  ├─ service      // 业务逻辑
  ├─ gateway      // Netty TCP 服务端
  ├─ repository   // Redis 数据访问
  ├─ model        // DTO 和 Enums
  └─ config       // 配置类
```

