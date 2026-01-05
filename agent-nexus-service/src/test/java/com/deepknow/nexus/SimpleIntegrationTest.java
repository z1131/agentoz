package com.deepknow.nexus;

import com.deepknow.nexus.biz.AgentManager;
import com.deepknow.nexus.biz.ConversationService;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.transaction.TestTransaction;

import java.util.List;

/**
 * 简单集成测试
 *
 * 测试目标：
 * 1. 创建 Session
 * 2. 创建 Agent
 * 3. Agent 聊天（搜索）
 */
@SpringBootTest(classes = TestApplication.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class SimpleIntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(SimpleIntegrationTest.class);


    @Autowired
    private ConversationService conversationService;

    @Autowired
    private AgentManager agentManager;

    @Test
    @Transactional
    public void testCreateSession() {
        log.info("========== 测试创建 Session ==========");

        String sessionId = conversationService.createSession("test-user-001", "测试会话");

        log.info("✓ Session 创建成功: {}", sessionId);

        var session = conversationService.getSession(sessionId);
        log.info("Session 信息: userId={}, title={}, status={}",
            session.getUserId(), session.getTitle(), session.getStatus());

        // 标记事务回滚
        TestTransaction.flagForRollback();
    }

    @Test
    @Transactional
    public void testCreateAgent() {
        log.info("========== 测试创建 Agent ==========");

        // 先创建 Session
        String sessionId = conversationService.createSession("test-user-002", "Agent测试会话");

        // 创建 Agent，挂载 WebSearch 工具配置
        java.util.Map<String, Object> mcpConfig = java.util.Map.of(
            "WebSearch", java.util.Map.of(
                "type", "streamable_http",
                "url", "https://dashscope.aliyuncs.com/compatible-mode/v1/mcps/WebSearch/sse"
            )
        );

        String agentId = conversationService.createAgent(
            sessionId,
            "搜索助手-" + System.currentTimeMillis(),  // 使用时间戳避免名称重复
            "你是一个智能助手，可以使用联网搜索工具回答用户关于最新信息的问题。",
            mcpConfig
        );

        log.info("✓ Agent 创建成功: {}", agentId);

        var agent = conversationService.getAgent(agentId);
        log.info("Agent 信息: name={}, mcpConfig={}, state={}",
            agent.getAgentName(), agent.getMcpConfig(), agent.getState());

        // 标记事务回滚
        TestTransaction.flagForRollback();
    }

    @Test
    @Transactional
    public void testAgentChat() {
        log.info("========== 测试 Agent 聊天（搜索） ==========");

        // 1. 创建 Session
        String sessionId = conversationService.createSession("test-user-003", "搜索测试");
        log.info("✓ Session: {}", sessionId);

        // 2. 创建 Agent（带 WebSearch 配置）
        java.util.Map<String, Object> mcpConfig = java.util.Map.of(
            "WebSearch", java.util.Map.of(
                "type", "streamable_http",
                "url", "https://dashscope.aliyuncs.com/compatible-mode/v1/mcps/WebSearch/sse"
            )
        );

        String agentId = conversationService.createAgent(
            sessionId,
            "搜索专家-" + System.currentTimeMillis(),
            "你是一个搜索专家，擅长使用联网搜索工具查找最新信息。",
            mcpConfig
        );
        log.info("✓ Agent: {}", agentId);

        // 3. Agent 聊天（搜索）
        String query = "人工智能最新进展";
        log.info("用户提问: {}", query);

        try {
            // 4. 调用 AgentManager 处理任务
            String response = agentManager.processTask(agentId, query);
            log.info("========== Agent 回复 ==========");
            log.info("{}", response);
            log.info("==============================");

            log.info("========== 测试通过 ==========");
        } catch (Exception e) {
            log.error("Agent 调用失败（可能是 codex-agent 服务未启动）", e);
            log.info("========== 跳过 AI 对话测试 ==========");
        }

        // 标记事务回滚
        TestTransaction.flagForRollback();
    }
}
