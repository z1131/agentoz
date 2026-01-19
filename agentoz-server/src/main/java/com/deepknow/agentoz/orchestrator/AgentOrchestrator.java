package com.deepknow.agentoz.orchestrator;

import com.deepknow.agentoz.api.dto.ExecuteTaskRequest;
import com.deepknow.agentoz.api.dto.StreamChatRequest;
import com.deepknow.agentoz.api.dto.StreamChatResponse;
import com.deepknow.agentoz.api.dto.TaskResponse;
import com.deepknow.agentoz.api.dto.SessionInfo;
import com.deepknow.agentoz.api.service.AgentExecutionService;
import com.deepknow.agentoz.dto.InternalCodexEvent;
import com.deepknow.agentoz.executor.AgentTaskExecutor;
import com.deepknow.agentoz.manager.AgentTaskBuilder;
import com.deepknow.agentoz.infra.repo.AgentRepository;
import com.deepknow.agentoz.infra.repo.AsyncTaskRepository;
import com.deepknow.agentoz.manager.converter.TaskResponseConverter;
import com.deepknow.agentoz.model.AgentEntity;
import com.deepknow.agentoz.model.AsyncTaskEntity;
import com.deepknow.agentoz.model.OrchestrationSession;
import com.deepknow.agentoz.service.ConversationHistoryService;
import com.deepknow.agentoz.service.RedisAgentTaskQueue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.common.stream.StreamObserver;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Agent 编排器 - 中心调度节点（Java 21 重写版）
 *
 * <p>职责：</p>
 * <ul>
 *   <li>接收用户请求，创建主会话</li>
 *   <li>调度 Agent 任务执行</li>
 *   <li>管理事件转发到前端</li>
 *   <li>管理会话生命周期</li>
 * </ul>
 *
 * <h3>🏗️ 架构</h3>
 * <pre>
 * 用户请求 → AgentOrchestrator
 *           ├─ 创建 OrchestrationSession
 *           ├─ 使用 Virtual Thread 执行任务
 *           │   └─ AgentTaskExecutor.execute()
 *           │       ├─ AgentTaskBuilder: 构建请求（配置+MCP+JWT+Header）
 *           │       └─ CodexAgentClient: RPC 调用
 *           └─ 转发事件 → 前端 SSE
 * </pre>
 *
 * <h3>✨ Java 21 特性</h3>
 * <ul>
 *   <li>Virtual Threads - 每个任务运行在独立的虚拟线程上</li>
 *   <li>同步代码风格 - 无需回调地狱，代码更清晰</li>
 *   <li>结构化并发 - 明确的任务生命周期管理</li>
 * </ul>
 */
@Slf4j
@Component
@DubboService(protocol = "tri", timeout = 3600000)
@RequiredArgsConstructor
public class AgentOrchestrator implements AgentExecutionService {

    private final AgentRepository agentRepository;
    private final AsyncTaskRepository asyncTaskRepository;
    private final AgentTaskExecutor taskExecutor;
    private final RedisAgentTaskQueue redisAgentTaskQueue;
    private final ConversationHistoryService conversationHistoryService;
    private final OrchestrationSessionManager sessionManager;
    private final com.deepknow.agentoz.scheduler.BacklogScheduler backlogScheduler;
    private final org.redisson.api.RedissonClient redissonClient;

    // 移除手动获取 sessionManager
    // private final OrchestrationSessionManager sessionManager = OrchestrationSessionManager.getInstance();

    @PostConstruct
    public void startConsumer() {
        Thread.startVirtualThread(() -> {
            log.info("[Orchestrator] 启动全局任务消费者线程 (Redisson监听中)...");
            while (true) {
                try {
                    // 1. 阻塞获取任务 (Redisson Blocking Queue)
                    String taskId = redisAgentTaskQueue.takeGlobalTask();
                    
                    // 2. 调度任务
                    dispatchTask(taskId);
                    
                } catch (InterruptedException e) {
                    log.warn("消费者线程被中断", e);
                    break;
                } catch (Exception e) {
                    log.error("消费者循环异常", e);
                    // 防止死循环刷屏，稍作休眠
                    try { Thread.sleep(1000); } catch (Exception ignored) {}
                }
            }
        });
    }

