package com.deepknow.agentoz.api.dto;

import lombok.Data;
import java.io.Serializable;

@Data
public class AgentDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * Agent 唯一标识
     */
    private String agentId;

    /**
     * Agent 名称
     */
    private String agentName;

    /**
     * 是否为主智能体
     */
    private Boolean isPrimary;

    /**
     * 所属会话ID
     */
    private String conversationId;

    /**
     * 绑定的配置ID
     */
    private String configId;
}
