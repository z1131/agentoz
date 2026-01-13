package com.deepknow.agentoz.infra.client;

import codex.agent.*;
import com.deepknow.agentoz.infra.converter.grpc.ConfigProtoConverter;
import com.deepknow.agentoz.model.AgentConfigEntity;
import org.apache.dubbo.common.stream.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Codex Agent 客户端
 * 负责与 codex-agent (Rust) 服务进行通信 (via Dubbo Triple Protocol)
 *
 * <p>通过 {@link } 接口,使用 Dubbo Triple 协议调用外部 Rust gRPC 服务。</p>
 *
 * <h3> 核心方法</h3>
 * <ul>
 *   <li>{@link #runTask(String, AgentConfigEntity, List, String, StreamObserver)} - 执行Agent任务（流式返回）</li>
 * </ul>
 *
 * @see
 * @see AgentConfigEntity
 */
@Slf4j
@Component
public class CodexAgentClient {

    @DubboReference(
            interfaceClass = AgentService.class,
            // 关键：强制指定直连 URL，从 Nacos 配置读取
            url = "tri://${codex.agent.host}:${codex.agent.port}",
            protocol = "tri",
            check = false,
            timeout = 600000
    )
    private AgentService agentRpcService;

    /**
     * 执行代理任务 (流式返回)
     *
     * @param conversationId 会话ID
     * @param request 预先构建好的请求对象
     * @param responseObserver 响应流观察者
     */
    public void runTask(
            String conversationId,
            RunTaskRequest request,
            StreamObserver<RunTaskResponse> responseObserver
    ) {
        log.info("发起 Codex-Agent 调用: conversationId={}", conversationId);

        try {
            agentRpcService.runTask(request, responseObserver);
        } catch (Exception e) {
            log.error("Codex-Agent 调用异常: conversationId={}", conversationId, e);
            responseObserver.onError(e);
        }
    }

    /**
     * 健康检查
     *
     * @return true if service is available
     */
    public boolean healthCheck() {
        return agentRpcService != null;
    }
}
