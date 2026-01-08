package com.deepknow.agentoz.infra.adapter.grpc;

import io.grpc.stub.StreamObserver;

/**
 * Codex Agent RPC 服务接口
 *
 * <p>这是 Dubbo Triple 协议的 Java 接口定义,用于适配外部 Rust gRPC 服务。
 * 通过这个接口,agentoz-server 可以使用 Dubbo Triple 协议调用 codex-agent (Rust)。</p>
 *
 * <h3>🔌 技术架构</h3>
 * <pre>
 * agentoz-server (Java)           codex-agent (Rust)
 *      |                                   |
 *      |-- Dubbo Triple --> HTTP/2 --> gRPC
 *      |         (本接口)               (Rust实现)
 * </pre>
 *
 * <h3>📦 包路径设计</h3>
 * <ul>
 *   <li>{@code infra.adapter.grpc} - 表明这是基础设施层的 gRPC 协议适配器</li>
 *   <li>不是 {@code service} - 因为这只是 RPC 接口定义,不是业务逻辑</li>
 * </ul>
 *
 * <h3>⚠️ 重要约束</h3>
 * <ol>
 *   <li>本接口的方法签名必须与 Rust 侧的 gRPC service 定义完全一致</li>
 *   <li>proto 文件的 {@code java_package} 选项必须指向本包: {@code com.deepknow.agentoz.infra.adapter.grpc}</li>
 *   <li>修改本接口前,必须先同步更新 Rust proto 定义</li>
 * </ol>
 *
 * @see <a href="https://github.com/QwenLM/Qwen-Agent">Qwen-Agent</a>
 * @see CodexAgentClient
 */
public interface CodexAgentRpcService {

    /**
     * 执行代理任务 (Unary Request -> Streaming Response)
     *
     * <p>客户端发送一个任务请求,服务端流式返回多个响应。</p>
     *
     * <h3>🔄 调用流程</h3>
     * <pre>
     * Client                  Server
     *   |                       |
     *   |--- RunTaskRequest --->|
     *   |                       |
     *   |<-- RunTaskResponse ---|
     *   |<-- RunTaskResponse ---|
     *   |<-- RunTaskResponse ---|
     *   |                       |
     *   |<------ complete ------|
     * </pre>
     *
     * @param request 任务执行请求
     * @param responseObserver 流式响应观察者
     */
    default void runTask(RunTaskRequest request, StreamObserver<RunTaskResponse> responseObserver) {
        throw new UnsupportedOperationException("CodexAgentRpcService.runTask() 未实现 - 此接口仅用于 Dubbo Triple 协议映射,实际实现由 Rust gRPC 服务提供");
    }

    /**
     * 实时聊天 (Bidirectional Streaming)
     *
     * <p>客户端和服务端可以同时发送和接收消息,支持实时对话场景。</p>
     *
     * <h3>🔄 调用流程</h3>
     * <pre>
     * Client                  Server
     *   |                       |
     *   |<-- stream open ------>|
     *   |--- ChatRequest ------>|
     *   |<-- ChatResponse ------|
     *   |--- ChatRequest ------>|
     *   |<-- ChatResponse ------|
     *   |                       |
     *   |<------ close -------->|
     * </pre>
     *
     * @param responseObserver 流式响应观察者
     * @return 请求流观察者,客户端可以通过它发送多个请求
     */
    default StreamObserver<ChatRequest> realtimeChat(StreamObserver<ChatResponse> responseObserver) {
        throw new UnsupportedOperationException("CodexAgentRpcService.realtimeChat() 未实现 - 此接口仅用于 Dubbo Triple 协议映射,实际实现由 Rust gRPC 服务提供");
    }
}
