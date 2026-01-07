package com.deepknow.agentozoz.dto;

import lombok.Data;
import java.io.Serializable;

@Data
public class SessionDTO implements Serializable {
    private String id;
    private String name;
    private String agentId;
}
