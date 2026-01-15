package com.deepknow.agentoz.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 任务执行响应 (流式帧)
 *
 * <h3>🔄 新版设计（对齐 adapter.proto）</h3>
 * <p>Codex Adapter 使用事件驱动模式返回响应：</p>
 * <ul>
 *   <li>codex_event_json - 原始 Codex 事件（解析后填充到各字段）</li>
 *   <li>adapter_log - 系统日志（调试用）</li>
 *   <li>error - 错误信息</li>
 *   <li>updated_rollout - 最终会话状态（字节数据）</li>
 * </ul>
 */
@Data
public class TaskResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 响应状态: PROCESSING, FINISHED, ERROR
     */
    private String status;

    /**
     * Codex原始事件JSON列表（直接透传，不做解析）
     *
     * <p>⚠️ 推荐使用此字段，直接透传Codex原始事件给前端</p>
     * <p>前端可以直接解析Codex的40+种事件类型，实现更丰富的UI效果</p>
     *
     * @see <a href="https://github.com/Codium-ai/codex">Codex Event Types</a>
     */
    private List<String> rawCodexEvents;

    /**
     * 文本增量 (用于打字机效果)
     * @deprecated 使用 rawCodexEvents 替代，前端直接解析 agent_message_delta 事件
     */
    @Deprecated
    private String textDelta;

    /**
     * 思考过程增量 (Reasoning)
     * @deprecated 使用 rawCodexEvents 替代，前端直接解析 agent_reasoning_delta 事件
     */
    @Deprecated
    private String reasoningDelta;

    /**
     * 完整回复内容 (仅在 FINISHED 状态下保证完整)
     * @deprecated 使用 rawCodexEvents 替代，前端直接解析 agent_message 事件
     */
    @Deprecated
    private String finalResponse;

    /**
     * 新增的结构化条目 (JSON 格式列表)
     * 对应 Codex 的 ItemCompleted 事件 (如工具调用结果)
     * @deprecated 使用 rawCodexEvents 替代，前端直接解析 item_completed 事件
     */
    @Deprecated
    private List<String> newItemsJson;

    /**
     * Token 使用统计
     */
    private Usage usage;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 更新后的会话状态数据（JSONL 格式字节数组）
     *
     * <p>⚠️ 核心字段：这是 Agent 下次请求时需要传回的 history_rollout</p>
     * <p>仅在 FINISHED 状态下有值，调用方应将此数据保存到 Agent 的 activeContext</p>
     */
    private byte[] updatedRollout;

    public static class Usage implements Serializable {
        public long promptTokens;
        public String completionTokens; // 考虑到有些模型返回非数字或包含推理 Token，使用 String 或 long
        public long totalTokens;
    }

}
