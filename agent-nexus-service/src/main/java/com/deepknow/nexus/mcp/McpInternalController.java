package com.deepknow.nexus.mcp;

import com.deepknow.nexus.service.McpService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/mcp/internal")
public class McpInternalController {
    private static final Logger log = LoggerFactory.getLogger(McpInternalController.class);

    @Autowired private McpService mcpService;

    @PostMapping
    public Map<String, Object> handleMcpRequest(
            @RequestHeader("X-Session-Id") String sessionId,
            @RequestBody Map<String, Object> request) {
        
        String method = (String) request.get("method");
        Object id = request.get("id");
        log.debug("MCP Dispatch: method={}, sessionId={}", method, sessionId);

        Map<String, Object> response = new HashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);

        try {
            if ("tools/list".equals(method)) response.put("result", mcpService.listTools());
            else if ("tools/call".equals(method)) response.put("result", mcpService.callTool(sessionId, (Map<String, Object>) request.get("params")));
            else response.put("error", Map.of("code", -32601, "message", "Method not found"));
        } catch (Exception e) {
            log.error("MCP call failed", e);
            response.put("error", Map.of("code", -32603, "message", e.getMessage()));
        }
        return response;
    }
}