    /**
     * 调度中心核心逻辑：路由任务
     *
     * <p>分布式环境改进：</p>
     * <ul>
     *   <li>sessionManager.getSession() 会自动从 Redis 恢复远程会话</li>
     *   <li>如果 Redis 也不存在，说明会话已过期，任务将被丢弃</li>
     *   <li>使用 Redisson 分布式锁保证 check-and-set-busy 的原子性</li>
     * </ul>
     *
     * <p>🔒 分布式锁保证原子操作：</p>
     * <ul>
     *   <li>防止多个消费者线程同时判定同一个 Agent 空闲</li>
     *   <li>保证 check-then-set 的原子性</li>
     *   <li>避免 Agent 并行执行多个任务</li>
     * </ul>
     */
    private void dispatchTask(String taskId) {
        // 1. 获取任务详情
        AsyncTaskEntity task = asyncTaskRepository.findByTaskId(taskId);
        if (task == null) {
            log.warn("⚠️ 收到任务但数据库不存在: taskId={}", taskId);
            return;
        }

        String agentId = task.getAgentId();

        // 2. 获取会话（分布式改进：会自动从 Redis 恢复远程节点的会话）
        OrchestrationSession session = sessionManager.getSession(task.getConversationId());

        if (session == null) {
            log.warn("⚠️ 任务所属会话不存在 (本地和 Redis 都未找到): convId={}, taskId={}",
                    task.getConversationId(), taskId);
            // 会话已过期或被删除，任务无法执行，直接跳过
            return;
        }

        // 3. 使用分布式锁保证原子操作
        String lockKey = "agentoz:lock:agent:" + agentId;
        org.redisson.api.RLock lock = redissonClient.getLock(lockKey);

        try {
            // 尝试获取锁（立即返回，不等待）
            boolean acquired = lock.tryLock();

            if (!acquired) {
                // 锁获取失败，说明 Agent 忙碌（其他节点正在执行）
                log.info("🔒 Agent 忙碌（被其他节点锁定），任务转入积压队列: agentId={}, taskId={}",
                        agentId, taskId);
                redisAgentTaskQueue.addToBacklog(agentId, taskId);
                return;
            }

            // ✅ 获取锁成功，Agent 确实空闲，原子性地标记忙碌并执行
            log.info("🔓 获取锁成功，Agent 空闲: agentId={}, taskId={}", agentId, taskId);

            // 4. 执行任务（此时已持有锁，保证独占访问）
            executeQueuedTask(session, taskId, agentId);

        } finally {
            // 5. 释放锁（注意：executeQueuedTask 内部会 markAgentBusy，这里只需要释放锁）
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("🔓 释放锁: agentId={}", agentId);
            }
        }
    }

    // ========== 实现 AgentExecutionService 接口 ==========

    @Override
    public void executeTask(ExecuteTaskRequest request, StreamObserver<TaskResponse> responseObserver) {
        String conversationId = request.getConversationId();
        String agentId = request.getAgentId();
        String userMessage = request.getMessage();

        // Fix: 如果 agentId 为空，尝试查找该会话的主 Agent
        if (agentId == null || agentId.isEmpty()) {
            AgentEntity primaryAgent = agentRepository.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AgentEntity>()
                            .eq(AgentEntity::getConversationId, conversationId)
                            .eq(AgentEntity::getIsPrimary, true)
            );

            if (primaryAgent != null) {
                agentId = primaryAgent.getAgentId();
            } else {
                String errorMsg = String.format("AgentId is missing and no primary agent found for conversation: %s", conversationId);
                log.error("[Orchestrator] {}", errorMsg);
                responseObserver.onError(new RuntimeException(errorMsg));
                return;
            }
        }

        log.info("[Orchestrator] 收到任务请求: convId={}, agentId={}", conversationId, agentId);

        // 保存用户消息到历史记录
        conversationHistoryService.appendUserMessage(conversationId, userMessage);

        try {
            // 创建主会话（传入 onComplete 回调）
            OrchestrationSession session = startMainSession(
                    conversationId,
                    agentId,
                    userMessage,
                    event -> {
                        TaskResponse dto = TaskResponseConverter.toTaskResponse(event);
                        if (dto != null) {
                            responseObserver.onNext(dto);
                        }
                    },
                    () -> {
                        // 任务完成时关闭流
                        log.info("[Orchestrator] 流式传输结束: convId={}", conversationId);
                        responseObserver.onCompleted();
                    }
            );

            log.info("[Orchestrator] 主会话已启动: sessionId={}", session.getSessionId());

        } catch (Exception e) {
            log.error("[Orchestrator] 任务执行失败: convId={}, error={}",
                    conversationId, e.getMessage(), e);
            responseObserver.onError(e);
        }
    }

    @Override
    public StreamObserver<StreamChatRequest> streamInputExecuteTask(StreamObserver<StreamChatResponse> responseObserver) {
        // TODO: 实现双向流式调用
        return new StreamObserver<>() {
            @Override public void onNext(StreamChatRequest value) {}
            @Override public void onError(Throwable t) { responseObserver.onError(t); }
            @Override public void onCompleted() { responseObserver.onCompleted(); }
        };
    }

    @Override
    public void cancelTask(String conversationId) {
        log.info("[Orchestrator] 收到取消任务请求: convId={}", conversationId);

        OrchestrationSession session = sessionManager.getSession(conversationId);
        if (session == null) {
            log.warn("[Orchestrator] 会话不存在，无法取消: convId={}", conversationId);
            return;
        }

        // 1. 标记会话为已取消
        session.cancel("用户主动取消");

        // 2. 发送取消事件到前端（如果 SSE 还连着）
        try {
            InternalCodexEvent cancelEvent = InternalCodexEvent.processing("cancel", "{\"message\":\"任务已取消\"}");
            session.sendEvent(cancelEvent);
        } catch (Exception e) {
            log.debug("[Orchestrator] SSE 已断开，无法发送取消事件: {}", conversationId);
        }

        // 3. 清理 Redis 队列中的待执行任务（如果有）
        // 这里可以添加清理逻辑，但 RedisAgentTaskQueue 目前没有按 conversationId 清理的接口

        log.info("[Orchestrator] 任务已取消: convId={}, activeTasks={}",
                conversationId, session.getActiveTaskCount());
    }

    @Override
    public SessionInfo getSessionInfo(String conversationId) {
        com.deepknow.agentoz.model.OrchestrationSession session = sessionManager.getSession(conversationId);
        if (session == null) {
            return null;
        }

        // ✅ 空值保护
        if (session.getStatus() == null) {
            log.warn("⚠️ [Orchestrator] Session status is null: conversationId={}", conversationId);
            session.setStatus(com.deepknow.agentoz.model.OrchestrationSession.SessionStatus.ACTIVE);
        }

        SessionInfo info = new SessionInfo();
        info.setConversationId(session.getSessionId());
        info.setStatus(session.getStatus().name());
        info.setSubscriberCount(session.getSubscriberCount());
        info.setCreatedAt(
            session.getCreatedAt() != null ?
                session.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() :
                System.currentTimeMillis()
        );
        info.setUpdatedAt(
            session.getUpdatedAt() != null ?
                session.getUpdatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() :
                System.currentTimeMillis()
        );
        info.setMainTaskId(session.getMainTaskId());
        info.setCurrentAgentId(session.getCurrentAgentId());
        info.setActiveTaskCount(session.getActiveTaskCount());

        return info;
    }

    @Override
    public void subscribeToSession(String conversationId, StreamObserver<TaskResponse> responseObserver) {
        log.info("[Orchestrator] 收到订阅请求: convId={}", conversationId);

        OrchestrationSession session = sessionManager.getSession(conversationId);
        if (session == null) {
            log.warn("[Orchestrator] 会话不存在，无法订阅: convId={}", conversationId);
            responseObserver.onCompleted();
            return;
        }

        // 检查会话状态
        if (session.getStatus() == OrchestrationSession.SessionStatus.CANCELLED ||
            session.getStatus() == OrchestrationSession.SessionStatus.FAILED) {
            log.info("[Orchestrator] 会话已结束，无法订阅: convId={}, status={}",
                    conversationId, session.getStatus());
            responseObserver.onCompleted();
            return;
        }

        // 添加订阅者
        session.subscribe(event -> {
            TaskResponse dto = TaskResponseConverter.toTaskResponse(event);
            if (dto != null) {
                responseObserver.onNext(dto);
            }
        });

        log.info("[Orchestrator] 订阅成功: convId={}, subscribers={}, status={}, activeTasks={}",
                conversationId, session.getSubscriberCount(), session.getStatus(), session.getActiveTaskCount());

        // 如果会话已经空闲（没有活跃任务），立即完成流
        if (session.getStatus() == OrchestrationSession.SessionStatus.IDLE &&
            session.getActiveTaskCount() == 0) {
            log.info("[Orchestrator] 会话空闲且无活跃任务，完成订阅流: convId={}", conversationId);
            responseObserver.onCompleted();
            return;
        }

        // 注意：对于活跃会话，这里不调用 onCompleted()，而是让会话自然结束时通过回调完成
        // 会话结束时，OrchestrationSession 会通知所有订阅者
    }

    // ========== 主会话管理 ==========

    /**
     * 启动主会话
     */
    public OrchestrationSession startMainSession(
            String conversationId,
            String agentId,
            String userMessage,
            Consumer<InternalCodexEvent> eventConsumer,
            Runnable onComplete
    ) {
        log.info("[Orchestrator] 启动主会话: convId={}, agentId={}", conversationId, agentId);

        // 1. 创建会话
        OrchestrationSession session = OrchestrationSession.builder()
                .sessionId(conversationId)
                .mainTaskId("main-" + conversationId)
                .currentAgentId(agentId)
                .status(OrchestrationSession.SessionStatus.ACTIVE)
                .build();

        // 2. 注册 eventConsumer（不要添加到 subscribers，因为 sendEvent 会分别处理）
        session.setEventConsumer(eventConsumer);

        // 3. 注册会话
        sessionManager.registerSession(session);

        // 4. 使用 Virtual Thread 执行主任务
        executeTaskAsync(session, agentId, userMessage, session.getMainTaskId(), false, onComplete);

        return session;
    }

    // ========== 子任务管理 ==========

    /**
     * 提交子任务
     */
    public String submitSubTask(
            String parentConversationId,
            String parentTaskId,
            String targetAgentId,
            String targetAgentName,
            String taskDescription,
            String priority
    ) {
        log.info("[Orchestrator] 提交子任务: target={}, parent={}",
                targetAgentName, parentTaskId);

        // 1. 验证 Agent
        AgentEntity targetAgent = agentRepository.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AgentEntity>()
                        .eq(AgentEntity::getAgentId, targetAgentId)
        );
        if (targetAgent == null) {
            throw new RuntimeException("Agent 不存在: " + targetAgentName);
        }

        // 2. 获取会话
        OrchestrationSession session = sessionManager.getSession(parentConversationId);
        if (session == null) {
            throw new RuntimeException("会话不存在: " + parentConversationId);
        }

        // 3. 检查忙碌状态并执行或排队
        if (redisAgentTaskQueue.isAgentBusy(targetAgentId)) {
            return enqueueTask(session, parentTaskId, targetAgentId, targetAgentName, taskDescription, priority);
        } else {
            return executeSubTask(session, parentTaskId, targetAgentId, taskDescription);
        }
    }

    /**
     * 执行子任务
     */
    private String executeSubTask(
            OrchestrationSession session,
            String parentTaskId,
            String agentId,
            String taskDescription
    ) {
        String taskId = UUID.randomUUID().toString();

        log.info("[Orchestrator] 执行子任务: taskId={}, agentId={}", taskId, agentId);

        // 1. 记录调用关系
        session.addChildTask(parentTaskId, taskId);

        // 2. 标记忙碌
        redisAgentTaskQueue.markAgentBusy(agentId, taskId);

        // 3. 使用 Virtual Thread 执行
        executeTaskAsync(session, agentId, taskDescription, taskId, true, null);

        return taskId;
    }

    /**
     * 排队任务
     */
    private String enqueueTask(
            OrchestrationSession session,
            String parentTaskId,
            String agentId,
            String agentName,
            String taskDescription,
            String priority
    ) {
        log.info("[Orchestrator] Agent 忙碌，排队: agent={}", agentName);

        String taskId = redisAgentTaskQueue.enqueue(
                agentId, agentName, session.getSessionId(),
                parentTaskId, taskDescription, priority
        );

        session.addChildTask(parentTaskId, taskId);
        session.incrementActiveTasks();

        // 分布式改进：同步活跃任务数到 Redis
        sessionManager.updateSessionStatus(session.getSessionId(), null, session.getActiveTaskCount());

        return taskId;
    }

    /**
     * 执行队列中的任务（恢复执行）
     */
    private void executeQueuedTask(
            OrchestrationSession session,
            String taskId,
            String agentId
    ) {
        log.info("[Orchestrator] 恢复执行队列任务: taskId={}, agentId={}", taskId, agentId);

        // 1. 获取任务详情
        AsyncTaskEntity task = asyncTaskRepository.findByTaskId(taskId);
        if (task == null) {
            log.error("[Orchestrator] 队列任务不存在或已删除: taskId={}", taskId);
            // 标记 Agent 空闲，否则它永远忙碌
            redisAgentTaskQueue.markAgentFree(agentId);
            return;
        }

        // 2. 标记忙碌
        redisAgentTaskQueue.markAgentBusy(agentId, taskId);

        // 3. 更新状态为 RUNNING
        task.setStatus(com.deepknow.agentoz.enums.AsyncTaskStatus.RUNNING);
        task.setStartTime(java.time.LocalDateTime.now());
        asyncTaskRepository.updateById(task);

        // 4. 使用 Virtual Thread 执行
        // 注意：这里使用 taskDescription 作为 userMessage
        executeTaskAsync(session, agentId, task.getTaskDescription(), taskId, true, null);
    }

    // ========== 核心执行逻辑 ==========

    /**
     * 异步执行任务（使用 Virtual Thread）
     *
     * <p>Java 21 特性：</p>
     * <ul>
     *   <li>Thread.startVirtualThread() - 创建轻量级虚拟线程</li>
     *   <li>同步代码风格 - 无需 CompletableFuture 回调</li>
     *   <li>自动阻塞转发 - 虚拟线程在阻塞时不会占用平台线程</li>
     * </ul>
     */
    private void executeTaskAsync(
            OrchestrationSession session,
            String agentId,
            String userMessage,
            String taskId,
            boolean isSubTask,
            Runnable onComplete
    ) {
        // 使用 Virtual Thread 执行任务
        Thread.startVirtualThread(() -> {
            try {
                log.info("[VirtualThread] 任务开始: taskId={}, agentId={}", taskId, agentId);

                // 构建任务上下文
                AgentTaskBuilder.TaskContext context = new AgentTaskBuilder.TaskContext(
                        agentId,
                        session.getSessionId(),
                        userMessage,
                        taskId,
                        isSubTask
                );

                // 执行任务
                taskExecutor.execute(context, new AgentTaskExecutor.EventHandler() {
                    @Override
                    public void onEvent(InternalCodexEvent event) {
                        // 转发到前端
                        session.sendEvent(event);
                    }

                    @Override
                    public void onComplete(String result) {
                        log.info("[VirtualThread] 任务完成: taskId={}, resultLength={}",
                                taskId, result.length());

                        // 保存Agent回复到历史记录
                        try {
                            // 从数据库查询Agent名称
                            String agentId = context.agentId();
                            String agentName = agentId; // 默认使用ID

                            var agentEntity = agentRepository.selectOne(
                                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AgentEntity>()
                                            .eq(AgentEntity::getAgentId, agentId)
                            );
                            if (agentEntity != null) {
                                agentName = agentEntity.getAgentName();
                            }

                            conversationHistoryService.appendAgentReply(session.getSessionId(), agentName, result);
                        } catch (Exception e) {
                            log.error("[History] 保存Agent回复失败: convId={}", session.getSessionId(), e);
                        }

                        if (!isSubTask) {
                            session.setStatus(OrchestrationSession.SessionStatus.IDLE);
                            // 分布式改进：同步状态到 Redis
                            sessionManager.updateSessionStatus(session.getSessionId(),
                                    OrchestrationSession.SessionStatus.IDLE, session.getActiveTaskCount());
                            // 主任务完成时，检查是否还有活跃的子任务
                            if (session.getActiveTaskCount() == 0) {
                                // 所有任务都完成，关闭流（线程安全：只执行一次）
                                log.info("[Orchestrator] 所有任务完成，关闭流: convId={}", session.getSessionId());
                                session.tryCloseStream(onComplete);
                            } else {
                                log.info("[Orchestrator] 主任务完成，但还有 {} 个子任务活跃，保持连接", session.getActiveTaskCount());
                            }
                        } else {
                            // ✅ 优雅设计：通知调度器，而不是直接调度下一个任务
                            redisAgentTaskQueue.markAgentFree(agentId);

                            session.completeSubTask(taskId);
                            // 分布式改进：同步活跃任务数到 Redis
                            sessionManager.updateSessionStatus(session.getSessionId(), null, session.getActiveTaskCount());

                            // 通知 Backlog 调度器：Agent 空闲了
                            backlogScheduler.notifyAgentFree(agentId,
                                    nextTaskId -> executeQueuedTask(session, nextTaskId, agentId));

                            // 子任务完成后，检查是否所有任务都完成
                            if (session.getActiveTaskCount() == 0) {
                                log.info("[Orchestrator] 所有子任务完成，关闭流: convId={}", session.getSessionId());
                                session.tryCloseStream(onComplete);
                            }
                        }
                    }

                    @Override
                    public void onError(Throwable t) {
                        log.error("[VirtualThread] 任务失败: taskId={}, error={}",
                                taskId, t.getMessage());

                        session.setStatus(OrchestrationSession.SessionStatus.FAILED);
                        // 分布式改进：同步状态到 Redis
                        sessionManager.updateSessionStatus(session.getSessionId(),
                                OrchestrationSession.SessionStatus.FAILED, session.getActiveTaskCount());

                        if (isSubTask) {
                            redisAgentTaskQueue.markAgentFree(agentId);
                            session.completeSubTask(taskId);
                            // 分布式改进：同步活跃任务数到 Redis
                            sessionManager.updateSessionStatus(session.getSessionId(), null, session.getActiveTaskCount());
                        }

                        // 任务失败时，检查是否所有任务都完成
                        if (session.getActiveTaskCount() == 0) {
                            log.info("[Orchestrator] 所有任务结束（含失败），关闭流: convId={}", session.getSessionId());
                            session.tryCloseStream(onComplete);
                        }
                    }
                });

            } catch (Exception e) {
                log.error("[VirtualThread] 任务异常: taskId={}, error={}",
                        taskId, e.getMessage(), e);
                // 异常情况下，检查是否所有任务都完成
                if (session.getActiveTaskCount() == 0) {
                    session.tryCloseStream(onComplete);
                }
            }
        });
    }

    // ========== 会话查询 ==========

    public OrchestrationSession getSession(String conversationId) {
        return sessionManager.getSession(conversationId);
    }

    public void endSession(String conversationId) {
        log.info("[Orchestrator] 结束会话: convId={}", conversationId);
        sessionManager.unregisterSession(conversationId);
    }
}
