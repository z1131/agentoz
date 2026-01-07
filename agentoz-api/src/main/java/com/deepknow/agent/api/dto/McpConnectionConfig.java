package com.deepknow.agent.api.dto;

import java.io.Serializable;
import java.util.Map;

/**
 * MCP (Model Context Protocol) 远程连接配置
 * 业务方部署工具服务，中台通过此配置进行连接回调
 */
public class McpConnectionConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * MCP 服务端地址 (通常是 SSE 协议端点)
     */
    private String url;

    /**
     * 自定义 HTTP 请求头 (用于鉴权)
     */
    private Map<String, String> headers;

    public McpConnectionConfig() {}

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public Map<String, String> getHeaders() { return headers; }
    public void setHeaders(Map<String, String> headers) { this.headers = headers; }
}
