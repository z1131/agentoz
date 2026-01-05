package com.deepknow.agent.sdk.agents;

/**
 * 对话智能体
 *
 * <p>使用示例：
 * <pre>
 * ChatAgent chat = new ChatAgent()
 *     .name("客服助手")
 *     .prompt("你是友好的客户服务代表")
 *     .temperature(0.9);
 *
 * String response = chat.reply("用户投诉产品有问题");
 *
 * // 流式对话（推荐用于实时场景）
 * chat.chatStream("用户咨询产品功能")
 *     .subscribe(content -> System.out.print(content));
 * </pre>
 *
 * @author Agent Platform
 * @version 1.0.0
 */
public class ChatAgent extends BaseAgent<ChatAgent> {

    public ChatAgent() {
        super();
        // 默认配置
        this.config.setAgentType("chat");
        this.config.setTemperature(0.9);  // 更高的创造性
        this.config.setMaxTokens(2000);
    }

    /**
     * 设置友好程度（影响温度）
     *
     * @param friendliness 友好程度 (0.0 - 1.0)
     * @return this
     */
    public ChatAgent friendliness(Double friendliness) {
        this.config.setTemperature(friendliness);
        return this;
    }

    /**
     * 对话（别名方法，语义更清晰）
     *
     * @param userMessage 用户消息
     * @return 智能体回复
     */
    public String reply(String userMessage) {
        return chat(userMessage);
    }

    /**
     * 流式对话（别名方法）
     *
     * @param userMessage 用户消息
     * @return 流式响应
     */
    public reactor.core.publisher.Flux<String> replyStream(String userMessage) {
        return chatStream(userMessage);
    }

    /**
     * 快速问答（适用于简单场景）
     *
     * @param question 问题
     * @return 答案
     */
    public String ask(String question) {
        return chat(question);
    }
}
