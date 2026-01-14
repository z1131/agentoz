package com.deepknow.agentoz.infra.client;

import codex.agent.*;
import org.apache.dubbo.common.stream.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

/**
 * Codex Agent 客户端（对齐 adapter.proto）
 *
 * <p>负责与 codex-agent (Rust Adapter) 服务进行通信 (via Dubbo Triple Protocol)</p>
 *
 * <h3>🔄 新版协议变化</h3>
 * <ul>
 *   <li>请求：使用 history_rollout (bytes) 传递会话状态</li>
 *   <li>响应：事件驱动模式（oneof event）</li>
 *   <li>结束标志：updated_rollout 事件包含最新会话状态</li>
 * </ul>
 *
 * @see codex.agent.AgentService
 * @see codex.agent.RunTaskRequest
 * @see codex.agent.RunTaskResponse
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
     * <p>调用 Codex Adapter 的 RunTask RPC，返回事件流：</p>
     * <ul>
     *   <li>codex_event_json - 原始 Codex 事件</li>
     *   <li>adapter_log - 系统日志</li>
     *   <li>error - 错误信息</li>
     *   <li>updated_rollout - 最终会话状态</li>
     * </ul>
     *
     * @param sessionId 会话ID（用于日志追踪）
     * @param request 预先构建好的请求对象
     * @param responseObserver 响应流观察者
     */
    public void runTask(
            String sessionId,
            RunTaskRequest request,
            StreamObserver<RunTaskResponse> responseObserver
    ) {
        log.info("发起 Codex-Agent 调用: sessionId={}, requestId={}, historySize={} bytes",
                sessionId,
                request.getRequestId(),
                request.getHistoryRollout().size());

        try {
            agentRpcService.runTask(request, responseObserver);
        } catch (Exception e) {
            log.error("Codex-Agent 调用异常: sessionId={}", sessionId, e);
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
