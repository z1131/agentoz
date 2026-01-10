package com.deepknow.agentoz.api.common.exception;

/**
 * AgentOz 业务异常基类
 */
public class AgentOzException extends RuntimeException {
    
    private final AgentOzErrorCode errorCode;

    public AgentOzException(AgentOzErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public AgentOzException(AgentOzErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public AgentOzException(AgentOzErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }

    public AgentOzErrorCode getErrorCode() {
        return errorCode;
    }
}
