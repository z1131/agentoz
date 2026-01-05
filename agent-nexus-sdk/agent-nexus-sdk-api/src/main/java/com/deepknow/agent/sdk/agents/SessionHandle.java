package com.deepknow.agent.sdk.agents;

import com.deepknow.agent.sdk.model.AgentDefinition;

import java.util.List;

/**
 * 会话句柄：管理会话内的智能体
 */
public interface SessionHandle {

    /**
     * 获取当前会话 ID
     */
    String getSessionId();

    /**
     * 在当前会话中孵化一个新的智能体
     *
     * @param definition 智能体定义
     * @return 智能体句柄
     */
    AgentHandle spawnAgent(AgentDefinition definition);

    /**
     * 根据名称获取会话内的智能体
     *
     * @param agentName 智能体名称
     * @return 智能体句柄
     */
    AgentHandle getAgent(String agentName);

    /**
     * 关闭并销毁会话
     */
    void close();
}
