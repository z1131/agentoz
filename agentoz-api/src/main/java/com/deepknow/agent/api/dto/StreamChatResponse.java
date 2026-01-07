package com.deepknow.agent.api.dto;

import java.io.Serializable;

/**
 * 实时交互响应帧
 */
public class StreamChatResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * STT 中间识别结果 (针对音频输入)
     */
    private String sttIntermediate;

    /**
     * Agent 回复文本增量
     */
    private String agentResponseDelta;

    /**
     * 错误信息
     */
    private String errorMessage;

    public StreamChatResponse() {}

    public String getSttIntermediate() { return sttIntermediate; }
    public void setSttIntermediate(String sttIntermediate) { this.sttIntermediate = sttIntermediate; }

    public String getAgentResponseDelta() { return agentResponseDelta; }
    public void setAgentResponseDelta(String agentResponseDelta) { this.agentResponseDelta = agentResponseDelta; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
