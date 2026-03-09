## 多人协作系统（Spring Boot + React）整体架构设计

下面给出一个 **可落地、易扩展** 的参考方案，涵盖：

1. **系统分层**（前端 / 后端 / 基础设施）  
2. **关键技术选型**（实时同步、冲突解决、权限、持久化、伸缩）  
3. **核心模块划分**、**数据流**、**接口设计**  
4. **关键代码示例**（WebSocket、CRDT、鉴权、事务）  
5. **部署/运维**（容器化、CI/CD、监控）  
6. **常见问题 & 选型建议**  

> **说明**：本方案以 **文档/白板类** 协作为例（如 Google Docs、Miro），但同样适用于任务板、代码编辑器等，只需要在业务层替换对应的数据模型即可。

---

## 1. 系统分层与技术栈概览

| 层级         | 主要职责                          | 技术选型（推荐）                                             | 备注                                                         |
| ------------ | --------------------------------- | ------------------------------------------------------------ | ------------------------------------------------------------ |
| **前端**     | UI、交互、实时渲染                | React 18 + TypeScript + Vite/CRA <br> UI库: Ant Design / Material UI <br> 状态管理: Zustand / Redux Toolkit <br> 实时层: **WebSocket + STOMP** 或 **WebRTC DataChannel** <br> 冲突解决: **Yjs**（CRDT） | React 负责视图，Yjs 与 WebSocket 负责协作层。                |
| **后端**     | API、业务、实时消息、权限、持久化 | Spring Boot 3.x (Java 17+) <br> **Spring WebFlux**（响应式）或 **Spring MVC**（简化） <br> **Spring Security + JWT** <br> **Spring Data JPA / MyBatis** <br> **WebSocket (SockJS + STOMP)** <br> **Redis**（Pub/Sub、分布式锁、缓存） <br> **Kafka / Pulsar**（可选，跨实例消息） | 推荐使用 **WebFlux** 搭配 **RSocket**，可以更自然地与前端的响应式流对接。 |
| **持久化**   | 长久存储、快照、历史版本          | PostgreSQL（关系）<br>MongoDB（文档）<br>**CouchDB**（内置 MVCC，适合冲突合并）<br>**ElasticSearch**（全文搜索） | 根据业务选择：结构化数据用关系库，协作文档建议用 **MongoDB** + **CouchDB** 的混合方案。 |
| **协作引擎** | 实时同步、冲突解决                | **Yjs**（CRDT）<br>或 **OT（Operational Transformation）**（如 ShareDB）<br>**Redis Streams** / **Kafka**（跨实例广播） | Yjs 在前端实现 CRDT，后端仅负责中转与持久化，简化实现难度。  |
| **基础设施** | 容器、负载均衡、监控、日志        | Docker + Docker‑Compose / Kubernetes <br> Nginx/Traefik 反向代理 + **Ingress** <br> **Prometheus + Grafana** 监控 <br> **ELK**（日志） <br> **Istio / Linkerd**（服务网格，流量控制） | 生产推荐用 K8s，Dev 环境可直接 Docker‑Compose。              |

---

## 2. 关键模块划分（微服务视角）

```mermaid
graph TD
    subgraph Frontend[React 前端]
        UI[UI 组件] --> State[状态管理 (Zustand/Redux)]
        State --> WS[WebSocket / WebRTC] --> Yjs[CRDT 引擎]
        UI --> API[REST / GraphQL]
    end

    subgraph Gateway[API 网关（可选）]
        GW[Spring Cloud Gateway] --> Auth[鉴权过滤器]
    end

    subgraph AuthService[用户/鉴权服务]
        AuthSrv[Spring Security + JWT + OAuth2]
        AuthSrv --> DB_User[(User DB)]
    end

    subgraph Collaboration[协作服务]
        CollabWS[WebSocket / STOMP] --> RedisPubSub[(Redis Pub/Sub)]
        CollabWS --> CollabHandler[业务处理（保存快照、事件持久化）]
        CollabHandler --> DB_Doc[(Document DB: PostgreSQL/MongoDB)]
    end

    subgraph History[历史 & 版本服务]
        VersionSrv[版本管理（增量快照）] --> DB_Version[(Version DB)]
    end

    subgraph Search[搜索服务]
        SearchSrv[ElasticSearch] --> DB_Search[(ES Index)]
    end

    subgraph Notification[通知服务]
        NotifySrv[Kafka / RabbitMQ] --> WS_Notify[WebSocket 通知]
    end

    Frontend --> GW
    GW --> AuthSrv
    GW --> CollabWS
    GW --> VersionSrv
    GW --> SearchSrv
    GW --> NotifySrv
```

