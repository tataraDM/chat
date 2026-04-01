# ChaTTE 即时通信应用 — 项目说明文档

> **课程**：面向对象技术课程实践  
> **项目名称**：ChaTTE（仿QQ/微信聊天软件）  
> **技术栈**：Java 21 + JavaFX 21 + MySQL 8.0 + TCP Socket  
> **构建工具**：Maven 3.9.6（Maven Wrapper）

---

## 一、项目概述

ChaTTE 是一个基于 C/S（客户端-服务器）架构的即时通信桌面应用程序，功能仿照 QQ/微信。服务器端使用多线程处理并发连接，通过 Java 对象序列化在 TCP Socket 上传输消息对象；客户端使用 JavaFX 构建现代化 UI，采用蓝紫色渐变配色方案。

### 核心功能一览

| 功能 | 说明 |
|------|------|
| 注册 / 登录 | SHA-256 密码加密，防重复登录 |
| 联系人列表 | 实时在线状态显示（绿●/灰○） |
| 私聊 | 气泡样式消息、实时收发 |
| 群聊 | 创建群、群消息广播、群成员标识 |
| 好友请求 | 发送/接受/拒绝，实时推送通知 |
| 删除好友 | 右键菜单，双向删除 |
| Emoji 表情 | 30 个 Unicode Emoji，弹出式网格选择器 |
| 聊天记录搜索 | 私聊/群聊关键字搜索，取消后还原完整记录 |
| 未读消息角标 | 红色数字角标，打开窗口自动清零 |
| 聊天历史 | 打开聊天窗口自动加载服务端历史记录 |

---

## 二、项目结构

```
D:\code\java\chatte\
│
├── pom.xml                          # Maven 配置（依赖、编译、插件）
├── mvnw.cmd                         # Maven Wrapper（免全局安装 Maven）
├── .mvn/wrapper/                    # Maven Wrapper 配置
│   ├── maven-wrapper.properties
│   └── maven-wrapper.jar
│
├── compile.bat                      # 一键编译脚本
├── run_server.bat                   # 启动服务器脚本
├── run_client.bat                   # 启动客户端脚本
│
├── src/                             # 源代码根目录
│   ├── common/
│   │   └── Message.java             # 通信协议：消息对象（共享）
│   │
│   ├── server/
│   │   ├── ChatServer.java          # 服务器主类：监听连接、管理在线用户
│   │   ├── ClientHandler.java       # 连接处理器：消息路由与业务逻辑
│   │   └── DatabaseManager.java     # 数据库管理器：所有 SQL 操作（单例）
│   │
│   └── client/
│       ├── Main.java                # 启动入口（绕过 JavaFX 模块检测）
│       ├── LoginFrame.java          # 登录界面（JavaFX Application）
│       ├── MainFrame.java           # 主界面：联系人列表、群聊列表、消息路由
│       ├── ChatFrame.java           # 聊天窗口：气泡、Emoji、搜索
│       └── style.css                # JavaFX CSS 样式表（全局统一样式）
│
├── target/                          # Maven 编译输出
│   └── classes/                     # 编译后的 .class 文件和资源
│
├── data/                            # 预留目录（文件存储）
└── out/                             # 旧编译输出（已弃用）
```

---

## 三、架构设计

### 3.1 整体架构图

```
┌──────────────────────────────────────────────────────────┐
│                       客户端 (JavaFX)                     │
│  ┌──────────┐   ┌──────────┐   ┌──────────────────────┐  │
│  │LoginFrame│──▶│MainFrame │──▶│ ChatFrame (多个实例)  │  │
│  │  登录页  │   │ 联系人/群 │   │ 聊天窗口(每个对话一个)│  │
│  └──────────┘   └──────────┘   └──────────────────────┘  │
│        │              │                    │               │
│        └──────────────┴────────────────────┘               │
│                        │                                   │
│                   ChatClient                               │
│              (Socket通信 + 消息监听)                        │
└────────────────────────┬───────────────────────────────────┘
                         │ TCP Socket (端口 9090)
                         │ Java 对象序列化
┌────────────────────────┴───────────────────────────────────┐
│                       服务器                                │
│  ┌──────────┐   ┌───────────────┐   ┌──────────────────┐  │
│  │ChatServer│──▶│ClientHandler  │──▶│DatabaseManager   │  │
│  │ 监听连接 │   │(每连接一线程) │   │(MySQL CRUD 单例) │  │
│  └──────────┘   └───────────────┘   └──────────────────┘  │
└────────────────────────────────────────────────────────────┘
                         │
                    ┌────┴────┐
                    │ MySQL   │
                    │ chatte  │
                    └─────────┘
```

