package com.deepknow.agentoz.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

/**
 * A2A (Agent-to-Agent) 协议上下文
 * 用于在智能体协作链路中追踪任务、控制递归和关联业务追踪
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class A2AContext implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * A2A 全局任务追踪 ID (建议关联业务 TraceId)
     */
    private String traceId;

    /**
     * 父任务 ID (当前任务是由哪个任务派生出来的)
     */
    private String parentTaskId;

    /**
     * 递归深度 (根任务为 0，每委派一次 +1)
     */
    private int depth;

    /**
     * 最初发起整个任务链路的 Agent ID
     */
    private String originAgentId;

    /**
     * 创建根任务上下文
     */
    public static A2AContext root(String agentId, String businessTraceId) {
        return A2AContext.builder()
                .traceId(businessTraceId != null ? businessTraceId : UUID.randomUUID().toString())
                .parentTaskId(null)
                .depth(0)
                .originAgentId(agentId)
                .build();
    }

    /**
     * 创建子任务上下文
     * @param currentTaskId 当前正在执行的任务 ID，将作为子任务的父 ID
     */
    public A2AContext next(String currentTaskId) {
        return A2AContext.builder()
                .traceId(this.traceId)
                .parentTaskId(currentTaskId)
                .depth(this.depth + 1)
                .originAgentId(this.originAgentId)
                .build();
    }
}
