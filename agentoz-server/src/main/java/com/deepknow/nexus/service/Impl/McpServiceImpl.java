package com.deepknow.nexus.service.impl;

import com.deepknow.nexus.service.McpService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;

@Service
public class McpServiceImpl implements McpService {
    private static final Logger log = LoggerFactory.getLogger(McpServiceImpl.class);

    @Override
    public Map<String, Object> listTools() {
        return Collections.emptyMap();
    }

    @Override
    public Map<String, Object> callTool(String sessionId, Map<String, Object> params) {
        log.warn("MCP call_agent not implemented yet in this version");
        return Collections.emptyMap();
    }
}
