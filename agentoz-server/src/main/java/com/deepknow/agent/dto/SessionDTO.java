package com.deepknow.nexus.dto;

import lombok.Data;
import java.io.Serializable;
import java.util.List;

@Data
public class SessionDTO implements Serializable {
    private String id;
    private String name;
    private String agentId;
}
