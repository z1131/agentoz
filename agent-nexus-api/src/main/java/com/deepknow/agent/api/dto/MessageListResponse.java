package com.deepknow.agent.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 消息列表响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageListResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<MessageDTO> messages;

    private Integer total;

    private String sessionId;
}
