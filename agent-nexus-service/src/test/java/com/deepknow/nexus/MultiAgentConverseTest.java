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

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 多智能体对抗/协作测试
 *
 * 验证目标：
 * 1. 两个 Agent 独立维护各自的上下文
 * 2. 模拟 5 轮对话，验证长链路通信的稳定性
 */
@SpringBootTest(classes = TestApplication.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class MultiAgentConverseTest {
    private static final Logger log = LoggerFactory.getLogger(MultiAgentConverseTest.class);

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private AgentManager agentManager;

    @Test
    @Transactional
    public void testTwoAgentsDebate() {
        log.info("========== 测试：多智能体 5 轮对话演练 ==========");

        String sessionId = conversationService.createSession("user-vip-001", "科学与哲学之辩");

        // 1. 创建 Agent A：哲学家
        String agentAId = conversationService.createAgent(
            sessionId,
            "哲学家苏格拉底",
            "你是一个古希腊哲学家，说话富有逻辑但总是爱反问。请用中文简短回答。",
            Map.of()
        );

        // 2. 创建 Agent B：物理学家
        String agentBId = conversationService.createAgent(
            sessionId,
            "物理学家爱因斯坦",
            "你是一个现代物理学家，相信科学实验和数据。请用中文简短回答。",
            Map.of()
        );

        // 3. 初始话题
        String currentMessage = "请问，这个世界是真实存在的吗？";
        log.info("初始话题: {}", currentMessage);

        // 4. 进行 5 轮“套娃”对话
        for (int i = 1; i <= 5; i++) {
            log.info("---------- 第 {} 轮 ----------", i);

            // A 说话
            log.info("[发送给 哲学家]");
            currentMessage = agentManager.processTask(agentAId, currentMessage);
            log.info("[哲学家 回复]: {}", currentMessage);

            assertNotNull(currentMessage, "哲学家不应沉默");

            // B 说话
            log.info("[发送给 物理学家]");
            currentMessage = agentManager.processTask(agentBId, currentMessage);
            log.info("[物理学家 回复]: {}", currentMessage);

            assertNotNull(currentMessage, "物理学家不应沉默");
        }

        log.info("========== 对话结束，测试圆满完成 ==========");
        
        TestTransaction.flagForRollback();
    }
}
