package com.deepknow.agentoz.api.dto;

import java.io.Serializable;

/**
 * 实时交互请求帧
 */
public class StreamChatRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private String sessionId;
    private String agentId;

    /**
     * 文本数据增量
     */
    private String textChunk;

    /**
     * 音频数据增量 (PCM/Opus)
     */
    private byte[] audioChunk;

    public StreamChatRequest() {}

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }

    public String getTextChunk() { return textChunk; }
    public void setTextChunk(String textChunk) { this.textChunk = textChunk; }

    public byte[] getAudioChunk() { return audioChunk; }
    public void setAudioChunk(byte[] audioChunk) { this.audioChunk = audioChunk; }
}
