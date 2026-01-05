package com.deepknow.platform.common;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * 领域异常基类
 *
 * 业务语义：
 * - 所有领域业务异常的基类
 * - 区分于系统异常和技术异常
 * - 包含业务错误码和错误信息
 */
public class DomainException extends RuntimeException {
    private static final Logger log = LoggerFactory.getLogger(DomainException.class);


    private final String errorCode;

    public DomainException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public DomainException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

    // ==================== 静态工厂方法 ====================

    /**
     * 会话不存在
     */
    public static DomainException sessionNotFound(String sessionId) {
        return new DomainException("SESSION_NOT_FOUND",
            "会话不存在: " + sessionId);
    }

    /**
     * Agent 不存在
     */
    public static DomainException agentNotFound(String agentId) {
        return new DomainException("AGENT_NOT_FOUND",
            "Agent 不存在: " + agentId);
    }

    /**
     * Agent 状态错误
     */
    public static DomainException agentStateError(String agentId, String currentState, String expectedState) {
        return new DomainException("AGENT_STATE_ERROR",
            String.format("Agent %s 状态错误，当前状态: %s，期望状态: %s", agentId, currentState, expectedState));
    }

    /**
     * MCP Tool 不存在
     */
    public static DomainException toolNotFound(String toolName) {
        return new DomainException("TOOL_NOT_FOUND",
            "MCP Tool 不存在: " + toolName);
    }

    /**
     * MCP Tool 未启用
     */
    public static DomainException toolNotActive(String toolName) {
        return new DomainException("TOOL_NOT_ACTIVE",
            "MCP Tool 未启用: " + toolName);
    }

    /**
     * 调用超时
     */
    public static DomainException callTimeout(String target, Long timeout) {
        return new DomainException("CALL_TIMEOUT",
            String.format("调用超时: %s，超时时间: %d ms", target, timeout));
    }

    /**
     * 参数验证失败
     */
    public static DomainException invalidParameter(String parameterName, String reason) {
        return new DomainException("INVALID_PARAMETER",
            String.format("参数验证失败: %s，原因: %s", parameterName, reason));
    }
}
