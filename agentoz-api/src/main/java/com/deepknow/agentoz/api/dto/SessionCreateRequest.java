package com.deepknow.agentoz.api.dto;

import java.io.Serializable;
import java.util.Map;

/**
 * 创建会话请求
 */
public class SessionCreateRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 用户 ID
     */
    private String userId;

    /**
     * 业务线/应用编码
     */
    private String businessCode;

    /**
     * 会话标题 (可选)
     */
    private String title;

    /**
     * 扩展元数据
     */
    private Map<String, Object> metadata;

    public SessionCreateRequest() {}

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getBusinessCode() { return businessCode; }
    public void setBusinessCode(String businessCode) { this.businessCode = businessCode; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
}
