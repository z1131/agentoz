package com.deepknow.agentoz.api.service;

import com.deepknow.agentoz.api.dto.ExecuteTaskRequest;
import com.deepknow.agentoz.api.dto.StreamChatRequest;
import com.deepknow.agentoz.api.dto.StreamChatResponse;
import com.deepknow.agentoz.api.dto.TaskResponse;
import org.apache.dubbo.common.stream.StreamObserver;

/**
 * Agent 执行服务 (数据面)
 * 驱动智能体进行任务推理与实时交互
 */
public interface AgentExecutionService {

    /**
     * 执行单次任务指令 (Unary Input -> Server Stream)
     * 对应 Codex 的 RunTask 模式
     * 
     * @param request 任务请求（指定 Agent 和输入消息）
     * @param responseObserver 结果流（包含思考过程、工具调用、最终回复）
     */
    void executeTask(ExecuteTaskRequest request, StreamObserver<TaskResponse> responseObserver);

    /**
     * 全双工实时交互任务 (Bidirectional Stream)
     * 对应 Codex 的 RealtimeChat 模式
     * 
     * @param responseObserver 响应流（实时语音/文本结果）
     * @return 请求流（用于持续推送语音数据或文本插话）
     */
    StreamObserver<StreamChatRequest> streamInputExecuteTask(StreamObserver<StreamChatResponse> responseObserver);
}
