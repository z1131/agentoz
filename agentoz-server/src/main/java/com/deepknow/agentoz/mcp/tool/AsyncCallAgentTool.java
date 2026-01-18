package com.deepknow.agentoz.mcp.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.deepknow.agentoz.enums.AsyncTaskStatus;
import com.deepknow.agentoz.infra.repo.AgentRepository;
import com.deepknow.agentoz.infra.repo.AsyncTaskRepository;
import com.deepknow.agentoz.manager.AgentExecutionManager;
import com.deepknow.agentoz.model.AgentEntity;
import com.deepknow.agentoz.model.AsyncTaskEntity;
import com.deepknow.agentoz.service.RedisAgentTaskQueue;
import com.deepknow.agentoz.starter.annotation.AgentParam;
import com.deepknow.agentoz.starter.annotation.AgentTool;
import io.modelcontextprotocol.common.McpTransportContext;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 异步调用 Agent 工具
 *
 * <p>允许 Agent_A 异步调用 Agent_B，立即返回任务 ID，
 * Agent_A 可以继续执行其他任务，无需等待 Agent_B 完成。</p>
 *
 * <h3>🔄 工作流程</h3>
 * <pre>
 * 1. Agent_A 调用 async_call_agent("Agent_B", "任务描述")
 * 2. 系统检查 Agent_B 是否忙碌
 * 3. 如果空闲 → 立即执行，返回 SUBMITTED 状态
 * 4. 如果忙碌 → 加入队列，返回 QUEUED 状态
 * 5. Agent_A 立即收到任务 ID，可以继续执行
 * 6. Agent_B 在后台执行（独立线程）
 * 7. Agent_A 可以通过 check_async_task 查询状态
 * </pre>
 *
 * <h3>📊 响应格式</h3>
 * <pre>
 * {
 *   "taskId": "uuid",
 *   "status": "SUBMITTED" | "QUEUED" | "RUNNING" | "COMPLETED" | "FAILED",
 *   "message": "任务已提交",
 *   "queuePosition": 3  // 仅当 status=QUEUED 时存在
 * }
 * </pre>
 *
 * <h3>🎯 使用示例</h3>
 * <pre>
 * // Agent_A 的工具调用
 * async_call_agent(
 *   targetAgentName = "PaperSearcher",
 *   task = "搜索关于机器学习的最新论文",
 *   priority = "high"  // 可选：high/normal/low（默认 normal）
 * )
 *
 * // 立即返回
 * {
 *   "taskId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
 *   "status": "SUBMITTED",
 *   "message": "任务已提交，Agent PaperSearcher 开始执行"
 * }
 *
 * // 后续查询结果
 * check_async_task_status(taskId = "a1b2c3d4...")
 *
 * // 返回
 * {
 *   "status": "COMPLETED",
 *   "result": "找到 15 篇相关论文..."
 * }
 * </pre>
 *
 * @see AgentTaskQueue
 * @see AsyncTaskEntity
 */
@Slf4j
@Component
public class AsyncCallAgentTool {

    @Autowired
    private AgentExecutionManager agentExecutionManager;

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private AsyncTaskRepository asyncTaskRepository;

    @Autowired
    private RedisAgentTaskQueue redisAgentTaskQueue;

