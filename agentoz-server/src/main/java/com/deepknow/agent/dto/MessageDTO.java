package com.deepknow.agent.dto;

import lombok.Data;
import java.io.Serializable;

@Data
public class MessageDTO implements Serializable {
    private String id;
    private String content;
    private String role;
}
