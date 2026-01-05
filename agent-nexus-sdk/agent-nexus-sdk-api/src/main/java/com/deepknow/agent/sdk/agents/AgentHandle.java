package com.deepknow.agent.sdk.agents;

import reactor.core.publisher.Flux;

/**
 * 智能体句柄：与特定智能体交互
 */
public interface AgentHandle {

    /**
     * 获取 Agent 名称
     */
    String getName();

    /**
     * 向智能体提问并获得同步回复
     *
     * @param message 用户消息
     * @return 助手回复
     */
    String ask(String message);

    /**
     * 向智能体提问并获得流式回复
     *
     * @param message 用户消息
     * @return 流式文本块
     */
    Flux<String> streamAsk(String message);

    /**
     * 仅推理，不更新 Agent 上下文历史
     */
    String inferOnly(String message);
}