### 2.1 前端协作流

1. **初始化**  
   - React 组件挂载后通过 **Axios** 拉取文档基础信息（标题、创建者、权限、最新快照 ID）。  
   - 同时创建 **Yjs Doc** 实例，读取本地缓存（IndexedDB）或从服务器加载历史快照（`GET /docs/{id}/snapshot?version=n`）。

2. **实时同步**  
   - 前端打开 **WebSocket (STOMP)** 连接 `ws://api.example.com/collab/{docId}`。  
   - Yjs 使用 **WebSocketProvider**（内部基于 **ws**）把本地操作（Insert、Delete、Format 等）以 **Binary CRDT update** 形式发送。  
   - 服务器把更新广播到同一 `docId` 订阅的所有实例（Redis Pub/Sub → 多节点转发）。  

3. **冲突解决**  
   - Yjs 基于 **CRDT** 自动合并，保证 **强一致性**（最终状态相同），无需服务器做乐观锁或 OT。  

4. **持久化**  
   - 服务器端 **CollabHandler** 将每条更新写入 **MongoDB**（增量 event）并定时生成 **快照**（完整文档 JSON）写入 **PostgreSQL**（或同一 Mongo 集合）。  
   - 前端可在离线时继续编辑，等网络恢复后自动发送本地缓存的更新。

5. **版本/历史**  
   - 每次快照（如每 5 分钟或每 100 条操作）触发 **VersionSrv** 保存为 **增量 Diff**，并在 UI 中提供“回滚”或“查看历史”功能。  

6. **权限/安全**  
   - **JWT** 随每条 WebSocket 消息携带，后端 **STOMP interceptor** 校验，确定用户拥有对 `docId` 的 `READ/WRITE` 权限。  

### 2.2 后端核心业务流程（伪代码）

```java
// 1. WebSocket 入口（Spring WebSocket + STOMP）
@MessageMapping("/collab/{docId}")
@SendTo("/topic/collab/{docId}")
public byte[] handleUpdate(@DestinationVariable String docId,
                           @Payload byte[] yjsUpdate,
                           Principal principal) {
    // ① 权限校验
    if (!authService.canEdit(principal.getName(), docId)) {
        throw new AccessDeniedException();
    }

    // ② 持久化增量（Event Store）
    collabEventService.appendEvent(docId, principal.getName(), yjsUpdate);

    // ③ 广播到其它节点（Redis Pub/Sub）
    redisTemplate.convertAndSend("collab:" + docId, yjsUpdate);

    // ④ 返回给发送者（可选回执）
    return yjsUpdate;
}
```

```java
// 2. Redis Pub/Sub Listener（跨实例同步）
@Component
public class CollabRedisListener {

    @Autowired private SimpMessagingTemplate messagingTemplate;

    @Scheduled(fixedDelay = 200)   // 防止频繁发送小包，做一次批量聚合
    public void listen() {
        // 每个 docId 对应一个 Redis channel "collab:{docId}"
        redisMessageListenerContainer.addMessageListener(
            (message, pattern) -> {
                String channel = new String(message.getChannel());
                String docId = channel.split(":")[1];
                byte[] update = message.getBody();
                // 推送到本地 WebSocket Session
                messagingTemplate.convertAndSend("/topic/collab/" + docId, update);
            },
            new ChannelTopic("collab:*")
        );
    }
}
```

```java
// 3. 定时快照任务（每 5 分钟或 500 条操作）
@Scheduled(cron = "0 */5 * * * *")
public void generateSnapshot() {
    // 查询最近 N 条未快照的增量
    List<CollabEvent> events = collabEventService.fetchPendingEvents();

    // 合并到 Yjs Doc（服务器端也可以使用 Yjs Java 实现或转交给前端生成快照）
    YDoc ydoc = new YDoc();
    events.forEach(e -> ydoc.applyUpdate(e.getUpdate()));

    // 持久化完整文档
    documentRepository.saveSnapshot(ydoc.encodeStateAsUpdateV2());

    // 标记事件已快照
    collabEventService.markSnapshotted(events);
}
```

