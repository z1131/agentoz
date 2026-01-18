# AsyncCallAgent 使用文档

## 📋 概述

`AsyncCallAgent` 是 AgentOZ 系统中的异步 Agent 调用工具，允许 Agent_A 异步调用 Agent_B，立即返回任务 ID，无需等待 Agent_B 完成。

---

## 🎯 核心特性

### ✅ 异步执行
- 调用后立即返回任务 ID（~10ms）
- Agent_A 可以继续执行其他任务
- Agent_B 在后台独立执行

### ✅ 智能队列
- Agent 忙碌时自动排队
- 支持优先级（high/normal/low）
- 先进先出（同优先级）

### ✅ 状态查询
- 实时查询任务状态
- 获取执行结果
- 错误信息追踪

---

## 📊 任务生命周期

```
[SUBMITTED] → [QUEUED] → [RUNNING] → [COMPLETED]
                  ↓           ↓
              (Agent 忙)   [FAILED]
```

### 状态说明

| 状态 | 说明 |
|------|------|
| `SUBMITTED` | 任务已提交，Agent 空闲，立即执行 |
| `QUEUED` | Agent 正忙，任务在队列中等待 |
| `RUNNING` | Agent 正在处理任务 |
| `COMPLETED` | 任务成功完成 |
| `FAILED` | 任务执行失败 |
| `CANCELLED` | 任务被取消 |

---

## 🚀 快速开始

### 1. 安装数据库表

```bash
mysql -u root -p agentoz < sql/create_async_tasks_table.sql
```

### 2. 配置 Spring 异步支持

```java
@Configuration
@EnableAsync
public class AsyncConfig {
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("async-agent-");
        executor.initialize();
        return executor;
    }
}
```

### 3. 在 Agent 中使用

```
用户: 帮我搜索最新的机器学习论文，同时分析这个选题的可行性

Agent_A (选题助手):
  1. async_call_agent("PaperSearcher", "搜索机器学习最新论文", "high")
     → 立即返回 taskId="abc-123"

  2. async_call_agent("FeasibilityAgent", "分析选题可行性", "high")
     → 立即返回 taskId="def-456"

  3. 返回用户: "已启动论文搜索和可行性分析，请稍候..."
```

---

## 📖 API 参考

### async_call_agent

异步调用其他 Agent

#### 参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `targetAgentName` | String | ✅ | 目标 Agent 名称 |
| `task` | String | ✅ | 任务描述 |
| `priority` | String | ❌ | 优先级（high/normal/low，默认 normal） |

#### 返回值

**成功（立即执行）**:
```json
{
  "taskId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "status": "SUBMITTED",
  "message": "任务已提交，Agent PaperSearcher 开始执行",
  "agentName": "PaperSearcher"
}
```

**成功（加入队列）**:
```json
{
  "taskId": "b2c3d4e5-f6a7-8901-bcde-f12345678901",
  "status": "QUEUED",
  "message": "Agent PaperSearcher 正在执行其他任务，您的任务已排入队列（第 3 位）",
  "queuePosition": 3,
  "agentName": "PaperSearcher"
}
```

**错误**:
```json
{
  "status": "ERROR",
  "message": "找不到目标 Agent: PaperSearcher"
}
```

---

### check_async_task_status

查询异步任务的执行状态和结果

#### 参数

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `taskId` | String | ✅ | 任务 ID |

#### 返回值

**排队中**:
```json
{
  "taskId": "b2c3d4e5-f6a7-8901-bcde-f12345678901",
  "status": "QUEUED",
  "message": "任务排队中，前方还有 2 个任务",
  "queuePosition": 2,
  "agentName": "PaperSearcher",
  "submitTime": "2026-01-18T14:30:00"
}
```

**执行中**:
```json
{
  "taskId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "status": "RUNNING",
  "message": "任务执行中...",
  "agentName": "PaperSearcher",
  "startTime": "2026-01-18T14:30:05"
}
```

**已完成**:
```json
{
  "taskId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "status": "COMPLETED",
  "message": "任务完成",
  "result": "找到 15 篇相关论文：\n1. xxx\n2. yyy\n...",
  "completeTime": "2026-01-18T14:30:45"
}
```

**失败**:
```json
{
  "taskId": "c3d4e5f6-a7b8-9012-cdef-123456789012",
  "status": "FAILED",
  "message": "任务失败: Connection timeout",
  "errorMessage": "Connection timeout",
  "completeTime": "2026-01-18T14:30:30"
}
```

---

## 🎯 使用场景

### 场景 1: 并行执行多个任务

**需求**: Agent_A 需要同时调用 3 个 Agent

**同步方式**（耗时 6 分钟）:
```
Agent_A → call_agent("Agent_B", "task1") → 等待 2 分钟
Agent_A → call_agent("Agent_C", "task2") → 等待 2 分钟
Agent_A → call_agent("Agent_D", "task3") → 等待 2 分钟
```

**异步方式**（耗时 2 分钟）:
```
Agent_A → async_call_agent("Agent_B", "task1") → 立即返回
Agent_A → async_call_agent("Agent_C", "task2") → 立即返回
Agent_A → async_call_agent("Agent_D", "task3") → 立即返回
Agent_A → 继续执行其他任务...

2 分钟后，所有任务完成
```

---

### 场景 2: 长时间任务不阻塞

**需求**: Agent_A 调用 Agent_B 执行需要 10 分钟的任务

**同步方式**:
```
Agent_A → call_agent("Agent_B", "long_running_task")
         ↓
         等待 10 分钟...（可能超时！）
         ↓
         ❌ 超时错误
```

