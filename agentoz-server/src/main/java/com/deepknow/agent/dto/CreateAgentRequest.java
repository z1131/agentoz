package com.deepknow.agent.dto;

import lombok.Data;
import java.io.Serializable;

@Data
public class CreateAgentRequest implements Serializable {
    private String name;
    private String prompt;
}
