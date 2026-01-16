package com.deepknow.agentoz.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * AgentOZ 内部 Codex 事件（对齐 Codex EventMsg 40+ 事件类型）
 *
 * <h3>🎯 设计原则</h3>
 * <ul>
 *   <li>完整透传 Codex 原始事件，供内部业务逻辑处理</li>
 *   <li>不暴露给外部 API，由 provider 层转换为 TaskResponse</li>
 * </ul>
 *
 * <h3>📦 事件来源 (adapter.proto oneof event)</h3>
 * <ul>
 *   <li>codex_event_json → eventType + rawEventJson</li>
 *   <li>error → status=ERROR + errorMessage</li>
 *   <li>updated_rollout → status=FINISHED + updatedRollout</li>
 *   <li>adapter_log → 调试日志（可忽略）</li>
 * </ul>
 */
@Data
@Accessors(chain = true)
public class InternalCodexEvent {

    /**
     * 事件状态: PROCESSING, FINISHED, ERROR
     */
    private Status status;

    /**
     * Codex 事件类型（对应 EventMsg 的 type 字段）
     *
     * <p>常见类型：</p>
     * <ul>
     *   <li>agent_message_delta - 文本增量</li>
     *   <li>agent_reasoning_delta - 推理增量</li>
     *   <li>item_started / item_completed - 工具调用生命周期</li>
     *   <li>exec_command_begin / exec_command_end - 命令执行</li>
     *   <li>token_count - Token 使用统计</li>
     *   <li>turn_started / turn_complete - 轮次生命周期</li>
     *   <li>mcp_tool_call_begin / mcp_tool_call_end - MCP 工具调用</li>
     *   <li>session_configured - 会话配置完成</li>
     *   <li>context_compacted - 上下文压缩</li>
     *   <li>等等约 40+ 种事件类型</li>
     * </ul>
     */
    private String eventType;

    /**
     * 原始 Codex 事件 JSON（完整透传）
     *
     * <p>内部业务逻辑根据 eventType 解析此 JSON</p>
     */
    private String rawEventJson;

    /**
     * 错误信息（仅 status=ERROR 时有值）
     */
    private String errorMessage;

    /**
     * 更新后的会话状态数据（JSONL 格式字节数组）
     *
     * <p>核心字段：Agent 下次请求时需传回的 history_rollout</p>
     * <p>仅在 status=FINISHED 时有值</p>
     */
    private byte[] updatedRollout;

    /**
     * 适配器日志（调试用，可忽略）
     */
    private String adapterLog;

    /**
     * 事件发送者名称 (e.g. "MasterAgent", "PaperSearcher")
     * 用于前端区分消息来源
     */
    private String senderName;

    /**
     * 事件状态枚举
     */
    public enum Status {
        /** 处理中 - 正常的事件流 */
        PROCESSING,
        /** 已完成 - 收到 updated_rollout */
        FINISHED,
        /** 错误 - 收到 error 事件 */
        ERROR
    }

    // ==================== 便捷工厂方法 ====================

    public static InternalCodexEvent processing(String eventType, String rawEventJson) {
        return new InternalCodexEvent()
                .setStatus(Status.PROCESSING)
                .setEventType(eventType)
                .setRawEventJson(rawEventJson);
    }

    public static InternalCodexEvent finished(byte[] updatedRollout) {
        return new InternalCodexEvent()
                .setStatus(Status.FINISHED)
                .setUpdatedRollout(updatedRollout);
    }

    public static InternalCodexEvent error(String errorMessage) {
        return new InternalCodexEvent()
                .setStatus(Status.ERROR)
                .setErrorMessage(errorMessage);
    }

    public static InternalCodexEvent log(String adapterLog) {
        return new InternalCodexEvent()
                .setStatus(Status.PROCESSING)
                .setAdapterLog(adapterLog);
    }
}