    /**
     * 异步调用其他 Agent
     *
     * @param ctx MCP 传输上下文
     * @param targetAgentName 目标 Agent 名称
     * @param task 任务描述
     * @param priority 优先级（high/normal/low，默认 normal）
     * @return JSON 格式的响应
     */
    @AgentTool(
        name = "async_call_agent",
        description = "异步调用其他 Agent，立即返回任务 ID。适用于需要长时间执行的任务，调用后可以继续执行其他操作。"
    )
    public String asyncCallAgent(
        McpTransportContext ctx,
        @AgentParam(name = "targetAgentName", value = "目标智能体名称") String targetAgentName,
        @AgentParam(name = "task", value = "任务描述") String task,
        @AgentParam(name = "priority", value = "优先级（high/normal/low，默认 normal）") String priority
    ) {
        // 参数验证
        if (targetAgentName == null || targetAgentName.trim().isEmpty()) {
            return createErrorResponse("targetAgentName 不能为空");
        }

        if (task == null || task.trim().isEmpty()) {
            return createErrorResponse("task 不能为空");
        }

        // 默认优先级
        if (priority == null || priority.trim().isEmpty()) {
            priority = "normal";
        }

        // 验证优先级值
        if (!priority.matches("^(high|normal|low)$")) {
            return createErrorResponse("priority 必须是 high、normal 或 low");
        }

        try {
            // 获取会话信息
            String conversationId = getHeader(ctx, "X-Conversation-ID");
            String callerAgentId = getHeader(ctx, "X-Agent-ID");

            if (conversationId == null) {
                return createErrorResponse("无法获取会话 ID（X-Conversation-ID）");
            }

            // 查找目标 Agent
            AgentEntity targetAgent = agentRepository.selectOne(
                new LambdaQueryWrapper<AgentEntity>()
                    .eq(AgentEntity::getConversationId, conversationId)
                    .eq(AgentEntity::getAgentName, targetAgentName)
            );

            if (targetAgent == null) {
                return createErrorResponse("找不到目标 Agent: " + targetAgentName);
            }

            // 生成任务 ID
            String taskId = UUID.randomUUID().toString();

            // 创建任务记录
            AsyncTaskEntity taskEntity = AsyncTaskEntity.builder()
                .taskId(taskId)
                .agentId(targetAgent.getAgentId())
                .agentName(targetAgentName)
                .conversationId(conversationId)
                .callerAgentId(callerAgentId)
                .taskDescription(task)
                .priority(priority)
                .status(AsyncTaskStatus.SUBMITTED)
                .submitTime(LocalDateTime.now())
                .build();

            asyncTaskRepository.insert(taskEntity);

            // 检查 Agent 是否忙碌
            if (agentExecutionManager.isAgentBusy(targetAgent.getAgentId())) {
                // Agent 正忙，加入 Redis 队列
                String queuedTaskId = redisAgentTaskQueue.enqueue(
                    targetAgent.getAgentId(),
                    targetAgentName,
                    conversationId,
                    callerAgentId,
                    task,
                    priority
                );

                // 更新任务状态
                taskEntity.setStatus(AsyncTaskStatus.QUEUED);
                asyncTaskRepository.updateById(taskEntity);

                long queuePosition = redisAgentTaskQueue.getPosition(targetAgent.getAgentId(), queuedTaskId);

                log.info("📥 任务已加入 Redis 队列: taskId={}, agentName={}, queuePosition={}",
                    queuedTaskId, targetAgentName, queuePosition);

                return createQueuedResponse(queuedTaskId, targetAgentName, (int) queuePosition);

            } else {
                // Agent 空闲，立即执行
                log.info("▶️  任务立即执行: taskId={}, agentName={}", taskId, targetAgentName);

                // 异步执行
                executeAsync(taskEntity, targetAgent);

                return createSubmittedResponse(taskId, targetAgentName);
            }

        } catch (Exception e) {
            log.error("❌ async_call_agent 执行失败: error={}", e.getMessage(), e);
            return createErrorResponse("执行失败: " + e.getMessage());
        }
    }

    /**
     * 查询异步任务状态
     *
     * @param taskId 任务 ID
     * @return JSON 格式的响应
     */
    @AgentTool(
        name = "check_async_task_status",
        description = "查询异步任务的执行状态和结果。可以用来检查任务是否完成、获取执行结果等。"
    )
    public String checkAsyncTaskStatus(
        @AgentParam(name = "taskId", value = "任务 ID") String taskId
    ) {
        if (taskId == null || taskId.trim().isEmpty()) {
            return createErrorResponse("taskId 不能为空");
        }

        try {
            AsyncTaskEntity task = asyncTaskRepository.findByTaskId(taskId);

            if (task == null) {
                return createErrorResponse("任务不存在: " + taskId);
            }

            log.debug("🔍 查询任务状态: taskId={}, status={}", taskId, task.getStatus());

            return switch (task.getStatus()) {
                case QUEUED -> {
                    long queuePosition = redisAgentTaskQueue.getPosition(task.getAgentId(), taskId);
                    yield createQueuedStatusResponse(task, (int) queuePosition);
                }

                case RUNNING -> createRunningStatusResponse(task);

                case COMPLETED -> createCompletedStatusResponse(task);

                case FAILED -> createFailedStatusResponse(task);

                case CANCELLED -> createCancelledStatusResponse(task);

                default -> createStatusResponse(task, "未知状态");
            };

        } catch (Exception e) {
            log.error("❌ check_async_task_status 执行失败: taskId={}, error={}",
                taskId, e.getMessage(), e);
            return createErrorResponse("查询失败: " + e.getMessage());
        }
    }

