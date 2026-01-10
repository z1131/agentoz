package com.deepknow.agentoz.api.service;

import com.deepknow.agentoz.api.dto.ExecuteTaskRequest;
import com.deepknow.agentoz.api.dto.StreamChatRequest;
import com.deepknow.agentoz.api.dto.StreamChatResponse;
import com.deepknow.agentoz.api.dto.TaskResponse;
import org.apache.dubbo.common.stream.StreamObserver;

/**
 * Agent 执行服务 (数据面)
 * 驱动智能体进行任务推理与实时交互
 *
 * <h3>🔄 响应式流式设计</h3>
 * <ul>
 *   <li><b>executeTask</b>: 使用 Reactor Flux 实现服务端流式调用</li>
 *   <li><b>streamInputExecuteTask</b>: 双向流式调用（暂保留StreamObserver）</li>
 * </ul>
 *
 * <h3>📋 Dubbo Triple + Reactor 支持</h3>
 * <p>基于 Dubbo 3.1.0+ 的 Triple 协议和 Project Reactor 集成</p>
 * @see <a href="https://cn.dubbo.apache.org/zh-cn/overview/mannual/java-sdk/tasks/framework/more/reactive/">Dubbo Reactive文档</a>
 */
public interface AgentExecutionService {

    /**
     * 执行单次任务指令 (Unary Input -> Server Stream)
     * 对应 Codex 的 RunTask 模式
     *
     * <h3>🔄 原生流式返回 (StreamObserver)</h3>
     * <pre>
     * StreamObserver&lt;TaskResponse&gt; 流式回调:
     *   1. onNext: 接收思考过程、工具调用、回复片段
     *   2. onError: 异常处理
     *   3. onCompleted: 任务结束
     * </pre>
     *
     * @param request 任务请求（指定 Agent 和输入消息）
     * @param responseObserver 响应流观察者
     */
    void executeTask(ExecuteTaskRequest request, StreamObserver<TaskResponse> responseObserver);

    /**
     * 全双工实时交互任务 (Bidirectional Stream)
     * 对应 Codex 的 RealtimeChat 模式
     *
     * <p>TODO: 后续改造成 StreamObserver 双向流</p>
     *
     * @param responseObserver 响应流（实时语音/文本结果）
     * @return 请求流（用于持续推送语音数据或文本插话）
     */
    StreamObserver<StreamChatRequest> streamInputExecuteTask(StreamObserver<StreamChatResponse> responseObserver);
}
