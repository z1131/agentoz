package com.deepknow.agent.dto;

import lombok.Data;
import java.io.Serializable;

@Data
public class AgentResponse implements Serializable { // Server 内部用的 Agent 详情
    private String id;
    private String name;
    private String prompt;
}
