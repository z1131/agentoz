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
@lombok.extern.slf4j.Slf4j
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
                log.warn("[MCP Security] ⚠️ RequestAttributes is NULL. Thread: {}", Thread.currentThread().getName());
                return null;
            }

            HttpServletRequest request = attributes.getRequest();
            
            // --- 埋点开始 ---
            log.info("=== MCP SECURITY DEBUG START ===");
            log.info("URI: {}, Method: {}, Thread: {}", request.getRequestURI(), request.getMethod(), Thread.currentThread().getName());
            java.util.Enumeration<String> headerNames = request.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String name = headerNames.nextElement();
                // 打印 Authorization 的长度而非内容，避免日志泄露，其他 Header 正常打印
                String value = request.getHeader(name);
                if ("Authorization".equalsIgnoreCase(name)) {
                    log.info("Header [{}]: {}...", name, value != null && value.length() > 10 ? value.substring(0, 10) : value);
                } else {
                    log.info("Header [{}]: {}", name, value);
                }
            }
            log.info("=== MCP SECURITY DEBUG END ===");
            // --- 埋点结束 ---

            // 1. 优先尝试 Header
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                log.info("[MCP Security] 从 Header 获取到 Token");
                return authHeader.substring(7);
            }
            
            // 2. 降级尝试 Query Param (规避部分客户端 Header 丢失问题)
            String tokenParam = request.getParameter("token");
            if (tokenParam != null && !tokenParam.isBlank()) {
                log.info("[MCP Security] 从 QueryParam 获取到 Token");
                return tokenParam;
            }

            // 3. 终极尝试: 手动解析 Query String (防止 ParameterMap 未解析)
            String queryString = request.getQueryString();
            if (queryString != null) {
                log.debug("[MCP Security] QueryString: {}", queryString);
                int index = queryString.indexOf("token=");
                if (index != -1) {
                    String sub = queryString.substring(index + 6);
                    int end = sub.indexOf("&");
                    String extracted = end == -1 ? sub : sub.substring(0, end);
                    log.info("[MCP Security] 从 QueryString 手动解析到 Token");
                    return extracted;
                }
            }
            
            log.warn("[MCP Security] 未能在请求中找到 Token");
        } catch (Throwable e) {
            log.error("[MCP Security] 获取 Token 异常", e);
        }
        return null;
    }
}