### 3.2 通信协议

客户端和服务器之间通过 **Java 对象序列化** (`ObjectOutputStream` / `ObjectInputStream`) 传输 `Message` 对象。每个 `Message` 包含以下字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| `type` | `String` | 消息类型常量（见下表） |
| `from` | `String` | 发送方（用户名 或 `"server"`） |
| `to` | `String` | 接收方（用户名 / `groupId` / `"server"` / `"all"`） |
| `content` | `String` | 消息内容（聊天文本/逗号分隔列表/管道分隔参数等） |
| `timestamp` | `long` | 时间戳（`System.currentTimeMillis()`） |

#### 消息类型一览

| 常量名 | 方向 | content 格式 | 用途 |
|--------|------|-------------|------|
| `LOGIN` | C→S | 密码 | 登录请求 |
| `LOGIN_SUCCESS` | S→C | `"登录成功"` | 登录成功 |
| `LOGIN_FAIL` | S→C | 错误原因 | 登录失败 |
| `REGISTER` | C→S | 密码 | 注册请求 |
| `REGISTER_SUCCESS` | S→C | `"注册成功"` | 注册成功 |
| `REGISTER_FAIL` | S→C | 错误原因 | 注册失败 |
| `LOGOUT` | C→S | — | 下线通知 |
| `CHAT` | C→S / S→C | 聊天文本 | 私聊消息 |
| `USER_LIST` | S→C | `"user1,user2,..."` | 在线用户列表广播 |
| `CONTACTS` | S→C | `"user1,user2,..."` | 我的联系人列表 |
| `HISTORY_REQ` | C→S | 对方用户名 | 请求私聊历史 |
| `HISTORY_RESP` | S→C | 历史消息内容 | 历史记录条目（`from="__END__"` 表示结束） |
| `FRIEND_REQ` | C→S / S→C | — | 好友请求 |
| `FRIEND_ACCEPT` | C→S / S→C | `"accept"` 或提示 | 接受好友 / 操作反馈 |
| `FRIEND_REJECT` | S→C | 拒绝原因 | 拒绝好友 |
| `FRIEND_DELETE` | C→S / S→C | 删除提示 | 删除好友 |
| `ALL_USERS_REQ` | C→S | — | 请求所有注册用户 |
| `ALL_USERS_RESP` | S→C | `"user1,user2,..."` | 所有注册用户列表 |
| `PENDING_REQUESTS` | S→C | `"user1,user2,..."` | 待处理的好友申请 |
| `GROUP_CREATE` | C→S | `"群名,成员1,成员2,..."` | 创建群聊 |
| `GROUP_CREATE_OK` | S→C | `groupId` | 创建成功 |
| `GROUP_CHAT` | C→S / S→C | 聊天文本（`to=groupId`） | 群消息 |
| `GROUP_LIST` | S→C | `"id:name,id:name,..."` | 群列表 |
| `GROUP_HISTORY_REQ` | C→S | `groupId` | 请求群聊历史 |
| `GROUP_HISTORY_RESP` | S→C | 历史消息内容 | 群历史条目 |
| `SEARCH_REQ` | C→S | `"peer\|keyword"` | 搜索私聊记录 |
| `SEARCH_RESP` | S→C | 搜索结果 | 私聊搜索结果 |
| `GROUP_SEARCH_REQ` | C→S | `"groupId\|keyword"` | 搜索群聊记录 |
| `GROUP_SEARCH_RESP` | S→C | 搜索结果 | 群聊搜索结果 |

---

