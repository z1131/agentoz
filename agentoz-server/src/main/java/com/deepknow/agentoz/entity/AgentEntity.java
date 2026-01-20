package com.deepknow.agentoz.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "agent", autoResultMap = true)
public class AgentEntity {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String name;

    private String description;

    private String systemPrompt;

    private String modelName;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> tools;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> config;

    private Boolean enabled;

    /**
     * 是否可被其他Agent调用
     */
    private Boolean callableByOthers;

    /**
     * 子智能体ID列表（Agent as Tool 模式）
     * 主智能体可通过工具调用这些子智能体
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> subAgentIds;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