> **关键点**  
> - **CRDT** 不需要锁或冲突检测，业务层只负责 **持久化** 与 **权限**。  
> - **Redis Pub/Sub** 负责 **跨实例广播**，如果要保证 **强一致性**（如消息不丢），可换成 **Kafka** 并开启 **事务式生产者 + Consumer Group**。  
> - **快照** 频率需根据文档大小与编辑频率调优，兼顾 **恢复速度** 与 **存储成本**。

---

## 3. 数据模型（示例）

### 3.1 Document（MongoDB / PostgreSQL）

| 字段               | 类型      | 说明                                   |
| ------------------ | --------- | -------------------------------------- |
| `id`               | UUID      | 主键                                   |
| `title`            | String    | 文档标题                               |
| `ownerId`          | UUID      | 创建者                                 |
| `createdAt`        | Timestamp | 创建时间                               |
| `updatedAt`        | Timestamp | 最近编辑时间                           |
| `permission`       | JSONB     | {userId: "READ/WRITE", groupId: "..."} |
| `latestSnapshotId` | UUID      | 指向 `DocumentSnapshot`                |
| `metadata`         | JSON      | 业务自定义信息（标签、共享链接等）     |

### 3.2 DocumentSnapshot（PostgreSQL）

| 字段           | 类型      | 说明                     |
| -------------- | --------- | ------------------------ |
| `id`           | UUID      | 主键                     |
| `docId`        | UUID      | 所属文档                 |
| `version`      | BIGINT    | 单调递增                 |
| `snapshotData` | BYTEA     | 完整的 Yjs state（压缩） |
| `createdAt`    | Timestamp | 生成时间                 |
| `sizeBytes`    | BIGINT    | 快照大小（用于清理策略） |

### 3.3 CollabEvent（MongoDB - 增量日志）

| 字段          | 类型     | 说明               |
| ------------- | -------- | ------------------ |
| `_id`         | ObjectId | 主键               |
| `docId`       | UUID     | 所属文档           |
| `userId`      | UUID     | 操作者             |
| `update`      | Binary   | Yjs update（增量） |
| `timestamp`   | ISODate  | 产生时间           |
| `snapshotted` | Boolean  | 是否已合并进快照   |

> **清理策略**：保留最近 **N** 个快照 + 最近 **M** 天的增量日志，旧日志可归档到对象存储（如 MinIO / S3）。

---

## 4. 实时协作技术细节对比

| 技术                                | 原理                                         | 优点                                                         | 缺点                                     | 适用场景                        |
| ----------------------------------- | -------------------------------------------- | ------------------------------------------------------------ | ---------------------------------------- | ------------------------------- |
| **CRDT (Yjs, Automerge)**           | 状态在每个副本本地演化，基于数学可交换合并   | 无需中心服务器冲突检测、离线编辑天然支持、实现简单（前端库已成熟） | 增量体积随编辑次数增长（但可压缩）       | 文档、白板、绘图、代码编辑      |
| **OT (Operational Transformation)** | 将操作转换到当前状态，中心服务器记录变换函数 | 对文本编辑优化好，历史记录可直接回放                         | 实现复杂，必须保持全局顺序，服务器压力大 | 传统富文本编辑器（如 Etherpad） |
| **WebRTC DataChannel**              | 点对点传输+信令服务器                        | 超低延迟、节省服务器带宽                                     | NAT/防火墙穿透难，需额外信令与TURN服务器 | 极限实时（白板、多人游戏）      |
| **WebSocket + STOMP**               | 双向长连接 + 主题/订阅模型                   | 成熟、易与 Spring 整合、可做可靠投递                         | 单点瓶颈，需要水平扩展 (Redis/Kafka)     | 大多数协作业务的首选            |

> **推荐**：**前端使用 Yjs + WebSocket**，后端仅做 **转发 + 持久化**，这样可以把冲突解决的复杂度从后端移到前端，降低服务端业务难度。

