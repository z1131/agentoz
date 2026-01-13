package com.deepknow.agentoz.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 任务执行响应 (流式帧)
 */
@Data
public class TaskResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 响应状态: PROCESSING, FINISHED, ERROR
     */
    private String status;

    /**
     * 文本增量 (用于打字机效果)
     */
    private String textDelta;

    /**
     * 思考过程增量 (Reasoning)
     */
    private String reasoningDelta;

    /**
     * 完整回复内容 (仅在 FINISHED 状态下保证完整)
     */
    private String finalResponse;

    /**
     * 新增的结构化条目 (JSON 格式列表)
     * 对应 Codex 的 ItemCompleted 事件 (如工具调用结果)
     */
    private List<String> newItemsJson;

    /**
     * Token 使用统计
     */
    private Usage usage;

    /**
     * 错误信息
     */
    private String errorMessage;

    public static class Usage implements Serializable {
        public long promptTokens;
        public String completionTokens; // 考虑到有些模型返回非数字或包含推理 Token，使用 String 或 long
        public long totalTokens;
    }

}
