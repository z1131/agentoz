package com.deepknow.agent.api.dto;
import java.io.Serializable;
public class AgentResponse implements Serializable {
    private Boolean success;
    private String output;
    private String errorMessage;
    public AgentResponse() {}
    public Boolean getSuccess() { return success; }
    public void setSuccess(Boolean success) { this.success = success; }
    public String getOutput() { return output; }
    public void setOutput(String output) { this.output = output; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}