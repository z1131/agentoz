package com.deepknow.agent.api.service;

import com.deepknow.agent.api.model.ChatRequest;
import com.deepknow.agent.api.model.ChatResponse;

/**
 * Agent 核心服务接口 (Dubbo Interface)
 */
public interface AgentService {

    /**
     * 发送聊天请求
     *
     * @param request 请求详情
     * @return 响应结果
     */
    ChatResponse chat(ChatRequest request);

    /**
     * (预留) 注册工具定义
     * 目前虽然是空逻辑，但接口先留着
     */
    void registerTools(String appName, String toolsJson);
}
