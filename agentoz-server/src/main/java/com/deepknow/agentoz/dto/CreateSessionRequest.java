package com.deepknow.agentozoz.dto;

import lombok.Data;
import java.io.Serializable;

@Data
public class CreateSessionRequest implements Serializable {
    private String agentId;
    private String name;
}
