package com.deepknow.agentoz.executor;

import com.deepknow.agentoz.dto.InternalCodexEvent;
import com.deepknow.agentoz.infra.client.CodexAgentClient;
import com.deepknow.agentoz.infra.converter.grpc.InternalCodexEventConverter;
import com.deepknow.agentoz.manager.AgentExecutionManager;
import com.deepknow.agentoz.manager.AgentTaskBuilder;
import com.deepknow.agentoz.model.AgentEntity;
import codex.agent.RunTaskRequest;
import codex.agent.RunTaskResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.common.stream.StreamObserver;
import org.springframework.stereotype.Component;

/**
 * Agent 任务执行器（Java 21 重写版）
 *
 * <p>职责：</p>
 * <ul>
 *   <li>执行单个 Agent 任务</li>
 *   <li>调用 Codex RPC 并处理响应流</li>
 *   <li>管理任务生命周期（开始/完成/失败）</li>
 *   <li>使用同步代码风格（运行在 Virtual Thread 上）</li>
 * </ul>
 *
 * <h3>🚀 Java 21 优势</h3>
 * <ul>
 *   <li>Virtual Threads - 轻量级并发，无需回调地狱</li>
 *   <li>同步代码风格 - 更易读易维护</li>
 *   <li>自动阻塞转发 - 无需手动管理线程池</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentTaskExecutor {

    private final AgentTaskBuilder taskBuilder;
    private final CodexAgentClient codexAgentClient;
    private final AgentExecutionManager executionManager;
    private final com.deepknow.agentoz.orchestrator.OrchestrationSessionManager sessionManager;

    /**
     * 执行任务
     *
     * @param context 任务上下文
     * @param eventHandler 事件处理器
     */
    public void execute(AgentTaskBuilder.TaskContext context, EventHandler eventHandler) {
        log.info("[TaskExecutor] 开始执行: taskId={}, agentId={}",
                context.taskId(), context.agentId());

        try {
            // 1. 构建请求
            RunTaskRequest request = taskBuilder.buildTaskRequest(context);
            AgentEntity agent = taskBuilder.loadAgent(context.agentId());

            // 2. 执行 RPC 调用并处理响应
            codexAgentClient.runTask(
                    context.conversationId(),
                    request,
                    new StreamObserver<>() {
                        private final StringBuilder resultBuilder = new StringBuilder();

                        @Override
                        public void onNext(RunTaskResponse response) {
                            try {
                                // 检查是否应该停止任务
                                com.deepknow.agentoz.model.OrchestrationSession session =
                                        sessionManager.getSession(context.conversationId());
                                if (session != null && session.shouldStop()) {
                                    log.info("[TaskExecutor] 任务已取消，停止处理: taskId={}, reason={}",
                                            context.taskId(), session.getCancelReason());
                                    return; // 停止处理后续事件
                                }

                                // 转换事件
                                InternalCodexEvent event = InternalCodexEventConverter.toInternalEvent(response);
                                if (event == null) return;

                                // 设置元数据
                                event.setSenderName(agent.getAgentName());
                                event.setAgentId(agent.getAgentId());

                                // 持久化
                                executionManager.persistEvent(
                                        context.conversationId(),
                                        agent.getAgentId(),
                                        agent.getAgentName(),
                                        event
                                );

                                // 收集结果
                                collectText(event, resultBuilder);

                                // 处理完成事件
                                if (event.getStatus() == InternalCodexEvent.Status.FINISHED) {
                                    handleFinished(agent, event);
                                }

                                // 触发事件处理
                                eventHandler.onEvent(event);

                            } catch (Exception e) {
                                log.error("[TaskExecutor] 事件处理失败: taskId={}",
                                        context.taskId(), e);
                            }
                        }

                        @Override
                        public void onError(Throwable t) {
                            // 检查是否是因为取消导致的错误
                            com.deepknow.agentoz.model.OrchestrationSession session =
                                    sessionManager.getSession(context.conversationId());
                            if (session != null && session.shouldStop()) {
                                log.info("[TaskExecutor] 任务已取消，不处理错误: taskId={}", context.taskId());
                                return;
                            }

                            log.error("[TaskExecutor] RPC 调用失败: taskId={}",
                                    context.taskId(), t);
                            eventHandler.onError(t);
                        }

                        @Override
                        public void onCompleted() {
                            // 检查是否已取消
                            com.deepknow.agentoz.model.OrchestrationSession session =
                                    sessionManager.getSession(context.conversationId());
                            if (session != null && session.shouldStop()) {
                                log.info("[TaskExecutor] 任务已取消，不处理完成: taskId={}", context.taskId());
                                return;
                            }

                            log.info("[TaskExecutor] 任务完成: taskId={}, resultLength={}",
                                    context.taskId(), resultBuilder.length());
                            eventHandler.onComplete(resultBuilder.toString());
                        }
                    }
            );

        } catch (Exception e) {
            log.error("[TaskExecutor] 任务启动失败: taskId={}",
                    context.taskId(), e);
            eventHandler.onError(e);
        }
    }

    /**
     * 处理任务完成事件
     */
    private void handleFinished(AgentEntity agent, InternalCodexEvent event) {
        if (event.getUpdatedRollout() != null && event.getUpdatedRollout().length > 0) {
            agent.setActiveContextFromBytes(event.getUpdatedRollout());
            executionManager.updateAgentActiveContext(
                    agent.getAgentId(),
                    event.getUpdatedRollout()
            );
            log.info("[TaskExecutor] activeContext 已更新: agentId={}, size={}",
                    agent.getAgentId(), event.getUpdatedRollout().length);
        }
    }

    /**
     * 从事件中提取文本
     */
    private void collectText(InternalCodexEvent event, StringBuilder sb) {
        try {
            String json = event.getRawEventJson();
            if (json == null) return;

            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var node = mapper.readTree(json);

            // 尝试多种文本字段
            String[] paths = {"item.text", "delta.text", "content"};
            for (String path : paths) {
                String[] parts = path.split("\\.");
                var current = node;
                for (String part : parts) {
                    current = current.path(part);
                }
                String text = current.asText();
                if (!text.isEmpty() && !text.equals("null")) {
                    if (sb.indexOf(text) == -1) {
                        sb.append(text);
                    }
                    return;
                }
            }
        } catch (Exception e) {
            // 忽略解析错误
        }
    }

    /**
     * 事件处理器接口
     */
    public interface EventHandler {
        void onEvent(InternalCodexEvent event);
        void onComplete(String result);
        void onError(Throwable t);
    }
}
