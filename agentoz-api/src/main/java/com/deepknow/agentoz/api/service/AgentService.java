package com.deepknow.agentoz.api.service;

import com.deepknow.agentoz.api.dto.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface AgentService {

    /**
     * 初始化会话（paper 创建项目时调用）
     * 预加载主智能体及其子智能体配置
     */
    Mono<SessionInitResponse> initSession(SessionInitRequest request);

    /**
     * 流式对话（主入口）
     * 返回主智能体和子智能体的所有事件
     */
    Flux<AgentChatResponse> streamChat(AgentChatRequest request);

    /**
     * 打断当前会话
     */
    Mono<Boolean> interruptSession(String sessionId);

    Mono<AgentChatResponse> chat(AgentChatRequest request);

    Mono<AgentDefinitionDTO> getAgent(String agentId);

    Mono<List<AgentDefinitionDTO>> listAgents();

    Mono<AgentDefinitionDTO> createAgent(AgentDefinitionDTO definition);

    Mono<AgentDefinitionDTO> updateAgent(AgentDefinitionDTO definition);

    Mono<Boolean> deleteAgent(String agentId);
}