---

## 5. 安全、鉴权与审计

| 场景               | 实现方式                                                     |
| ------------------ | ------------------------------------------------------------ |
| **用户登录**       | Spring Security OAuth2（支持 Google、GitHub） + JWT（accessToken 15min，refreshToken 30d） |
| **WebSocket 鉴权** | 客户端在 `connect` 时把 `Authorization: Bearer <jwt>` 放在`stompHeaders`，后端 `ChannelInterceptor` 校验。 |
| **文档权限**       | `Document.permission`（JSON）+ `PermissionService`（基于 RBAC）<br>→ 每次 `JOIN` / `SEND` 都检查 `READ/WRITE` |
| **操作审计**       | `CollabEvent` 中记录 `userId、timestamp、updateHash`，可通过 Kafka Connect 同步到审计库（ClickHouse）做报表。 |
| **防止滥用**       | - Rate Limiting：Spring Cloud Gateway + Redis `token bucket` <br>- 内容过滤：对富文本使用 `OWASP HTML Sanitizer` <br>- 频率监控：Prometheus `http_requests_total` + `alertmanager` |

---

## 6. 部署与运维方案

### 6.1 Docker‑Compose（本地开发）

```yaml
version: "3.9"
services:
  redis:
    image: redis:7-alpine
    ports: ["6379:6379"]
  postgres:
    image: postgres:15-alpine
    environment:
      POSTGRES_USER: app
      POSTGRES_PASSWORD: secret
      POSTGRES_DB: collab
    ports: ["5432:5432"]
  mongo:
    image: mongo:6
    ports: ["27017:27017"]
  api:
    build: ./backend
    environment:
      - SPRING_PROFILES_ACTIVE=dev
      - REDIS_HOST=redis
      - POSTGRES_URL=jdbc:postgresql://postgres:5432/collab
      - MONGO_URI=mongodb://mongo:27017/collab
    ports: ["8080:8080"]
    depends_on: [redis, postgres, mongo]
  web:
    build: ./frontend
    ports: ["3000:80"]
    depends_on: [api]
```

### 6.2 Kubernetes（生产）

| 资源                                 | 关键配置                                                     |
| ------------------------------------ | ------------------------------------------------------------ |
| **Deployment**                       | `replicas: ≥3`，使用 **Liveness/Readiness Probe**（/actuator/health） |
| **Service**                          | `api` → **ClusterIP** + **Ingress**（TLS）<br>`ws` → **NodePort** / **LoadBalancer**（启用 `proxy_set_header Upgrade $http_upgrade;`） |
| **StatefulSet** (MongoDB)            | 持久化卷 `storageClass: fast-ssd`                            |
| **Redis Sentinel/Cluster**           | 高可用，连接字符串 `redis://my-redis:26379`                  |
| **Istio**                            | `VirtualService` → `gateway`，`DestinationRule` 开启 **circuit‑breaker** |
| **Horizontal Pod Autoscaler**        | 基于 **CPU 70%** 或 **WebSocket 连接数**（自定义指标）       |
| **ConfigMap / Secret**               | 存放 `jwt.secret`、数据库凭证、OAuth 客户端 ID/Secret        |
| **PrometheusOperator** + **Grafana** | 自动抓取 `spring_boot_metrics`、`node_exporter`、`nginx_ingress_controller` |
| **ELK**                              | Logstash 收集 `stdout`，Kibana 做日志搜索                    |

---

## 7. 关键代码、配置示例

### 7.1 前端 – Yjs + WebSocketProvider

