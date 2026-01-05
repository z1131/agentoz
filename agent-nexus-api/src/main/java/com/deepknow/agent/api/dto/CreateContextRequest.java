package com.deepknow.agent.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;
import java.util.Map;

/**
 * 创建上下文请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateContextRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "会话ID不能为空")
    private String sessionId;

    @NotBlank(message = "租户ID不能为空")
    private String tenantId;

    private Map<String, Object> initialData;
}
