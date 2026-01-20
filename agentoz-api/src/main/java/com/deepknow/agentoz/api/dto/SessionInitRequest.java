package com.deepknow.agentoz.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 会话初始化请求
 * paper 创建项目时调用，初始化多 Agent 配置
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionInitRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 会话ID（由 paper 生成）
     */
    private String sessionId;

    /**
     * 主智能体ID
     */
    private String primaryAgentId;

    /**
     * 子智能体ID列表（可选，如果主智能体已配置则忽略）
     */
    private List<String> subAgentIds;
}