```tsx
// src/collab/useYjsDoc.ts
import * as Y from 'yjs';
import { WebsocketProvider } from 'y-websocket';
import { useEffect, useState } from 'react';
import { IndexeddbPersistence } from 'y-indexeddb';
import { useAuth } from '@/hooks/useAuth';

export const useYjsDoc = (docId: string) => {
  const { token } = useAuth();
  const [ydoc] = useState(() => new Y.Doc());

  useEffect(() => {
    // 1️⃣ 持久化到 IndexedDB（离线） 
    const indexeddb = new IndexeddbPersistence(docId, ydoc);
    indexeddb.whenSynced.then(() => console.log('Indexes synced'));

    // 2️⃣ WebSocket 连接（带 JWT 参数） 
    const wsProvider = new WebsocketProvider(
      `${import.meta.env.VITE_WS_ENDPOINT}/collab/${docId}`,
      docId,
      ydoc,
      {
        // 通过 params 传递 token，后端拦截验证
        params: { token },
        // 自动重连
        retryDelay: 2000,
      }
    );

    wsProvider.on('status', (event: { status: string }) => {
      console.info('WebSocket connection status:', event.status);
    });

    return () => {
      wsProvider.destroy();
      indexeddb.destroy();
    };
  }, [docId, token, ydoc]);

  return ydoc;
};
```

> **要点**：  
> - `WebsocketProvider` 把 **Yjs update** 自动序列化为 **binary**，压缩率约 80% ~ 90%。  
> - `IndexeddbPersistence` 让用户在 **离线编辑** 完全无感，恢复时自动同步。

### 7.2 前端 – 富文本编辑器 (TipTap) 与 Yjs

```tsx
// src/components/Editor.tsx
import { EditorContent, useEditor } from '@tiptap/react';
import StarterKit from '@tiptap/starter-kit';
import Collaboration from '@tiptap/extension-collaboration';
import { useYjsDoc } from '@/collab/useYjsDoc';

export const Editor = ({ docId }: { docId: string }) => {
  const ydoc = useYjsDoc(docId);
  const yXmlFragment = ydoc.getXmlFragment('tiptap');

  const editor = useEditor({
    extensions: [
      StarterKit,
      Collaboration.configure({
        document: ydoc,
        fragment: yXmlFragment,
      }),
    ],
    content: '',
  });

  return <EditorContent editor={editor} />;
};
```

> **TipTap** 已内置 `Collaboration` 扩展，可直接绑定 Yjs。这样所有富文本动作（加粗、列表、图片）都会走同一 CRDT 流。

### 7.3 Spring Boot – WebSocket + STOMP 配置

```java
// src/main/java/com/example/config/WebSocketConfig.java
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // broker 用 redis 的 simpleMessageBroker 进行跨实例广播
        registry.enableSimpleBroker("/topic")
                .setHeartbeatValue(new long[]{10000, 10000}); // 心跳
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 支持 SockJS fallback
        registry.addEndpoint("/collab/{docId}")
                .setAllowedOriginPatterns("*")
                .addInterceptors(new JwtHandshakeInterceptor())
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new JwtChannelInterceptor()); // 读取 token 并放入 Principal
    }
}
```

`JwtHandshakeInterceptor` / `JwtChannelInterceptor` 负责把 JWT 解析成 `Principal`，代码略。

### 7.4 Spring Security – JWT

```java
// src/main/java/com/example/security/JwtTokenProvider.java
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String secretKey;

    private final long validityInMs = 15 * 60 * 1000; // 15min

    public String createToken(String userId, List<String> roles) {
        Claims claims = Jwts.claims().setSubject(userId);
        claims.put("roles", roles);
        Date now = new Date();
        Date exp = new Date(now.getTime() + validityInMs);
        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(now)
                .setExpiration(exp)
                .signWith(SignatureAlgorithm.HS256, secretKey)
                .compact();
    }

    public Authentication getAuthentication(String token) {
        UserDetails userDetails = loadUserDetails(getUserId(token));
        return new UsernamePasswordAuthenticationToken(userDetails, "", userDetails.getAuthorities());
    }

    public String getUserId(String token) {
        return Jwts.parser().setSigningKey(secretKey)
                .parseClaimsJws(token).getBody().getSubject();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().setSigningKey(secretKey).parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
```

> **WebSocket**：在 `JwtChannelInterceptor` 中调用 `JwtTokenProvider.validateToken`，若通过则 `setUser` 为 `Principal`.

### 7.5 Redis Pub/Sub 统一广播（跨实例）

```yaml
# src/main/resources/application.yml
spring:
  redis:
    host: ${REDIS_HOST:localhost}
    port: 6379
    password: ${REDIS_PASSWORD:}
    client-type: lettuce
  # 用于 Spring Messaging 通配符 channel
  messaging:
    redis:
      enabled: true
```