    /**
     * 异步执行任务
     */
    @Async
    protected void executeAsync(AsyncTaskEntity taskEntity, AgentEntity targetAgent) {
        CompletableFuture.runAsync(() -> {
            String taskId = taskEntity.getTaskId();
            String agentId = taskEntity.getAgentId();
            String conversationId = taskEntity.getConversationId();
            String task = taskEntity.getTaskDescription();

            try {
                // 更新状态为 RUNNING
                taskEntity.setStatus(AsyncTaskStatus.RUNNING);
                taskEntity.setStartTime(LocalDateTime.now());
                asyncTaskRepository.updateById(taskEntity);

                log.info("▶️  任务开始执行: taskId={}, agentId={}", taskId, agentId);

                // 执行 Agent
                StringBuilder resultBuilder = new StringBuilder();

                agentExecutionManager.executeTaskExtended(
                    new AgentExecutionManager.ExecutionContextExtended(
                        agentId,
                        conversationId,
                        task,
                        "assistant",
                        "AsyncCallAgent",
                        true  // ← 标记为子任务
                    ),
                    event -> {
                        // 1. 将事件回传到前端 SSE 连接（关键！）
                        agentExecutionManager.broadcastSubTaskEvent(conversationId, event);

                        // 2. 同时收集结果用于保存到数据库
                        if (event != null) {
                            String text = extractTextFromEvent(event);
                            if (text != null && !text.isEmpty()) {
                                resultBuilder.append(text);
                            }
                        }
                    },
                    () -> {
                        // 完成
                        String result = resultBuilder.toString();
                        taskEntity.setResult(result);
                        taskEntity.setStatus(AsyncTaskStatus.COMPLETED);
                        taskEntity.setCompleteTime(LocalDateTime.now());
                        asyncTaskRepository.updateById(taskEntity);

                        log.info("✅ 任务完成: taskId={}, resultLength={}",
                            taskId, result.length());

                        // 处理队列中的下一个任务
                        redisAgentTaskQueue.processNextTask(agentId,
                            (nextTaskId) -> {
                                AsyncTaskEntity nextTaskEntity = asyncTaskRepository.findByTaskId(nextTaskId);
                                if (nextTaskEntity != null) {
                                    executeAsync(nextTaskEntity, targetAgent);
                                }
                            });
                    },
                    throwable -> {
                        // 失败
                        taskEntity.setStatus(AsyncTaskStatus.FAILED);
                        taskEntity.setErrorMessage(throwable.getMessage());
                        taskEntity.setCompleteTime(LocalDateTime.now());
                        asyncTaskRepository.updateById(taskEntity);

                        log.error("❌ 任务失败: taskId={}, error={}",
                            taskId, throwable.getMessage(), throwable);

                        // 处理队列中的下一个任务
                        redisAgentTaskQueue.processNextTask(agentId,
                            (nextTaskId) -> {
                                AsyncTaskEntity nextTaskEntity = asyncTaskRepository.findByTaskId(nextTaskId);
                                if (nextTaskEntity != null) {
                                    executeAsync(nextTaskEntity, targetAgent);
                                }
                            });
                    }
                );

            } catch (Exception e) {
                log.error("❌ 执行异步任务异常: taskId={}, error={}",
                    taskId, e.getMessage(), e);

                taskEntity.setStatus(AsyncTaskStatus.FAILED);
                taskEntity.setErrorMessage(e.getMessage());
                taskEntity.setCompleteTime(LocalDateTime.now());
                asyncTaskRepository.updateById(taskEntity);

                // 处理队列中的下一个任务
                redisAgentTaskQueue.processNextTask(agentId,
                    (nextTaskId) -> {
                        AsyncTaskEntity nextTaskEntity = asyncTaskRepository.findByTaskId(nextTaskId);
                        if (nextTaskEntity != null) {
                            executeAsync(nextTaskEntity, targetAgent);
                        }
                    });
            }
        });
    }

