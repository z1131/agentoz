package com.deepknow.agentozoz.dto;

import lombok.Data;
import java.io.Serializable;

@Data
public class AddMessageRequest implements Serializable {
    private String sessionId;
    private String content;
    private String role;
}