```java
// src/main/java/com/example/collab/RedisBroadcastService.java
@Service
@RequiredArgsConstructor
public class RedisBroadcastService {
    private final StringRedisTemplate redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;

    public void broadcastUpdate(String docId, byte[] update) {
        // 推送到所有 redis 订阅者
        redisTemplate.convertAndSend("collab:" + docId, update);
    }

    @EventListener
    public void onMessage(Message<byte[]> message) {
        String channel = new String(message.getMessageProperties().getReceivedRoutingKey());
        String docId = channel.substring("collab:".length());
        byte[] update = message.getBody();
        messagingTemplate.convertAndSend("/topic/collab/" + docId, update);
    }
}
```

> **说明**：如果业务对 **消息不丢失** 有强需求，可将 `Redis` 替换成 **Kafka**，把 `broadcastUpdate` 改成 `KafkaTemplate.send(topic, update)`。Kafka 支持 **Exactly‑once**（结合事务）并提供持久化。

### 7.6 快照/增量持久化（Mongo + PostgreSQL 示例）

```java
// src/main/java/com/example/collab/CollabEventService.java
@Service
@RequiredArgsConstructor
public class CollabEventService {
    private final MongoTemplate mongo;
    private final DocumentSnapshotRepository snapshotRepo; // JPA

    // 保存单条增量
    public void appendEvent(String docId, String userId, byte[] update) {
        CollabEvent event = new CollabEvent();
        event.setDocId(docId);
        event.setUserId(userId);
        event.setUpdate(update);
        event.setTimestamp(Instant.now());
        mongo.insert(event);
    }

    // 定时合并为快照
    @Scheduled(cron = "0 */5 * * * *") // 每5分钟
    @Transactional
    public void generateSnapshots() {
        // 1️⃣ 找到未合并的事件
        List<CollabEvent> pending = mongo.find(
                Query.query(Criteria.where("snapshotted").is(false)),
                CollabEvent.class);

        // 2️⃣ 合并到 Yjs Doc (使用 Java 实现的 Yjs 官方库 yjs-java)
        YDoc ydoc = new YDoc();
        pending.forEach(e -> ydoc.applyUpdate(e.getUpdate()));

        // 3️⃣ 生成快照（压缩）
        byte[] snapshot = ydoc.encodeStateAsUpdate();

        // 4️⃣ 存入 PostgreSQL
        DocumentSnapshot snap = new DocumentSnapshot();
        snap.setDocId(pending.get(0).getDocId());
        snap.setVersion(snapVersionGenerator.next()); // 单调递增
        snap.setSnapshotData(snapshot);
        snap.setCreatedAt(Instant.now());
        snapshotRepo.save(snap);

        // 5️⃣ 标记事件已快照
        pending.forEach(e -> e.setSnapshotted(true));
        mongo.bulkOps(BulkOperations.BulkMode.UNORDERED, CollabEvent.class)
                .replaceAll(pending)
                .execute();
    }
}
```

> **Yjs Java**：官方提供 `org.yjs:yjs`（目前仍在维护），可在后台直接使用 CRDT 合并。若不想引入 Java 实现，可把快照生成的任务**交给前端**（每次用户提交时前端发送完整快照），后端只负责持久化。

---

## 8. 常见问题 & 优化建议

| 场景                     | 可能的瓶颈                               | 解决思路                                                     |
| ------------------------ | ---------------------------------------- | ------------------------------------------------------------ |
| **大量并发编辑**         | WebSocket 消息频率 ↑、Redis Pub/Sub 带宽 | 1) 使用 **二进制压缩**（Yjs 已自带）<br>2) 引入 **消息聚合**（每 50 ms 合并一次）<br>3) 部署 **水平扩容**（Ingress + HPA） |
| **文档体积大（>10 MB）** | 快照/增量体积爆炸、前端渲染卡顿          | - 将文档拆分为 **块（Chunk）**，每块单独 CRDT（如 Yjs `Y.Map`）<br>- 对大图片采用 **CDN** + **懒加载** |
| **离线冲突恢复**         | 断网期间本地大量更新，恢复后大量 merge   | - 利用 **Yjs undo/redo** 保持操作序列<br>- 快照频率适当提升（每 2 min） |
| **安全审计**             | 需要查询历史谁改了什么                   | - 将 `CollabEvent.update` 做 **SHA256** 哈希存入审计库<br>- 使用 **ClickHouse** 做高效查询 |
| **跨地域合作**           | 延迟 ↑、网络抖动                         | - 在每个区域部署 **边缘 Redis**（Geo‑replication）<br>- 采用 **WebRTC** + **TURN** 进行 P2P 直接传输（仅增量） |
| **权限变更即时生效**     | 老的 WebSocket 仍能操作                  | - 在 `PermissionService` 中维持 **版本号**，每次变更推送到对应 `docId` 的 **系统通道** `/topic/perm/{docId}`，客户端收到后主动 **关闭** 或 **重新授权**。 |

