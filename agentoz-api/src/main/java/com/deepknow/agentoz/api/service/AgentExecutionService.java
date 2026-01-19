package com.deepknow.agentoz.api.service;

import com.deepknow.agentoz.api.dto.ExecuteTaskRequest;
import com.deepknow.agentoz.api.dto.StreamChatRequest;
import com.deepknow.agentoz.api.dto.StreamChatResponse;
import com.deepknow.agentoz.api.dto.TaskResponse;
import com.deepknow.agentoz.api.dto.SessionInfo;
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
     * <h3>🎯 使用场景</h3>
     * <ul>
     *   <li>用户发起的对话（自动路由到主智能体）</li>
     *   <li>用户消息会追加到会话历史（所有 Agent 共享）</li>
     *   <li>用户消息会追加到该会话的所有 Agent 的 activeContext</li>
     * </ul>
     *
     * <h3>🔄 原生流式返回 (StreamObserver)</h3>
     * <pre>
     * StreamObserver&lt;TaskResponse&gt; 流式回调:
     *   1. onNext: 接收思考过程、工具调用、回复片段
     *   2. onError: 异常处理
     *   3. onCompleted: 任务结束
     * </pre>
     *
     * @param request 任务请求（conversationId 必填，agentId 可选）
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

    /**
     * 取消正在执行的任务
     *
     * <h3>🎯 使用场景</h3>
     * <ul>
     *   <li>用户点击"停止"按钮</li>
     *   <li>前端 SSE 连接断开</li>
     *   <li>需要紧急终止所有正在执行的 Agent</li>
     * </ul>
     *
     * <h3>⚡ 行为</h3>
     * <ul>
     *   <li>取消会话中的所有任务（主任务 + 子任务）</li>
     *   <li>停止向 SSE 推送事件</li>
     *   <li>清理 Redis 队列中的待执行任务</li>
     *   <li>更新会话状态为 CANCELLED</li>
     * </ul>
     *
     * @param conversationId 会话 ID
     */
    void cancelTask(String conversationId);

    /**
     * 获取会话状态信息（用于断线重连）
     *
     * <h3>🎯 使用场景</h3>
     * <ul>
     *   <li>前端断线重连时检查会话是否还存在</li>
     *   <li>查询会话的当前状态和订阅者数量</li>
     * </ul>
     *
     * @param conversationId 会话 ID
     * @return 会话状态信息，如果会话不存在返回 null
     */
    SessionInfo getSessionInfo(String conversationId);

    /**
     * 订阅会话事件流（用于SSE断线重连）
     *
     * <h3>🎯 使用场景</h3>
     * <ul>
     *   <li>前端刷新页面后重新连接到现有会话</li>
     *   <li>多个客户端同时监听同一会话</li>
     *   <li>SSE连接断开后自动恢复</li>
     * </ul>
     *
     * <h3>⚡ 行为</h3>
     * <ul>
     *   <li>将提供的StreamObserver添加为会话的订阅者</li>
     *   <li>后续的所有事件都会推送给该订阅者</li>
     *   <li>如果会话不存在或已结束，立即完成流</li>
     * </ul>
     *
     * @param conversationId 会话 ID
     * @param responseObserver 响应流观察者
     */
    void subscribeToSession(String conversationId, StreamObserver<TaskResponse> responseObserver);
}
