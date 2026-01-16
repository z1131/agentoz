package com.deepknow.agentoz.provider;

import com.deepknow.agentoz.api.dto.ExecuteTaskRequest;
import com.deepknow.agentoz.api.dto.StreamChatRequest;
import com.deepknow.agentoz.api.dto.StreamChatResponse;
import com.deepknow.agentoz.api.dto.TaskResponse;
import com.deepknow.agentoz.api.service.AgentExecutionService;
import com.deepknow.agentoz.dto.InternalCodexEvent;
import com.deepknow.agentoz.manager.AgentExecutionManager;
import com.deepknow.agentoz.manager.converter.TaskResponseConverter;
import com.deepknow.agentoz.infra.util.StreamGuard;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.common.stream.StreamObserver;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Agent 执行服务实现 (API 层 - 对外接口)
 *
 * <h3>🎯 职责</h3>
 * <ul>
 *   <li>接收外部请求，转换为内部执行上下文</li>
 *   <li>调用 manager 层执行核心业务逻辑</li>
 *   <li>将内部事件转换为 API DTO 返回给调用方</li>
 * </ul>
 *
 * <h3>📦 分层设计</h3>
 * <pre>
 * 外部调用 → provider (API适配) → manager (业务逻辑) → infrastructure (技术实现)
 * </pre>
 */
@Slf4j
@DubboService(protocol = "tri", timeout = 3600000)
public class AgentExecutionServiceImpl implements AgentExecutionService {

    @Autowired
    private AgentExecutionManager agentExecutionManager;

    @Override
    public void executeTask(ExecuteTaskRequest request, StreamObserver<TaskResponse> responseObserver) {
        String traceInfo = "ConvId=" + request.getConversationId();

        StreamGuard.run(responseObserver, () -> {
            log.info("收到任务请求: {}, Role={}", traceInfo, request.getRole());

            // 1. 构建执行上下文
            AgentExecutionManager.ExecutionContext context = new AgentExecutionManager.ExecutionContext(
                    request.getAgentId(),
                    request.getConversationId(),
                    request.getMessage(),
                    request.getRole() != null ? request.getRole() : "user",
                    request.getSenderName()
            );

            // 2. 调用 manager 执行任务
            agentExecutionManager.executeTask(
                    context,
                    // 事件回调：转换为 API DTO 并发送
                    (InternalCodexEvent event) -> {
                        TaskResponse dto = TaskResponseConverter.toTaskResponse(event);
                        if (dto != null) {
                            responseObserver.onNext(dto);
                        }
                    },
                    // 完成回调
                    responseObserver::onCompleted,
                    // 错误回调
                    responseObserver::onError
            );

        }, traceInfo);
    }

    @Override
    public StreamObserver<StreamChatRequest> streamInputExecuteTask(StreamObserver<StreamChatResponse> responseObserver) {
        return new StreamObserver<>() {
            @Override public void onNext(StreamChatRequest value) {}
            @Override public void onError(Throwable t) { responseObserver.onError(t); }
            @Override public void onCompleted() { responseObserver.onCompleted(); }
        };
    }
}
