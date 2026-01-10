package com.deepknow.agentoz.api.common.exception;

/**
 * AgentOz 统一错误码
 */
public enum AgentOzErrorCode {
    
    // 系统级错误
    SYSTEM_ERROR("SYS_001", "系统内部错误"),
    INVALID_PARAM("SYS_002", "非法参数"),
    TIMEOUT("SYS_003", "调用超时"),
    
    // 业务级错误
    AGENT_NOT_FOUND("BIZ_001", "智能体不存在"),
    CONVERSATION_NOT_FOUND("BIZ_002", "会话不存在"),
    CONFIG_NOT_FOUND("BIZ_003", "配置不存在"),
    PRIMARY_AGENT_MISSING("BIZ_004", "未定义主智能体"),
    
    // 上游依赖错误
    CODEX_ERROR("EXT_001", "Codex服务调用失败"),
    CODEX_STREAM_ERROR("EXT_002", "Codex流式中断");

    private final String code;
    private final String message;

    AgentOzErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
