package com.deepknow.agent.infra.grpc;

import codex.agent.v1.Agent;
import io.grpc.stub.StreamObserver;

/**
 * Dubbo 接口，通过 gRPC (Triple) 协议对接外部 Rust 计算节点。
 */
public interface AgentService {

    /**
     * 执行代理任务 (Unary Request -> Streaming Response)
     */
    default void runTask(Agent.RunTaskRequest request, StreamObserver<Agent.RunTaskResponse> responseObserver) {
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * 实时聊天 (Bidirectional Streaming)
     */
    default StreamObserver<Agent.ChatRequest> realtimeChat(StreamObserver<Agent.ChatResponse> responseObserver) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