## 四、数据库设计

### 4.1 连接配置

- **JDBC URL**: `jdbc:mysql://localhost:3306/chatte?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8&allowPublicKeyRetrieval=true`
- **用户名**: `root`
- **密码**: `123456`
- **连接方式**: 单例共享连接（`sharedConn`），自动重连

### 4.2 表结构

#### `users` — 用户表

| 列名 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `username` | `VARCHAR(50)` | PRIMARY KEY | 用户名 |
| `password` | `VARCHAR(64)` | NOT NULL | SHA-256 哈希密码 |
| `created_at` | `TIMESTAMP` | DEFAULT CURRENT_TIMESTAMP | 注册时间 |

#### `messages` — 私聊消息表

| 列名 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | `BIGINT` | AUTO_INCREMENT, PK | 消息ID |
| `from_user` | `VARCHAR(50)` | NOT NULL, INDEX | 发送者 |
| `to_user` | `VARCHAR(50)` | NOT NULL, INDEX | 接收者 |
| `content` | `TEXT` | NOT NULL | 消息内容 |
| `send_time` | `BIGINT` | NOT NULL, INDEX | 发送时间戳 |

#### `friend_requests` — 好友关系表

| 列名 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `from_user` | `VARCHAR(50)` | PK (联合) | 请求发起方 |
| `to_user` | `VARCHAR(50)` | PK (联合) | 请求接收方 |
| `status` | `VARCHAR(10)` | DEFAULT `'pending'` | `pending` / `accepted` |

> **好友关系存储**：接受后双向写入两条 `accepted` 记录（A→B 和 B→A），查询联系人只需 `WHERE from_user=? AND status='accepted'`。

#### `chat_groups` — 群聊表

| 列名 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `group_id` | `INT` | AUTO_INCREMENT, PK | 群ID |
| `group_name` | `VARCHAR(100)` | NOT NULL | 群名称 |
| `creator` | `VARCHAR(50)` | NOT NULL | 创建者 |
| `created_at` | `TIMESTAMP` | DEFAULT CURRENT_TIMESTAMP | 创建时间 |

#### `group_members` — 群成员表

| 列名 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `group_id` | `INT` | PK (联合) | 群ID |
| `username` | `VARCHAR(50)` | PK (联合) | 成员用户名 |

#### `group_messages` — 群消息表

| 列名 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | `BIGINT` | AUTO_INCREMENT, PK | 消息ID |
| `group_id` | `INT` | NOT NULL, INDEX | 所属群ID |
| `from_user` | `VARCHAR(50)` | NOT NULL | 发送者 |
| `content` | `TEXT` | NOT NULL | 消息内容 |
| `send_time` | `BIGINT` | NOT NULL, INDEX | 发送时间戳 |

### 4.3 ER 关系图

```
┌──────────┐         ┌──────────────────┐         ┌──────────┐
│  users   │────────▶│ friend_requests  │◀────────│  users   │
│(username)│ 1    N  │(from_user,to_user│  N    1 │(username)│
└──────────┘         │ status)          │         └──────────┘
     │               └──────────────────┘              │
     │                                                 │
     │ 1         N  ┌──────────────┐  N         1      │
     ├─────────────▶│   messages   │◀──────────────────┤
     │              │(from_user,   │                   │
     │              │ to_user,     │                   │
     │              │ content,     │                   │
     │              │ send_time)   │                   │
     │              └──────────────┘                   │
     │                                                 │
     │ N         N  ┌───────────────┐                  │
     ├─────────────▶│group_members  │◀─────────────────┤
     │              │(group_id,     │                  │
     │              │ username)     │                  │
     │              └───────┬───────┘                  │
     │                      │ N                        │
     │                      │                          │
     │               ┌──────┴───────┐                  │
     │               │ chat_groups  │                  │
     │               │(group_id,    │                  │
     │               │ group_name,  │                  │
     │               │ creator)     │                  │
     │               └──────┬───────┘                  │
     │                      │ 1                        │
     │                      │                          │
     │               ┌──────┴────────┐                 │
     │               │group_messages │                 │
     │               │(group_id,     │                 │
     │               │ from_user,    │                 │
     │               │ content,      │                 │
     │               │ send_time)    │                 │
     │               └───────────────┘                 │
```

