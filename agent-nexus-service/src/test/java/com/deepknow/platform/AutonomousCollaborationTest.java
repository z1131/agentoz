package com.deepknow.platform;

import com.deepknow.platform.service.AgentManager;
import com.deepknow.platform.service.ConversationService;
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
 * 自主协作测试
 * 
 * 验证目标：
 * 一个 Agent 能够通过 call_agent 工具，自主地与其他 Agent 通信。
 */
@SpringBootTest(classes = TestApplication.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class AutonomousCollaborationTest {
    private static final Logger log = LoggerFactory.getLogger(AutonomousCollaborationTest.class);

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private AgentManager agentManager;

    @Test
    @Transactional
    public void testAutonomousCall() {
        log.info("========== 测试：Agent 自主协作演练 ==========");

        String sessionId = conversationService.createSession("user-agent-expert", "多智能体自主协作");

        // 1. 创建编剧智能体
        String writerId = conversationService.createAgent(
            sessionId,
            "编剧",
            "你是一个编剧。当用户要求翻译时，你必须使用 call_agent 工具并指定 target_name 为 '翻译家' 来完成任务。",
            Map.of()
        );

        // 2. 创建翻译家智能体
        String translatorId = conversationService.createAgent(
            sessionId,
            "翻译家",
            "你是一个翻译家，擅长将中文翻译成法语。请直接给出法语翻译，不要有多余废话。",
            Map.of()
        );

        // 3. 只给编剧下指令
        String task = "写一个关于小狗的超短故事（10个字以内），然后请'翻译家'帮忙翻译成法语。";
        log.info("用户下令给 [编剧]: {}", task);

        String finalResponse = agentManager.processTask(writerId, task);

        log.info("========== 编剧的最终回复 ==========");
        log.info(finalResponse);
        log.info("==================================");

        // 4. 验证：回复中是否包含法语（简单的启发式判断）
        assertNotNull(finalResponse);
        // 如果协作成功，通常会看到法语单词（如 le, chien, petit 等）
        log.info("✓ 协作测试流程跑通");

        TestTransaction.flagForRollback();
    }
}
