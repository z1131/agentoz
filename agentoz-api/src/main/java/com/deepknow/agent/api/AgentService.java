package com.deepknow.agent.api;

import com.deepknow.agent.api.dto.*;
import reactor.core.publisher.Flux;

/**
 * 智能体执行服务 - Dubbo 接口定义
 *
 * @author Agent Platform
 * @version 1.0.0
 */
public interface AgentService {

    /**
     * 同步执行智能体任务
     *
     * 
     * @param request 执行请求
     * @return 执行响应
     */
    AgentResponse execute(AgentRequest request);

    /**
     * 在会话中创建一个新的智能体
     *
     * @param request 创建请求
     * @return 智能体 ID
     */
    String createAgent(CreateAgentRequest request);

    /**
     * 获取指定名称的智能体 ID
     *
     * @param sessionId 会话 ID
     * @param agentName 智能体名称
     * @return 智能体 ID
     */
    String getAgentIdByName(String sessionId, String agentName);

    /**
     * 流式执行智能体任务（用于实时对话场景）
     *
     * @param request 执行请求
     * @return 流式响应
     */
    Flux<AgentChunk> executeStream(AgentRequest request);

    /**
     * 创建子会话（用于多智能体协作）
     *
     * @param parentSessionId 父会话ID
     * @param agentRole 智能体角色
     * @param taskDescription 任务描述
     * @return 子会话信息
     */
    SessionDTO createChildSession(String parentSessionId, String agentRole, String taskDescription);

    /**
     * 获取会话历史消息
     *
     * @param sessionId 会话ID
     * @return 历史消息列表
     */
    MessageListResponse getHistory(String sessionId);

    /**
     * 关闭会话
     *
     * @param sessionId 会话ID
     */
    void closeSession(String sessionId);
}