---

## 五、各模块详解

### 5.1 共享模块 — `common.Message`

消息对象，实现 `Serializable` 接口，是客户端与服务器之间通信的唯一载体。包含 26 个消息类型常量，以及两个格式化时间的工具方法：

- `getFormattedTime()` → `"HH:mm"`（用于聊天气泡）
- `getFormattedDateTime()` → `"yyyy-MM-dd HH:mm:ss"`（用于日志等）

### 5.2 服务器端

#### `ChatServer`

- **职责**：启动 TCP 监听（端口 9090）、管理在线用户映射、广播在线状态
- **核心数据**：`Map<String, ClientHandler> onlineUsers`（ConcurrentHashMap）
- **启动流程**：初始化数据库 → 创建 ServerSocket → 循环 accept → 为每个连接创建 ClientHandler 线程

#### `ClientHandler`

- **职责**：处理单个客户端的所有消息，是服务器端的业务逻辑核心
- **工作方式**：每个客户端连接对应一个独立线程，通过 `ObjectInputStream` 持续读取消息
- **消息处理**：`handleMessage()` 方法根据 `msg.getType()` 分发到对应的 case 分支
- **关键方法**：
  - `send(Message)` — 线程安全的消息发送（synchronized）
  - `sendContacts(db)` — 推送联系人列表
  - `sendGroupList(db)` — 推送群列表
  - `sendPendingRequests(db)` — 推送待处理好友申请

#### `DatabaseManager`

- **设计模式**：单例模式（`getInstance()`）
- **连接管理**：单个共享连接 `sharedConn`，自动重连（`isClosed()` 检测）
- **密码安全**：SHA-256 哈希存储，不存明文
- **事务处理**：好友接受、好友删除、创建群聊等操作使用手动事务（`setAutoCommit(false)` + `commit/rollback`）
- **表初始化**：`init()` 方法使用 `CREATE TABLE IF NOT EXISTS` 自动建表

### 5.3 客户端

#### `Main`

启动入口类。不继承 `Application`，仅调用 `LoginFrame.main(args)`。这样做是为了绕过 JavaFX 11+ 的模块系统检测 — 如果直接运行继承 `Application` 的类，在 classpath 模式下会报 "缺少 JavaFX 运行时组件" 错误。

#### `ChatClient`

- **职责**：网络通信层，管理 Socket 连接、消息收发、回调监听
- **连接模式**：
  - `connect(password)` — 登录，阻塞等待结果（`LinkedBlockingQueue`，超时 6 秒）
  - `register(password)` — 注册，同样阻塞等待
- **消息监听**：`MessageListener` 接口，由 `MainFrame` 实现
  - `onMessage(Message)` — 收到消息
  - `onDisconnected()` — 连接断开
- **消息缓冲**：在 `listener` 设置前收到的消息暂存在 `messageBuffer` 中，设置 listener 时回放
- **便捷方法**：
  - `requestHistory(peer)` — 请求私聊历史
  - `requestGroupHistory(groupId)` — 请求群聊历史
  - `searchMessages(peer, keyword)` — 搜索私聊记录
  - `searchGroupMessages(groupId, keyword)` — 搜索群聊记录

#### `LoginFrame`

- **继承**：`javafx.application.Application`
- **UI 布局**：
  - 上方：蓝紫渐变背景 + Logo 圆形头像 + "ChaTTE" 标题
  - 下方：白色卡片 + 用户名/密码输入框 + 登录按钮 + 注册链接
- **登录流程**：点击登录 → 禁用按钮 → 新线程执行 `ChatClient.connect()` → 成功则创建 `MainFrame` 并关闭登录窗口
- **注册流程**：弹出 `Dialog` → 输入用户名/密码/确认密码 → 新线程执行 `ChatClient.register()` → 成功后自动填入用户名

#### `MainFrame`

