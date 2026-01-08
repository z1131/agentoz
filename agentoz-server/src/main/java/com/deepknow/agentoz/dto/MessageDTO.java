package com.deepknow.agentoz.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageDTO implements Serializable {
    private String id;
    private String content;
    private String role;
}
