package com.deepknow.nexus;

import com.deepknow.nexus.common.DomainException;
import com.deepknow.nexus.biz.ConversationService;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.transaction.TestTransaction;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Agent 名称作用域测试
 *
 * 验证目标：
 * 1. 同一会话内不能创建同名 Agent
 * 2. 不同会话间可以创建同名 Agent
 */
@SpringBootTest(classes = TestApplication.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class AgentNameScopeTest {
    private static final Logger log = LoggerFactory.getLogger(AgentNameScopeTest.class);

    @Autowired
    private ConversationService conversationService;

    @Test
    @Transactional
    public void testDuplicateNameInSameSession() {
        log.info("========== 测试：同一会话内创建同名 Agent ==========");
        
        String sessionId = conversationService.createSession("user-001", "会话1");
        String agentName = "我的助手";

        // 1. 创建第一个 Agent
        conversationService.createAgent(sessionId, agentName, "提示词1", Map.of());
        log.info("✓ 第一个 Agent 创建成功");

        // 2. 尝试在同一会话创建同名 Agent，预期抛出异常
        DomainException exception = assertThrows(DomainException.class, () -> {
            conversationService.createAgent(sessionId, agentName, "提示词2", Map.of());
        });

        log.info("✓ 预期异常捕获成功: {}", exception.getMessage());
        assertTrue(exception.getMessage().contains("已存在"));

        TestTransaction.flagForRollback();
    }

    @Test
    @Transactional
    public void testSameNameInDifferentSessions() {
        log.info("========== 测试：不同会话间创建同名 Agent ==========");

        String agentName = "通用助手";

        // 1. 在会话 A 创建 Agent
        String sessionIdA = conversationService.createSession("user-001", "会话A");
        String agentIdA = conversationService.createAgent(sessionIdA, agentName, "提示词A", Map.of());
        log.info("✓ 会话A 中的 Agent 创建成功: {}", agentIdA);

        // 2. 在会话 B 创建同名 Agent，预期成功
        String sessionIdB = conversationService.createSession("user-002", "会话B");
        String agentIdB = conversationService.createAgent(sessionIdB, agentName, "提示词B", Map.of());
        log.info("✓ 会话B 中的同名 Agent 创建成功: {}", agentIdB);

        assertNotEquals(agentIdA, agentIdB);
        
        log.info("✓ 测试通过：不同会话间允许同名 Agent");

        TestTransaction.flagForRollback();
    }
}