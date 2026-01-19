package com.deepknow.agentoz.orchestrator;

import com.deepknow.agentoz.api.dto.ExecuteTaskRequest;
import com.deepknow.agentoz.api.dto.StreamChatRequest;
import com.deepknow.agentoz.api.dto.StreamChatResponse;
import com.deepknow.agentoz.api.dto.TaskResponse;
import com.deepknow.agentoz.api.service.AgentExecutionService;
import com.deepknow.agentoz.dto.InternalCodexEvent;
import com.deepknow.agentoz.executor.AgentTaskExecutor;
import com.deepknow.agentoz.manager.AgentTaskBuilder;
import com.deepknow.agentoz.infra.repo.AgentRepository;
import com.deepknow.agentoz.manager.converter.TaskResponseConverter;
import com.deepknow.agentoz.model.AgentEntity;
import com.deepknow.agentoz.model.OrchestrationSession;
import com.deepknow.agentoz.service.ConversationHistoryService;
import com.deepknow.agentoz.service.RedisAgentTaskQueue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.common.stream.StreamObserver;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Component;

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
    private final AgentTaskExecutor taskExecutor;
    private final AgentTaskBuilder taskBuilder;
    private final RedisAgentTaskQueue redisAgentTaskQueue;
    private final ConversationHistoryService conversationHistoryService;

    /**
     * 会话管理器（单例，所有实例共享）
     */
    private final OrchestrationSessionManager sessionManager = OrchestrationSessionManager.getInstance();

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
            InternalCodexEvent cancelEvent = new InternalCodexEvent();
            cancelEvent.setType("cancel");
            cancelEvent.setContent("任务已取消");
            session.sendEvent(cancelEvent);
        } catch (Exception e) {
            log.debug("[Orchestrator] SSE 已断开，无法发送取消事件: {}", conversationId);
        }

        // 3. 清理 Redis 队列中的待执行任务（如果有）
        // 这里可以添加清理逻辑，但 RedisAgentTaskQueue 目前没有按 conversationId 清理的接口

        log.info("[Orchestrator] 任务已取消: convId={}, activeTasks={}",
                conversationId, session.getActiveTaskCount());
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
                .eventConsumer(eventConsumer)
                .build();

        sessionManager.registerSession(session);

        // 2. 使用 Virtual Thread 执行主任务
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

        return taskId;
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
                            // 主任务完成时，检查是否还有活跃的子任务
                            if (session.getActiveTaskCount() == 0) {
                                // 所有任务都完成，关闭流
                                log.info("[Orchestrator] 所有任务完成，关闭流: convId={}", session.getSessionId());
                                if (onComplete != null) {
                                    onComplete.run();
                                }
                            } else {
                                log.info("[Orchestrator] 主任务完成，但还有 {} 个子任务活跃，保持连接", session.getActiveTaskCount());
                            }
                        } else {
                            // 处理队列中的下一个任务
                            redisAgentTaskQueue.processNextTask(agentId,
                                    nextTaskDesc -> executeSubTask(session, taskId, agentId, nextTaskDesc));

                            session.completeSubTask(taskId);
                            redisAgentTaskQueue.markAgentFree(agentId);

                            // 子任务完成后，检查是否所有任务都完成
                            if (session.getActiveTaskCount() == 0) {
                                log.info("[Orchestrator] 所有子任务完成，关闭流: convId={}", session.getSessionId());
                                if (onComplete != null) {
                                    onComplete.run();
                                }
                            }
                        }
                    }

                    @Override
                    public void onError(Throwable t) {
                        log.error("[VirtualThread] 任务失败: taskId={}, error={}",
                                taskId, t.getMessage());

                        session.setStatus(OrchestrationSession.SessionStatus.FAILED);

                        if (isSubTask) {
                            redisAgentTaskQueue.markAgentFree(agentId);
                            session.completeSubTask(taskId);
                        }

                        // 任务失败时，检查是否所有任务都完成
                        if (session.getActiveTaskCount() == 0) {
                            log.info("[Orchestrator] 所有任务结束（含失败），关闭流: convId={}", session.getSessionId());
                            if (onComplete != null) {
                                onComplete.run();
                            }
                        }
                    }
                });

            } catch (Exception e) {
                log.error("[VirtualThread] 任务异常: taskId={}, error={}",
                        taskId, e.getMessage(), e);
                // 异常情况下，检查是否所有任务都完成
                if (session.getActiveTaskCount() == 0 && onComplete != null) {
                    onComplete.run();
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
