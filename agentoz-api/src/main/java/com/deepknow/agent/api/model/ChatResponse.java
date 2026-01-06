package com.deepknow.agent.api.model;

import java.io.Serializable;

/**
 * 聊天响应对象 (手动实现 Getter/Setter)
 */
public class ChatResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private String sessionId;
    private String content;
    private String status;

    public ChatResponse() {}

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}