**异步方式**:
```
Agent_A → async_call_agent("Agent_B", "long_running_task")
         ↓
         立即返回 taskId="xxx"
         ↓
Agent_A → 返回用户: "任务已提交，请稍后..."
         ↓
         10 分钟后，用户可以查询结果
```

---

### 场景 3: 优先级控制

**需求**: 高优先级任务需要优先执行

```
// 普通任务
async_call_agent("PaperSearcher", "搜索论文", "normal")

// 高优先级任务（会排在队列前面）
async_call_agent("PaperSearcher", "紧急任务：搜索最新 COVID-19 研究论文", "high")
```

---

## 🔧 高级配置

### 1. 修改队列优先级

在 `AgentTaskQueue.java` 中:

```java
private int getPriorityValue(String priority) {
    return switch (priority.toLowerCase()) {
        case "high" -> 3;   // 可修改为更大的值
        case "normal" -> 2;
        case "low" -> 1;
        default -> 2;
    };
}
```

### 2. 修改线程池大小

在 `AsyncConfig.java` 中:

```java
executor.setCorePoolSize(20);  // 核心线程数
executor.setMaxPoolSize(100);  // 最大线程数
executor.setQueueCapacity(200); // 队列容量
```

### 3. 添加超时控制

```java
@Async
protected void executeAsync(AsyncTaskEntity taskEntity, AgentEntity targetAgent) {
    CompletableFuture.runAsync(() -> {
        // ... 执行逻辑
    }, executor)
    .orTimeout(10, TimeUnit.MINUTES)  // 10 分钟超时
    .exceptionally(ex -> {
        // 超时处理
        taskEntity.setStatus(AsyncTaskStatus.FAILED);
        taskEntity.setErrorMessage("任务超时");
        asyncTaskRepository.updateById(taskEntity);
        return null;
    });
}
```

---

## 📈 监控指标

### 数据库查询

```sql
-- 查看队列统计
SELECT agent_name, status, COUNT(*) as count
FROM async_tasks
WHERE status IN ('QUEUED', 'RUNNING')
GROUP BY agent_name, status;

-- 查看平均执行时间
SELECT
    agent_name,
    AVG(TIMESTAMPDIFF(MINUTE, start_time, complete_time)) as avg_duration_minutes
FROM async_tasks
WHERE status = 'COMPLETED'
    AND start_time IS NOT NULL
    AND complete_time IS NOT NULL
GROUP BY agent_name;

-- 查看失败率
SELECT
    agent_name,
    COUNT(*) as total,
    SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) as failed,
    SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) / COUNT(*) * 100 as failure_rate
FROM async_tasks
GROUP BY agent_name;
```

---

## ⚠️ 注意事项

### 1. Agent 必须支持并发

如果 Agent_B 不支持并发执行多个任务，需要添加锁机制：

```java
// 在 AgentExecutionManager 中
private final Map<String, Lock> agentLocks = new ConcurrentHashMap<>();

public void executeWithLock(String agentId, Runnable task) {
    Lock lock = agentLocks.computeIfAbsent(agentId, k -> new ReentrantLock());
    lock.lock();
    try {
        task.run();
    } finally {
        lock.unlock();
    }
}
```

### 2. 队列内存管理

定期清理空队列，避免内存泄漏：

```java
@Scheduled(fixedRate = 300000) // 每 5 分钟
public void cleanupQueues() {
    agentTaskQueue.cleanupEmptyQueues();
}
```

### 3. 超时任务清理

```java
@Scheduled(fixedRate = 600000) // 每 10 分钟
public void cleanupTimeoutTasks() {
    LocalDateTime threshold = LocalDateTime.now().minusHours(1);

    List<AsyncTaskEntity> timeoutTasks = asyncTaskRepository.findTimeoutTasks(
        AsyncTaskStatus.RUNNING,
        threshold
    );

    timeoutTasks.forEach(task -> {
        task.setStatus(AsyncTaskStatus.FAILED);
        task.setErrorMessage("任务超时（1小时）");
        asyncTaskRepository.updateById(task);
    });
}
```

---

## 🔍 故障排查

### 问题 1: 任务一直处于 QUEUED 状态

**原因**: Agent 可能崩溃或未正确完成任务

**解决**:
```sql
-- 查找长时间 RUNNING 的任务
SELECT * FROM async_tasks
WHERE status = 'RUNNING'
  AND start_time < DATE_SUB(NOW(), INTERVAL 1 HOUR);

-- 手动重置为 FAILED
UPDATE async_tasks
SET status = 'FAILED', error_message = '任务超时'
WHERE task_id = 'xxx';
```

### 问题 2: 队列积压严重

**原因**: 任务执行时间过长或并发不足

**解决**:
1. 增加线程池大小
2. 添加更多 Agent 实例
3. 优化任务执行逻辑

### 问题 3: 内存占用过高

**原因**: 队列任务过多或 result 字段过大

**解决**:
```sql
-- 清理 30 天前的已完成任务
DELETE FROM async_tasks
WHERE status IN ('COMPLETED', 'FAILED', 'CANCELLED')
  AND complete_time < DATE_SUB(NOW(), INTERVAL 30 DAY);
```

---

## 📚 相关文档

- [AgentExecutionManager 使用指南](./AgentExecutionManager.md)
- [MCP 工具开发规范](./MCP工具开发规范.md)
- [队列管理最佳实践](./队列管理最佳实践.md)

---

**文档版本**: v1.0
**最后更新**: 2026-01-18
**作者**: AgentOZ Team
