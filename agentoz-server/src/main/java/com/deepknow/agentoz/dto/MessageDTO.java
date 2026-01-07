package com.deepknow.agentoz.dto;

import lombok.Data;
import java.io.Serializable;

@Data
public class MessageDTO implements Serializable {
    private String id;
    private String content;
    private String role;
}
