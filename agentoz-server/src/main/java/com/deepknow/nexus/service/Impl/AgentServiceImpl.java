package com.deepknow.nexus.service.impl;

import com.deepknow.agent.api.service.AgentService;
import com.deepknow.agent.api.model.*;
import org.apache.dubbo.config.annotation.DubboService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@DubboService
public class AgentServiceImpl implements AgentService {
    private static final Logger log = LoggerFactory.getLogger(AgentServiceImpl.class);

    @Override
    public ChatResponse chat(ChatRequest request) {
        log.info("接收到 Chat 请求: sessionId={}, msg={}", request.getSessionId(), request.getMessage());
        
        // TODO: 真正的 Codex 调用逻辑稍后实现
        // 目前先返回一个 Mock 响应，证明链路通了
        
        ChatResponse response = new ChatResponse();
        response.setSessionId(request.getSessionId());
        response.setStatus("SUCCESS");
        response.setContent("Server 收到消息: " + request.getMessage() + "\n(AgentOZ v2 正在重构中)");
        
        return response;
    }

    @Override
    public void registerTools(String appName, String toolsJson) {
        log.info("收到工具注册请求: app={}, tools={}", appName, toolsJson);
    }
}