- **实现**：`ChatClient.MessageListener` 接口
- **UI 布局**：
  - 顶部 Header：用户头像 + 用户名 + 在线状态 + 好友申请通知 + 创建群聊按钮 + 添加好友按钮
  - 中部 TabPane：「联系人」和「群聊」两个标签页，各含一个 `ListView`
- **联系人列表**：自定义 `ContactCell`（`ListCell<String>`），显示彩色圆形头像、用户名、在线状态、未读角标
- **群聊列表**：自定义 `GroupCell`，显示紫色方形图标 + 群名 + 未读角标
- **头像生成**：`makeAvatar(name, size)` — 根据用户名 hash 生成 HSB 颜色的圆形头像，中心显示首字母
- **消息路由**：`onMessage()` 根据消息类型分发处理：
  - `CHAT` / `GROUP_CHAT` → 转发到对应 ChatFrame，或累加未读计数
  - `USER_LIST` → 更新在线状态
  - `CONTACTS` → 更新联系人列表
  - `GROUP_LIST` → 更新群列表
  - `HISTORY_RESP` / `GROUP_HISTORY_RESP` → 转发到对应 ChatFrame
  - `SEARCH_RESP` / `GROUP_SEARCH_RESP` → 转发搜索结果
  - `FRIEND_REQ` / `PENDING_REQUESTS` → 更新好友申请通知
  - `FRIEND_ACCEPT` / `FRIEND_REJECT` / `FRIEND_DELETE` → 弹窗提示
- **交互功能**：
  - 双击联系人/群聊 → 打开 ChatFrame
  - 右键联系人 → 显示"删除好友"菜单
  - 点击"+"按钮 → 弹出好友搜索对话框
  - 点击"群+"按钮 → 弹出创建群聊对话框（多选联系人）

#### `ChatFrame`

- **UI 布局**：
  - 顶部：蓝紫渐变 Header + 对方名称/群名 + 在线状态 + 搜索按钮
  - 中部：ScrollPane 聊天气泡区域（VBox）
  - 底部：Emoji 按钮 + 快捷键提示 + TextArea 输入框 + 发送按钮
- **聊天气泡**：
  - 自己的消息：右对齐，蓝紫渐变背景，白色文字，圆角 `14 14 4 14`
  - 对方的消息：左对齐，白色背景，深色文字，圆角 `14 14 14 4`
  - 群聊中对方消息额外显示发送者名称
  - 每条消息上方显示时间（`HH:mm`）
- **Emoji 选择器**：
  - 30 个真实 Unicode Emoji（😀😂😊😍🥰 等）
  - 点击"😀 表情"按钮弹出 6×5 网格的 `Popup`
  - 点击 Emoji 插入到输入框光标位置
- **聊天记录搜索**：
  - 点击"🔍 搜索"展开搜索栏
  - 输入关键字 → 服务器端 SQL `LIKE` 查询 → 结果替换当前聊天区域显示
  - 点击"取消" → 从 `allMessages` 列表还原完整聊天记录
- **消息时序管理**：
  - `pendingHistory` — 历史记录加载前的缓冲
  - `pendingRealtime` — 历史加载完成前收到的实时消息缓冲
  - `allMessages` — 所有已显示消息的完整列表（历史+实时），用于搜索取消后还原
  - `historyLoaded` — 标记历史是否加载完毕
- **快捷键**：Enter 发送，Shift+Enter 换行

---

## 六、UI 设计

### 6.1 配色方案

采用蓝紫色渐变色系，整体风格现代、简洁：

| 元素 | 颜色值 | 用途 |
|------|--------|------|
| 主渐变起色 | `#667eea` | Header、按钮、自己的气泡 |
| 主渐变终色 | `#764ba2` | 渐变终点 |
| 紫色辅助 | `#7c6cd4` | 群聊图标、次要渐变 |
| 页面背景 | `#F5F6FA` | 淡蓝灰背景 |
| 卡片/气泡 | `#FFFFFF` | 白色容器 |
| 角标红 | `#FF6B6B` | 未读角标、删除、通知 |
| 次要文字 | `#8E93A4` | 提示文字、离线状态 |
| 边框/分割 | `#EEF0FA` | 列表分割线、悬停背景 |

