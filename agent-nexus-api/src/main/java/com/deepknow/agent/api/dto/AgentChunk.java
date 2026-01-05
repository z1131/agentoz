package com.deepknow.agent.api.dto;

import java.io.Serializable;

/**
 * 智能体响应块（流式信号碎片）
 */
public class AgentChunk implements Serializable {
    
    /**
     * 信号类型
     * TEXT: 正在生成的文本
     * THOUGHT: 智能体思考过程
     * TOOL_CALL: 正在调用工具
     * TOOL_RESULT: 工具返回结果
     * STATUS: 状态变更（开始、结束、错误）
     */
    private String type;
    
    /**
     * 信号内容
     */
    private String content;

    public AgentChunk() {}

    public AgentChunk(String type, String content) {
        this.type = type;
        this.content = content;
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    // 快捷创建方法
    public static AgentChunk text(String text) { return new AgentChunk("TEXT", text); }
    public static AgentChunk thought(String thought) { return new AgentChunk("THOUGHT", thought); }
    public static AgentChunk toolCall(String call) { return new AgentChunk("TOOL_CALL", call); }
    public static AgentChunk status(String status) { return new AgentChunk("STATUS", status); }
}