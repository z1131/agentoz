package com.deepknow.agent.sdk.agents;

/**
 * 代码智能体
 *
 * <p>使用示例：
 * <pre>
 * CodeAgent coder = new CodeAgent()
 *     .name("代码助手")
 *     .language("Java")
 *     .temperature(0.2);  // 更低，确保准确性
 *
 * // 生成代码
 * String code = coder.generate("写一个快速排序算法");
 *
 * // 代码审查
 * String review = coder.review("public void test() {...}");
 *
 * // 解释代码
 * String explanation = coder.explain("for (int i = 0; i < 10; i++) {...}");
 * </pre>
 *
 * @author Agent Platform
 * @version 1.0.0
 */
public class CodeAgent extends BaseAgent<CodeAgent> {

    public CodeAgent() {
        super();
        // 默认配置
        this.config.setAgentType("coder");
        this.config.setTemperature(0.2);  // 更低，确保准确性
        this.config.setMaxTokens(4000);
    }

    /**
     * 设置编程语言
     *
     * @param language 语言 (Java, Python, JavaScript...)
     * @return this
     */
    public CodeAgent language(String language) {
        this.context("language", language);
        return this;
    }

    /**
     * 生成代码
     *
     * @param requirement 需求描述
     * @return 生成的代码
     */
    public String generate(String requirement) {
        return chat("请生成以下代码：\n" + requirement);
    }

    /**
     * 代码审查
     *
     * @param code 代码
     * @return 审查意见
     */
    public String review(String code) {
        return chat("请审查以下代码，指出潜在问题和改进建议：\n\n" + code);
    }

    /**
     * 解释代码
     *
     * @param code 代码
     * @return 代码解释
     */
    public String explain(String code) {
        return chat("请解释以下代码的功能和逻辑：\n\n" + code);
    }

    /**
     * 优化代码
     *
     * @param code 原始代码
     * @return 优化后的代码
     */
    public String optimize(String code) {
        return chat("请优化以下代码，提高性能和可读性：\n\n" + code);
    }

    /**
     * 添加单元测试
     *
     * @param code 代码
     * @return 单元测试代码
     */
    public String addTests(String code) {
        return chat("请为以下代码编写完整的单元测试：\n\n" + code);
    }

    /**
     * 代码重构
     *
     * @param code 原始代码
     * @param instruction 重构指令
     * @return 重构后的代码
     */
    public String refactor(String code, String instruction) {
        return chat("请根据以下要求重构代码：\n" +
            instruction + "\n\n原始代码：\n" + code);
    }
}
