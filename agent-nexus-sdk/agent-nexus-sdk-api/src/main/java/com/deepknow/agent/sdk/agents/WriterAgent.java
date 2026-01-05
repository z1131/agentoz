package com.deepknow.agent.sdk.agents;

/**
 * 写作智能体
 *
 * <p>使用示例：
 * <pre>
 * WriterAgent writer = new WriterAgent()
 *     .name("论文写作助手")
 *     .prompt("你是专业的AI论文写作专家")
 *     .domain("Artificial Intelligence")
 *     .style("academic");
 *
 * // 写摘要
 * String abstractContent = writer.write(
 *     "写一篇关于\"大语言模型在智能教育中的应用\"的论文摘要，" +
 *     "字数300字，包含研究背景、方法和创新点"
 * );
 *
 * // 续写内容
 * String introduction = writer.continueWrite(
 *     existingContent,
 *     "基于上述摘要，写引言部分"
 * );
 * </pre>
 *
 * @author Agent Platform
 * @version 1.0.0
 */
public class WriterAgent extends BaseAgent<WriterAgent> {

    public WriterAgent() {
        super();
        // 默认配置
        this.config.setAgentType("writer");
        this.config.setTemperature(0.7);  // 平衡创造性和准确性
        this.config.setMaxTokens(4000);   // 写作需要更长输出
    }

    /**
     * 设置写作风格
     *
     * @param style 风格 (academic, casual, professional)
     * @return this
     */
    public WriterAgent style(String style) {
        this.context("writing_style", style);
        return this;
    }

    /**
     * 设置写作领域
     *
     * @param domain 领域 (AI, Medicine, Law...)
     * @return this
     */
    public WriterAgent domain(String domain) {
        this.context("domain", domain);
        return this;
    }

    /**
     * 设置目标语言
     *
     * @param language 语言
     * @return this
     */
    public WriterAgent language(String language) {
        this.context("language", language);
        return this;
    }

    /**
     * 写作（别名方法，语义更清晰）
     *
     * @param instruction 写作指令
     * @return 写作内容
     */
    public String write(String instruction) {
        return chat(instruction);
    }

    /**
     * 续写（基于已有内容继续写作）
     *
     * @param existingContent 已有内容
     * @param instruction 续写指令
     * @return 续写内容
     */
    public String continueWrite(String existingContent, String instruction) {
        initSession();

        StringBuilder prompt = new StringBuilder();
        prompt.append("【已有内容】\n").append(existingContent).append("\n\n");
        prompt.append("【续写要求】\n").append(instruction);

        return chat(prompt.toString());
    }

    /**
     * 润色（改进已有文本）
     *
     * @param content 原始内容
     * @return 润色后的内容
     */
    public String polish(String content) {
        return chat("请润色以下文本，使其更加流畅和专业：\n\n" + content);
    }

    /**
     * 总结（生成摘要）
     *
     * @param content 原始内容
     * @return 摘要
     */
    public String summarize(String content) {
        return chat("请为以下内容生成简洁的摘要：\n\n" + content);
    }
}
