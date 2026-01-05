# AgentNexus | 智能体协作中枢

AgentNexus 是一套企业级多智能体（Multi-Agent）协同平台，通过 **Model Context Protocol (MCP)** 协议，实现不同领域智能体之间的无缝连接、自主协作与状态共享。

---

## 🏗 架构设计

AgentNexus 遵循“中枢化管控、分布式协同”的设计理念：

- **核心服务 (Nexus Service)**：基于 Java 17 + Spring Boot 3 构建，作为协作中转站，管理会话、Agent 状态及内置协作工具。
- **公共契约 (Nexus API)**：定义全套 RPC 接口与 DTO，支持 Dubbo 3.x 协议，方便微服务矩阵快速接入。
- **开发者 SDK (Nexus SDK)**：提供 Handle 句柄模式的编程接口，支持全链路 Reactive (Project Reactor) 流式响应。
- **计算引擎 (Codex Agent)**：对接极速 Rust 推理引擎，负责复杂的任务拆解与动作执行。

---

## 📦 模块概览

| 模块 | 描述 |
| :--- | :--- |
| `agent-nexus-api` | **契约包**：公共 DTO、枚举及 Dubbo 接口定义。 |
| `agent-nexus-service` | **服务端**：业务逻辑核心、gRPC 客户端、数据库与 Redis 存储层。 |
| `agent-nexus-sdk` | **集成包**：业务系统接入平台的标准工具库。 |

---

## ✨ 核心能力

### 1. 自主协作 (P2P Collaboration)
Agent 之间不再是孤立的，它们可以通过平台内置的 `call_agent` 工具互相通信。例如：编剧 Agent 可以自主寻求翻译 Agent 的帮助。

### 2. 共享 Workspace 存储
解决了多智能体协作中“长文本传递”的痛点。通过 `write_resource` 和 `read_resource` 工具，Agent 可以仅通过“数据引用（Key）”来共享大规模论文、代码库等，极大节省了 Token 损耗。

### 3. 全链路流式直播
从底层 gRPC 到 RPC 再到 SDK，全面支持打字机效果。开发者可以实时监听 Agent 的 **THOUGHT (思考)**、**TOOL_CALL (动作)** 及 **TEXT (输出)**。

---

## 🛠 快速开始

### 编译与安装
```bash
# 在项目根目录下执行，将 API 和 SDK 安装到本地 Maven 仓库
mvn clean install -DskipTests
```

### 业务接入示例
```java
// 使用 SessionHandle 快速启动
SessionHandle session = agentPlatform.openSession("user_001", "学术协作场景");

// 定义 Agent 角色
AgentHandle writer = session.spawnAgent(AgentDefinition.builder()
    .name("写作专家")
    .systemPrompt("你负责撰写学术论文初稿")
    .build());

// 发起流式对话
writer.streamAsk("撰写一篇关于量子力学的导论").subscribe(System.out::print);
```

---

## 📜 开源协议
Apache License 2.0

---
**AgentNexus** —— 让智能体像团队一样工作。