### 6.2 CSS 样式表 (`style.css`)

所有 UI 样式通过外部 CSS 统一管理，Java 代码中仅保留少量动态内联样式。CSS 覆盖范围：

- `.login-root` / `.login-avatar` / `.form-card` / `.form-field` / `.btn-primary` — 登录页
- `.main-header` / `.username-label` / `.btn-icon` / `.btn-notify` — 主界面顶栏
- `.list-view` / `.list-cell` / `.contact-name` / `.badge` — 联系人列表
- `.tab-pane` — 标签页切换
- `.chat-header` / `.chat-area` / `.bubble-mine` / `.bubble-other` — 聊天窗口
- `.input-area` / `.send-btn` / `.emoji-btn` — 输入区域
- `.search-bar` / `.search-field` / `.btn-search` / `.btn-cancel` — 搜索栏
- `.emoji-grid-btn` — Emoji 网格按钮
- `.scroll-bar` — 自定义滚动条（圆角、隐藏箭头）

### 6.3 字体策略

```css
-fx-font-family: "Microsoft YaHei", "Segoe UI Emoji", "Apple Color Emoji", sans-serif;
```

- **Microsoft YaHei**：中文显示
- **Segoe UI Emoji**：Windows 上的 Emoji 渲染（JavaFX 支持渲染真实 Unicode Emoji）
- **Apple Color Emoji**：macOS 兼容

---

## 七、关键流程

### 7.1 登录流程

```
用户输入用户名/密码 → 点击"登录"
    │
    ├─ 客户端创建 ChatClient，调用 connect(password)
    │     ├─ 建立 TCP Socket 连接
    │     ├─ 创建接收线程 receiveLoop
    │     ├─ 发送 LOGIN 消息
    │     └─ 阻塞等待 authQueue（最多6秒）
    │
    ├─ 服务器 ClientHandler 收到 LOGIN
    │     ├─ 检查用户是否存在
    │     ├─ 验证密码（SHA-256 比对）
    │     ├─ 检查是否重复登录
    │     ├─ 添加到 onlineUsers
    │     ├─ 返回 LOGIN_SUCCESS
    │     ├─ 推送 CONTACTS（联系人列表）
    │     ├─ 推送 GROUP_LIST（群聊列表）
    │     ├─ 推送 PENDING_REQUESTS（待处理好友申请）
    │     └─ 广播 USER_LIST（所有在线用户更新状态）
    │
    └─ 客户端收到 LOGIN_SUCCESS
          ├─ 创建 MainFrame 并显示
          └─ 关闭 LoginFrame
```

### 7.2 发送私聊消息

```
用户在 ChatFrame 输入文字 → 按 Enter 或点击"发送"
    │
    ├─ ChatFrame.doSend()
    │     ├─ 创建 CHAT 类型 Message（from=自己, to=对方）
    │     ├─ 通过 ChatClient.send() 发送到服务器
    │     └─ 本地立即显示自己的气泡（addBubble）
    │
    ├─ 服务器收到 CHAT 消息
    │     ├─ 保存到 messages 表
    │     └─ 查找对方的 ClientHandler → 转发消息
    │
    └─ 对方客户端收到 CHAT 消息
          ├─ MainFrame.onMessage() 分发
          ├─ 如果有打开的 ChatFrame → 直接显示气泡
          └─ 否则 → 累加 unreadCount，刷新列表角标
```

### 7.3 群聊消息

```
用户在群聊 ChatFrame 发送消息
    │
    ├─ 创建 GROUP_CHAT 消息（to=groupId）
    │
    ├─ 服务器收到 GROUP_CHAT
    │     ├─ 保存到 group_messages 表
    │     ├─ 查询群成员列表
    │     └─ 遍历成员，转发给所有在线成员（除发送者自己）
    │
    └─ 群成员客户端收到消息
          └─ 与私聊类似，但气泡额外显示发送者名称
```

---

## 八、依赖配置

### 8.1 Maven 依赖 (`pom.xml`)

