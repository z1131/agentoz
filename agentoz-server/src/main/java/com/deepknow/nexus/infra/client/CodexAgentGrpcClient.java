package com.deepknow.nexus.infra.client;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

/**
 * Codex Agent gRPC 客户端
 *
 * 业务语义：
 * - 调用 codex-agent 的 gRPC 接口
 * - 使用 RunTask 接口进行推理
 * - 支持流式响应
 *
 * proto 定义：
 * service AgentService {
 *   rpc RunTask (RunTaskRequest) returns (stream RunTaskResponse);
 * }
 */
@Component
public class CodexAgentGrpcClient {

    private static final Logger log = LoggerFactory.getLogger(CodexAgentGrpcClient.class);

    @Value("${codex.grpc.host:localhost}")
    private String host;

    @Value("${codex.grpc.port:50051}")
    private int port;

    private ManagedChannel channel;

    @PostConstruct
    public void init() {
        log.info("初始化 Codex Agent gRPC 客户端: {}:{}", host, port);

        channel = ManagedChannelBuilder
            .forAddress(host, port)
            .usePlaintext()  // 开发环境使用明文传输
            .build();

        log.info("Codex Agent gRPC 客户端初始化完成");
    }

    @PreDestroy
    public void shutdown() {
        if (channel != null) {
            try {
                channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
                log.info("Codex Agent gRPC 客户端已关闭");
            } catch (InterruptedException e) {
                log.error("关闭 gRPC 客户端时出错", e);
            }
        }
    }

    /**
     * 获取 gRPC Channel
     */
    public ManagedChannel getChannel() {
        return channel;
    }

    /**
     * 健康检查
     */
    public boolean healthCheck() {
        try {
            return channel != null && !channel.isShutdown();
        } catch (Exception e) {
            log.error("Codex Agent 健康检查失败", e);
            return false;
        }
    }
}