    /**
     * 从事件中提取文本
     */
    private String extractTextFromEvent(com.deepknow.agentoz.dto.InternalCodexEvent event) {
        // 简化版本：直接返回 rawEventJson
        // 实际实现可以参考 CallAgentTool 的 collectText 方法
        try {
            String json = event.getRawEventJson();
            if (json != null) {
                return json; // 简化处理
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * 创建已提交响应
     */
    private String createSubmittedResponse(String taskId, String agentName) {
        return String.format("""
            {
              "taskId": "%s",
              "status": "SUBMITTED",
              "message": "任务已提交，Agent %s 开始执行",
              "agentName": "%s"
            }
            """, taskId, agentName, agentName);
    }

    /**
     * 创建已排队响应
     */
    private String createQueuedResponse(String taskId, String agentName, int queuePosition) {
        return String.format("""
            {
              "taskId": "%s",
              "status": "QUEUED",
              "message": "Agent %s 正在执行其他任务，您的任务已排入队列（第 %d 位）",
              "queuePosition": %d,
              "agentName": "%s"
            }
            """, taskId, agentName, queuePosition, queuePosition, agentName);
    }

    /**
     *创建错误响应
     */
    private String createErrorResponse(String message) {
        return String.format("""
            {
              "status": "ERROR",
              "message": "%s"
            }
            """, message);
    }

    /**
     * 创建排队状态响应
     */
    private String createQueuedStatusResponse(AsyncTaskEntity task, int queuePosition) {
        return String.format("""
            {
              "taskId": "%s",
              "status": "QUEUED",
              "message": "任务排队中，前方还有 %d 个任务",
              "queuePosition": %d,
              "agentName": "%s",
              "submitTime": "%s"
            }
            """, task.getTaskId(), queuePosition, queuePosition,
            task.getAgentName(), task.getSubmitTime());
    }

    /**
     * 创建执行中状态响应
     */
    private String createRunningStatusResponse(AsyncTaskEntity task) {
        return String.format("""
            {
              "taskId": "%s",
              "status": "RUNNING",
              "message": "任务执行中...",
              "agentName": "%s",
              "startTime": "%s"
            }
            """, task.getTaskId(), task.getAgentName(), task.getStartTime());
    }

    /**
     * 创建已完成状态响应
     */
    private String createCompletedStatusResponse(AsyncTaskEntity task) {
        // 转义结果中的换行和引号
        String escapedResult = task.getResult()
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r");

        return String.format("""
            {
              "taskId": "%s",
              "status": "COMPLETED",
              "message": "任务完成",
              "result": "%s",
              "completeTime": "%s"
            }
            """, task.getTaskId(), escapedResult, task.getCompleteTime());
    }

    /**
     * 创建失败状态响应
     */
    private String createFailedStatusResponse(AsyncTaskEntity task) {
        return String.format("""
            {
              "taskId": "%s",
              "status": "FAILED",
              "message": "任务失败: %s",
              "errorMessage": "%s",
              "completeTime": "%s"
            }
            """, task.getTaskId(), task.getErrorMessage(),
            task.getErrorMessage(), task.getCompleteTime());
    }

    /**
     * 创建已取消状态响应
     */
    private String createCancelledStatusResponse(AsyncTaskEntity task) {
        return String.format("""
            {
              "taskId": "%s",
              "status": "CANCELLED",
              "message": "任务已取消",
              "completeTime": "%s"
            }
            """, task.getTaskId(), task.getCompleteTime());
    }

    /**
     * 创建通用状态响应
     */
    private String createStatusResponse(AsyncTaskEntity task, String message) {
        return String.format("""
            {
              "taskId": "%s",
              "status": "%s",
              "message": "%s"
            }
            """, task.getTaskId(), task.getStatus(), message);
    }

    /**
     * 从上下文中获取请求头
     */
    private String getHeader(McpTransportContext ctx, String name) {
        if (ctx == null) return null;
        Object v = ctx.get(name);
        if (v == null) v = ctx.get(name.toLowerCase());
        return v != null ? v.toString() : null;
    }

    /**
     * 响应 DTO（可选，用于类型安全的响应）
     */
    @Data
    public static class AsyncCallResponse {
        private String taskId;
        private String status;
        private String message;
        private Integer queuePosition;
        private String agentName;
    }
}
