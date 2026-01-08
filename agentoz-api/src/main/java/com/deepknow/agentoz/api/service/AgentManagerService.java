package com.deepknow.agentoz.api.service;

import com.deepknow.agentoz.api.dto.AgentDefineRequest;
import com.deepknow.agentoz.api.dto.ConversationCreateRequest;

/**
 * Agent 管理服务 (控制面)
 * 负责会话生命周期管理及智能体能力装配
 */
public interface AgentManagerService {

    /**
     * 创建一个新会话（已重命名为Conversation）
     * @param request 包含用户ID、业务标签等信息
     * @return conversationId 会话唯一标识
     */
    String createConversation(ConversationCreateRequest request);

    /**
     * 在指定会话中定义/装配一个 Agent
     * 业务侧在此处深度定制 Agent 的大脑（模型）和手脚（MCP工具）
     * @param request 包含 Agent 核心配置
     * @return agentId 实例唯一标识
     */
    String defineAgent(AgentDefineRequest request);

    /**
     * 销毁智能体实例
     */
    void removeAgent(String agentId);

    /**
     * 结束会话
     */
    void endConversation(String conversationId);
}
