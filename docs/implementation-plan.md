# 多人协作系统实施计划

## 背景
根据 `docs/collab-system.md` 架构设计文档，开发一个类似 Google Docs 的实时协作系统。采用 Spring Boot + React 技术栈，核心使用 Yjs (CRDT) 进行冲突解决，WebSocket + STOMP 进行实时同步。

## 实施阶段

### Phase 1: 项目基础搭建

#### 1.1 创建项目结构
- 创建 `backend/` 目录（Spring Boot 3.x, Java 17+）
- 创建 `frontend/` 目录（React 18 + TypeScript + Vite）
- 配置 Maven/Gradle 依赖管理

#### 1.2 本地开发环境
- 编写 `docker-compose.yml`（Redis、PostgreSQL、MongoDB）
- 启动基础设施容器
- 验证数据库连接

### Phase 2: 后端核心实现

#### 2.1 基础配置
- Spring Boot 项目初始化
- `application.yml` 配置（Redis、PostgreSQL、MongoDB）
- 日志配置 `logback-spring.xml`

#### 2.2 WebSocket + STOMP
- `WebSocketConfig.java` - STOMP 端点配置
- `JwtHandshakeInterceptor.java` - WebSocket 握手 JWT 验证
- `JwtChannelInterceptor.java` - 消息通道 Token 校验

#### 2.3 安全模块
- `JwtTokenProvider.java` - JWT 生成与验证
- `SecurityConfig.java` - Spring Security 配置
- 用户认证流程（OAuth2 可选）

#### 2.4 协作服务
- `CollabWebSocketController.java` - WebSocket 消息处理
- `RedisBroadcastService.java` - Redis Pub/Sub 跨实例广播
- `CollabEventService.java` - 增量事件与快照管理

### Phase 3: 前端核心实现

#### 3.1 项目初始化
- Vite + React + TypeScript 项目创建
- 安装依赖（Yjs、y-websocket、y-indexeddb、@tiptap/react）
- 基础组件架构

#### 3.2 实时协作
- `useYjsDoc.ts` - Yjs Doc 初始化与 WebSocket 连接
- `Editor.tsx` - TipTap 富文本编辑器 + Yjs 集成
- 离线支持（IndexedDB）

#### 3.3 状态管理
- **Zustand** 配置
- 认证状态管理

### Phase 4: 数据持久化

#### 4.1 数据模型（混合存储方案）
- `Document` - 文档元信息（PostgreSQL）
- `DocumentSnapshot` - 完整快照（PostgreSQL）
- `CollabEvent` - 增量事件日志（MongoDB）

#### 4.2 快照服务
- 定时任务生成快照（每5分钟）
- 增量事件持久化到 MongoDB
- 完整快照写入 PostgreSQL

#### 4.3 历史版本
- API: `GET /docs/{id}/snapshot?version=n`
- 版本回滚功能

### Phase 5: 权限与安全

#### 5.1 文档权限
- `PermissionService.java` - 权限校验
- READ/WRITE 权限控制
- 权限变更即时推送

#### 5.2 审计
- 操作日志记录（userId、timestamp）
- 审计查询接口

### Phase 6: 部署配置

#### 6.1 Docker 镜像
- Backend Dockerfile
- Frontend Dockerfile (nginx)

#### 6.2 K8s 配置
- `k8s/api-deployment.yaml`
- `k8s/web-deployment.yaml`
- `k8s/redis.yaml`、`postgres.yaml`、`mongo.yaml`

#### 6.3 CI/CD
- GitHub Actions 工作流

### Phase 7: 监控运维

#### 7.1 监控
- Prometheus + Grafana 配置
- 关键指标：`ws_connections`、`event_queue_size`

#### 7.2 日志
- ELK Stack 配置

## 关键里程碑

| 阶段 | 里程碑 |
|------|--------|
| Phase 1 | 本地环境启动成功 |
| Phase 2 | WebSocket 连接正常 |
| Phase 3 | 两人编辑实时同步 |
| Phase 4 | 快照保存与恢复正常 |
| Phase 5 | 权限控制生效 |
| Phase 6 | K8s 部署成功 |
| Phase 7 | 监控面板可用 |

## 验证方式
1. 运行 `docker-compose up -d` 启动基础设施
2. 启动后端 `mvn spring-boot:run`
3. 启动前端 `npm run dev`
4. 打开两个浏览器窗口，编辑同一文档验证实时同步
5. 断开网络后编辑，恢复后验证同步