| 依赖 | GroupId | ArtifactId | 版本 | 用途 |
|------|---------|-----------|------|------|
| MySQL 驱动 | `mysql` | `mysql-connector-java` | 8.0.33 | 数据库连接 |
| JavaFX Controls | `org.openjfx` | `javafx-controls` | 21.0.2 | UI 控件 |
| JavaFX Graphics | `org.openjfx` | `javafx-graphics` | 21.0.2 | 图形渲染 |

### 8.2 编译配置

- **Java 版本**：源码和目标均为 Java 21
- **编译器插件**：`maven-compiler-plugin 3.11.0`
- **JavaFX 插件**：`javafx-maven-plugin 0.0.8`（mainClass = `client.Main`）
- **资源处理**：`<resources>` 配置复制 `*.css`、`*.fxml`、`*.png`、`*.jpg` 到编译输出目录

---

## 九、运行环境与启动方式

### 9.1 环境要求

| 组件 | 版本 | 说明 |
|------|------|------|
| JDK | 21+ | 推荐 Zulu OpenJDK 21 |
| MySQL | 8.0+ | 需创建 `chatte` 数据库 |
| Maven | 3.9+ | 已内置 Maven Wrapper，无需全局安装 |

### 9.2 数据库准备

```sql
CREATE DATABASE IF NOT EXISTS chatte
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

> 表结构由程序自动创建（`DatabaseManager.init()` 使用 `CREATE TABLE IF NOT EXISTS`），无需手动建表。

### 9.3 启动步骤

**方式一：批处理脚本**

```
1. 双击 compile.bat         → Maven 编译项目
2. 启动 MySQL 服务
3. 双击 run_server.bat      → 启动聊天服务器（端口 9090）
4. 双击 run_client.bat      → 启动客户端（可多开）
```

**方式二：IntelliJ IDEA**

1. 打开项目，IDEA 自动识别 Maven 配置
2. 设置 JDK 为 21+
3. 运行服务器：右键 `ChatServer.java` → Run
4. 运行客户端：右键 `Main.java`（注意：是 `Main.java`，不是 `LoginFrame.java`）→ Run

> **注意**：不能直接运行 `LoginFrame.java`，否则会报 "缺少 JavaFX 运行时组件"。这是 JavaFX 11+ 的模块系统限制——必须通过不继承 `Application` 的入口类启动。

---

## 十、设计模式与技术要点

### 10.1 使用的设计模式

| 模式 | 应用位置 | 说明 |
|------|---------|------|
| **单例模式** | `DatabaseManager` | 全局唯一的数据库管理器实例 |
| **观察者模式** | `ChatClient.MessageListener` | 客户端收到消息时回调通知 UI 层 |
| **MVC 分层** | 整体架构 | Model（Message）、View（JavaFX UI）、Controller（业务逻辑） |
| **生产者-消费者** | `authQueue` | 登录/注册结果的阻塞等待机制 |

### 10.2 线程安全

- `ChatServer.onlineUsers` 使用 `ConcurrentHashMap`
- `ClientHandler.send()` 使用 `synchronized` 防止并发写入
- `ChatClient.send()` 同样 `synchronized`
- `ChatClient.setListener()` 和消息分发使用 `synchronized(this)` 保护
- UI 更新统一通过 `Platform.runLater()` 切换到 JavaFX 应用线程

### 10.3 消息缓冲机制

ChatFrame 打开时会请求历史记录，但在历史记录加载完成前可能已收到新的实时消息。为保证消息顺序正确：

1. `historyLoaded = false` → 收到的实时消息存入 `pendingRealtime`
2. 历史记录逐条存入 `pendingHistory`
3. 收到 `__END__` 标记 → 先渲染所有历史，再追加缓冲的实时消息
4. `historyLoaded = true` → 后续实时消息直接渲染

### 10.4 搜索与还原

ChatFrame 维护 `allMessages` 列表记录所有已显示消息。搜索时清空界面显示搜索结果；点击"取消"时从 `allMessages` 重建完整聊天记录。搜索模式下收到的新消息仍然记录到 `allMessages` 但不显示，取消搜索后自动出现。