---

## 9. 项目结构（示例）

```
/collab-system
│
├─ backend/                     # Spring Boot 项目
│   ├─ src/main/java/com/example/
│   │   ├─ config/               # WebSocket, Redis, Security 配置
│   │   ├─ controller/            # REST / STOMP 接口
│   │   ├─ service/               # 业务层（CollabEventService, PermissionService）
│   │   ├─ domain/                 # JPA / Mongo 实体
│   │   ├─ security/              # JWT, Interceptors
│   │   └─ util/                  # Yjs 合并工具、压缩工具
│   └─ src/main/resources/
│       ├─ application.yml
│       └─ logback-spring.xml
│
├─ frontend/                    # React 项目
│   ├─ src/
│   │   ├─ components/          # Editor, Toolbar, PresenceList
│   │   ├─ collab/              # Yjs 初始化、WebSocketProvider
│   │   ├─ pages/               # DocumentPage, Dashboard
│   │   ├─ hooks/               # useAuth, usePresence
│   │   └─ services/            # API client (axios)
│   ├─ public/
│   └─ vite.config.ts
│
├─ docker-compose.yml
├─ k8s/
│   ├─ api-deployment.yaml
│   ├─ web-deployment.yaml
│   ├─ redis.yaml
│   ├─ postgres.yaml
│   └─ mongo.yaml
└─ README.md
```

---

## 10. 小结 & 下一步行动计划

| 步骤                | 目标                                            | 关键里程碑                                                   |
| ------------------- | ----------------------------------------------- | ------------------------------------------------------------ |
| **1️⃣ 需求拆分**      | 明确文档类型、权限模型                          | 完成 `Document`、`User`、`Permission` ER 图                  |
| **2️⃣ 环境搭建**      | 本地 Docker‑Compose 启动所有组件                | `docker-compose up -d`，前后端可访问                         |
| **3️⃣ 基础协作实现**  | Yjs + WebSocket 双向实时同步                    | - 前端编辑器联通 `ws://localhost:8080/collab/{docId}`<br>- 能看到多人光标、编辑同步 |
| **4️⃣ 持久化 & 快照** | 增量日志写入 Mongo，快照写入 PostgreSQL         | 定时任务生成快照、能够通过 API 拉取历史版本                  |
| **5️⃣ 权限 & 鉴权**   | JWT + Spring Security，WebSocket 鉴权           | 验证不同用户对同一文档的 READ/WRITE 控制                     |
| **6️⃣ 部署 & 扩容**   | K8s + HPA，启用 Redis Pub/Sub 跨 Pod            | 通过 `kubectl apply -f k8s/`，验证水平扩容                   |
| **7️⃣ 监控 & 灰度**   | Prometheus + Grafana，主动报警                  | 监控 `ws_connections`, `event_queue_size`                    |
| **8️⃣ 高可用**        | 多节点 Redis、PostgreSQL 主从、Mongo ReplicaSet | 完成灾备演练（模拟节点失联）                                 |

> 当上述 **MVP（最小可行产品）** 完成后，你即可在内部进行用户测试、收集编辑延迟 & 丢包数据，再根据实际使用情况对 **快照频率、消息聚合阈值、分区策略** 进行细调。

祝项目顺利 🚀！如果有更细的业务细节（比如要实现绘图白板、代码高亮、插件系统等），欢迎继续聊，我可以再帮你扩展对应的模块和技术选型。