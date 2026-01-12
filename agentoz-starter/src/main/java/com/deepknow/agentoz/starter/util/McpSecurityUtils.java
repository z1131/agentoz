package com.deepknow.agentoz.starter.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * MCP 安全工具类
 * <p>
 * 提供从当前运行环境中获取身份令牌 (Token) 的通用方法。
 * </p>
 *
 * @author AgentOZ Team
 */
public class McpSecurityUtils {

    /**
     * 获取当前请求中的 Bearer Token
     *
     * @return Token 字符串，如果未找到则返回 null
     */
    public static String getCurrentToken() {
        try {
            // 尝试获取 Spring WebMVC 的请求上下文
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return null;
            }

            HttpServletRequest request = attributes.getRequest();
            
            // 1. 优先尝试 Header
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                return authHeader.substring(7);
            }
            
            // 2. 降级尝试 Query Param (规避部分客户端 Header 丢失问题)
            String tokenParam = request.getParameter("token");
            if (tokenParam != null && !tokenParam.isBlank()) {
                return tokenParam;
            }
        } catch (Throwable ignored) {
            // 可能不在 Web 环境下，或者没有引入 Servlet API
        }
        return null;
    }
}
