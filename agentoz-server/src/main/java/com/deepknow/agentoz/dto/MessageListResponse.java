package com.deepknow.agentozoz.dto;

import lombok.Data;
import java.io.Serializable;
import java.util.List;

@Data
public class MessageListResponse implements Serializable {
    private List<MessageDTO> list;
    private long total;
}
