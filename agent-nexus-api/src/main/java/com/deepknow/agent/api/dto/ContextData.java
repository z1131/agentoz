package com.deepknow.agent.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * 上下文数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContextData implements Serializable {

    private static final long serialVersionUID = 1L;

    private Map<String, Object> data;
}
