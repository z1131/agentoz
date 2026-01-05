package com.deepknow.platform.common.exception;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import lombok.Getter;

/**
 * 平台异常
 */
@Getter
public class PlatformException extends RuntimeException {
    private static final Logger log = LoggerFactory.getLogger(PlatformException.class);


    private final String code;

    public PlatformException(String code, String message) {
        super(message);
        this.code = code;
    }

    public PlatformException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public static PlatformException sessionNotFound(String sessionId) {
        return new PlatformException("SESSION_NOT_FOUND", 
            "会话不存在: " + sessionId);
    }

    public static PlatformException contextNotFound(String contextId) {
        return new PlatformException("CONTEXT_NOT_FOUND", 
            "上下文不存在: " + contextId);
    }

    public static PlatformException agentExecutionFailed(String reason) {
        return new PlatformException("AGENT_EXECUTION_FAILED", 
            "智能体执行失败: " + reason);
    }
}
