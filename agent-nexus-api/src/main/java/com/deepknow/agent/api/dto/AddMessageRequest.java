package com.deepknow.agent.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;

/**
 * 添加消息请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddMessageRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "角色不能为空")
    private String role;

    @NotBlank(message = "内容不能为空")
    private String content;
}
