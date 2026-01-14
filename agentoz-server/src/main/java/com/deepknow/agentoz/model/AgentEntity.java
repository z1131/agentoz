package com.deepknow.agentoz.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.Base64;

/**
 * Agent业务实体（轻量级）
 *
 * <p>代表一个智能体实例，专注于业务属性和状态管理。
 * 计算配置通过 {@code configId} 关联到 {@link AgentConfigEntity}。</p>
 *
 * <h3>🔄 设计思想</h3>
 * <ul>
 *   <li><b>职责分离</b>: AgentEntity负责业务属性，AgentConfigEntity负责计算配置</li>
 *   <li><b>配置复用</b>: 多个Agent可以共享同一套配置（如同一套Qwen-Max配置）</li>
 *   <li><b>灵活扩展</b>: 配置变更不需要修改Agent表结构</li>
 * </ul>
 *
 * <h3>📊 核心字段</h3>
 * <ul>
 *   <li>agentId - Agent唯一标识</li>
 *   <li>conversationId - 所属会话ID（对齐Conversation）</li>
 *   <li>configId - 关联的配置ID（指向AgentConfigEntity）</li>
 *   <li>agentName - Agent显示名称</li>
 * </ul>
 *
 * <h3>🎯 与其他实体的关系</h3>
 * <pre>
 * ConversationEntity (会话)
 *   ├─ primaryAgentId → AgentEntity (主Agent)
 *   └─ 1:N → AgentEntity (参与会话的多个Agent)
 *                      ├─ configId → AgentConfigEntity (配置)
 *                      └─ state → 运行时状态
 * </pre>
 *
 * @see AgentConfigEntity
 * @see ConversationEntity
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("agents")
public class AgentEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * Agent唯一标识
     * 格式: "agent-{timestamp}-{random}"
     */
    private String agentId;

    /**
     * 所属会话ID（对齐ConversationEntity）
     */
    private String conversationId;

    /**
     * 关联的配置ID（指向AgentConfigEntity）
     * 通过此字段获取完整的计算配置
     */
    private String configId;

    // ============================================================
    // 业务属性 - Business Attributes
    // ============================================================

    /**
     * Agent显示名称
     * 示例: "代码助手", "数据分析专家"
     */
    @TableField("agent_name")
    private String agentName;

    /**
     * 是否主Agent
     */
    @TableField("is_primary")
    private Boolean isPrimary;

    /**
     * Agent描述
     */
    private String description;

    // ============================================================
    // 上下文管理 - Context Management
    // ============================================================

    /**
     * 全量历史记录（JSON格式）
     *
     * <p>包含该Agent参与的所有完整对话历史，不会被压缩</p>
     * <p>用于审计、回溯或需要完整历史的场景</p>
     * <p>⚠️ 此字段不参与 Codex 计算，仅用于业务展示</p>
     */
    private String fullHistory;

    /**
     * 活跃上下文（Base64 编码的 JSONL 二进制数据）
     *
     * <h3>🔄 新版设计 (对齐 Codex Adapter)</h3>
     * <p>此字段直接存储 Codex 返回的 {@code updated_rollout} 二进制数据，
     * 使用 Base64 编码以适配数据库 TEXT 类型。</p>
     *
     * <h3>📦 数据格式</h3>
     * <ul>
     *   <li>原始格式：Codex 原生 JSONL（每行一个 JSON 对象）</li>
     *   <li>存储格式：Base64(JSONL bytes)</li>
     * </ul>
     *
     * <h3>🔁 使用流程</h3>
     * <pre>
     * 1. 请求时：Base64 解码 → bytes → 传给 Codex 的 history_rollout
     * 2. 响应时：Codex 返回 updated_rollout → Base64 编码 → 存储
     * </pre>
     *
     * <p>⚠️ 此字段参与 Codex 计算，由 Codex 内部管理压缩和状态</p>
     */
    private String activeContext;

    // ============================================================
    // 状态与生命周期 - State & Lifecycle
    // ============================================================

    /**
     * Agent运行状态
     * 枚举: "ACTIVE", "INACTIVE", "ERROR"
     */
    private String state;

    /**
     * Agent 状态描述（新增）
     *
     * <p>记录 Agent 被调用时的输入摘要和执行结果摘要</p>
     *
     * <p>更新策略：</p>
     * <ul>
     *   <li>Agent 被调用时：更新为输入摘要，例如 "正在处理天气查询任务"</li>
     *   <li>Agent 返回时：追加执行结果，例如 "正在处理天气查询任务 | 已完成：北京晴天25°C"</li>
     * </ul>
     *
     * <p>格式示例：</p>
     * <pre>
     * "输入: 帮我查北京天气"
     * "输入: 帮我查北京天气 | 输出: 正在调用天气服务..."
     * "输入: 帮我查北京天气 | 输出: 北京今天晴天，温度25°C"
     * </pre>
     */
    private String stateDescription;

    /**
     * 交互次数统计
     *
     * <p>该 Agent 的总交互次数（调用+返回）</p>
     */
    private Integer interactionCount;

    /**
     * 最后交互类型
     *
     * <p>可能的值: input(被调用), output(返回), error(错误)</p>
     */
    private String lastInteractionType;

    /**
     * 最后交互时间
     */
    private LocalDateTime lastInteractionAt;

    /**
     * 优先级（用于多Agent调度）
     * 范围: 1-10，数字越大优先级越高
     */
    private Integer priority;

    /**
     * 扩展元数据（JSON格式）
     * 用于存储未预定义的扩展字段
     */
    private String metadata;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastUsedAt;

        /**

         * 创建者用户ID

         */

        private String createdBy;

    

        // ============================================================

        // 充血模型方法 (Rich Domain Methods)

        // ============================================================

        /**
         * 获取活跃上下文的原始字节数据
         *
         * <p>将 Base64 编码的 activeContext 解码为原始 JSONL 字节数组，
         * 用于传递给 Codex 的 history_rollout 字段</p>
         *
         * @return JSONL 格式的字节数组，如果为空则返回空数组
         */
        public byte[] getActiveContextBytes() {
            if (this.activeContext == null || this.activeContext.isEmpty()) {
                return new byte[0];
            }
            try {
                return Base64.getDecoder().decode(this.activeContext);
            } catch (IllegalArgumentException e) {
                // 兼容旧数据：如果不是 Base64 格式，可能是旧的 JSON 格式，返回空
                return new byte[0];
            }
        }

        /**
         * 设置活跃上下文（从字节数据）
         *
         * <p>将 Codex 返回的 updated_rollout 字节数组编码为 Base64 并存储</p>
         *
         * @param rolloutBytes Codex 返回的 JSONL 格式字节数组
         */
        public void setActiveContextFromBytes(byte[] rolloutBytes) {
            if (rolloutBytes == null || rolloutBytes.length == 0) {
                this.activeContext = null;
            } else {
                this.activeContext = Base64.getEncoder().encodeToString(rolloutBytes);
            }
        }

        /**
         * 检查是否有活跃上下文
         *
         * @return 如果有有效的活跃上下文则返回 true
         */
        public boolean hasActiveContext() {
            return this.activeContext != null && !this.activeContext.isEmpty();
        }

            /**
             * 更新输入状态
             *
             * @param inputMessage 输入消息
             * @param role 来源角色 (user 或 AgentName)
             */

            public void updateInputState(String inputMessage, String role) {

                String summary = generateSummary(inputMessage);
                String prefix;

                if (role == null || "user".equalsIgnoreCase(role)) {
                    prefix = "输入: ";
                } else {
                    prefix = "[From " + role + "]: ";
                }
                if (this.stateDescription == null || this.stateDescription.isEmpty()) {
                    this.stateDescription = prefix + summary;
                } else {
                    this.stateDescription = this.stateDescription + " | " + prefix + summary;
                }
                this.interactionCount = (this.interactionCount != null ? this.interactionCount : 0) + 1;
                this.lastInteractionType = "input";
                this.lastInteractionAt = LocalDateTime.now();
            }
        /**
         * 更新输出状态
         *
         * @param responseMessage 输出消息
         */
        public void updateOutputState(String responseMessage) {

            String summary = generateSummary(responseMessage);
            String prefix = "输出: ";
            if (this.stateDescription == null || this.stateDescription.isEmpty()) {
                this.stateDescription = prefix + summary;
            } else {
                this.stateDescription = this.stateDescription + " | " + prefix + summary;
            }
            this.interactionCount = (this.interactionCount != null ? this.interactionCount : 0) + 1;
            this.lastInteractionType = "output";
            this.lastInteractionAt = LocalDateTime.now();
        }

        private String generateSummary(String text) {
            if (text == null) return "";
            String summary = text.length() > 50 ? text.substring(0, 50) + "..." : text;
            return summary.replace("\n", " ");
        }

    }

    