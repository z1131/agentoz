package com.deepknow.agentoz.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentChatResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private String sessionId;

    /**
     * 产生此事件的智能体ID（可能是主智能体或子智能体）
     */
    private String agentId;

    /**
     * 智能体名称
     */
    private String agentName;

    /**
     * 事件内容
     */
    private String content;

    /**
     * 事件类型：THINKING, ACTION, OBSERVATION, RESPONSE, ERROR, SUB_AGENT_START, SUB_AGENT_END
     */
    private String eventType;

    /**
     * 是否是子智能体产生的事件
     */
    private Boolean fromSubAgent;

    /**
     * 父智能体ID（如果是子智能体事件）
     */
    private String parentAgentId;

    /**
     * 是否完成
     */
    private Boolean finished;
}