## 项目结构

```
/collab-system
│
├─ backend/                      # Spring Boot 项目
│   ├─ src/main/java/com/example/
│   │   ├─ config/               # WebSocket, Redis, Security 配置
│   │   ├─ controller/            # REST / STOMP 接口
│   │   ├─ service/               # 业务层
│   │   ├─ domain/                 # JPA / Mongo 实体
│   │   ├─ repository/            # 数据仓库
│   │   ├─ security/              # JWT, Interceptors
│   │   └─ util/                  # 工具类
│   └─ src/main/resources/
│       ├─ application.yml
│       └─ logback-spring.xml
│
├─ frontend/                     # React 项目
│   ├─ src/
│   │   ├─ components/           # Editor, Toolbar
│   │   ├─ collab/              # Yjs 初始化
│   │   ├─ pages/                # Login, DocumentList, EditorPage
│   │   ├─ hooks/                # 自定义 Hooks
│   │   ├─ services/             # API client
│   │   └─ store/                # Zustand 状态管理
│   ├─ public/
│   └─ vite.config.ts
│
├─ docker-compose.yml
├─ Dockerfile.backend
├─ Dockerfile.frontend
└─ docs/
    └─ collab-system.md           # 架构设计文档
```

## 技术栈

| Layer | Technology |
|-------|------------|
| Frontend | React 18 + TypeScript + Vite, Zustand, Yjs (CRDT), WebSocket + STOMP, TipTap |
| Backend | Spring Boot 3.x (Java 17+), Spring Security + JWT, WebSocket + STOMP |
| Real-time | Redis Pub/Sub for cross-instance broadcast |
| Persistence | PostgreSQL (documents/snapshots), MongoDB (incremental events) |
| Deployment | Docker + Docker-Compose / Kubernetes |

## 已实现功能

### 后端 (Phase 2)
- [x] Spring Boot 项目初始化
- [x] WebSocket + STOMP 配置
- [x] JWT 认证 (Token 生成/验证)
- [x] WebSocket 握手拦截器
- [x] STOMP 消息通道拦截器
- [x] Spring Security 配置
- [x] Redis 配置与 Pub/Sub 广播
- [x] Document 实体 (PostgreSQL)
- [x] DocumentSnapshot 实体 (PostgreSQL)
- [x] CollabEvent 实体 (MongoDB)
- [x] REST API (文档 CRUD)
- [x] Auth API (登录/注册)
- [x] WebSocket 协作控制器
- [x] 定时快照任务

### 前端 (Phase 3)
- [x] Vite + React + TypeScript 项目
- [x] TipTap 编辑器集成
- [x] Yjs 实时同步
- [x] IndexedDB 离线支持
- [x] Zustand 状态管理
- [x] 登录页面
- [x] 文档列表页面
- [x] 协作编辑器页面
- [x] Docker 构建配置

### 基础设施 (Phase 1)
- [x] docker-compose.yml (Redis, PostgreSQL, MongoDB)
- [x] Backend Dockerfile
- [x] Frontend Dockerfile
- [x] Nginx 配置

## 待实现功能

### Phase 4: 数据持久化
- [ ] 完整的快照生成逻辑（使用 Yjs Java 库合并更新）
- [ ] 历史版本 API 完善
- [ ] 增量事件清理策略

### Phase 5: 权限与安全
- [ ] PermissionService 权限校验
- [ ] 权限变更实时推送
- [ ] 操作审计日志

### Phase 6: 部署配置
- [ ] Kubernetes 部署配置
- [ ] GitHub Actions CI/CD

### Phase 7: 监控运维
- [ ] Prometheus + Grafana 监控
- [ ] ELK 日志聚合

## 启动指南

### 1. 启动基础设施
```bash
docker-compose up -d
```

### 2. 启动后端
```bash
cd backend
./mvnw spring-boot:run
# 或使用 Maven wrapper
mvn spring-boot:run
```

### 3. 启动前端
```bash
cd frontend
npm install
npm run dev
```

### 4. 验证
1. 打开浏览器访问 http://localhost:3000
2. 登录后创建新文档
3. 打开另一个浏览器窗口，访问同一文档
4. 在两个窗口中编辑，验证实时同步
5. 断开网络后编辑，恢复后验证同步
