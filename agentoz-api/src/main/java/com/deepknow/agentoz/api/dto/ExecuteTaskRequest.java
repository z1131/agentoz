package com.deepknow.agentoz.api.dto;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 执行任务请求
 */
public class ExecuteTaskRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 指定执行任务的 Agent ID (可选)
     */
    private String agentId;

    /**
     * 所属会话 ID (若 agentId 为空，则默认使用该会话的主智能体)
     */
    private String conversationId;

    /**
     * 用户输入的指令内容
     */
    private String message;

    /**
     * 运行时覆盖配置 (可选)
     */
    private Map<String, Object> overrides;

    /**
     * 消息发送者角色 (user / assistant)
     */
    private String role;

    /**
     * 发送者名称 (用于业务显示)
     */
    private String senderName;

    /**
     * 附件列表 (可选)
     * <p>用于携带文件附件信息，支持多模态交互</p>
     */
    private List<AttachmentInfo> attachments;

    /**
     * 附件信息
     */
    public static class AttachmentInfo implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * 附件ID
         */
        private Long id;

        /**
         * 文件名
         */
        private String fileName;

        /**
         * 文件访问 URL
         */
        private String fileUrl;

        /**
         * 文件类型 (MIME 类型)
         */
        private String mimeType;

        /**
         * 附件类型 (REFERENCE_PDF, FIGURE, TABLE, etc.)
         */
        private String type;

        /**
         * 文件大小 (字节)
         */
        private Long fileSize;

        public AttachmentInfo() {}

        public AttachmentInfo(Long id, String fileName, String fileUrl,
                            String mimeType, String type, Long fileSize) {
            this.id = id;
            this.fileName = fileName;
            this.fileUrl = fileUrl;
            this.mimeType = mimeType;
            this.type = type;
            this.fileSize = fileSize;
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }

        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }

        public String getFileUrl() { return fileUrl; }
        public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }

        public String getMimeType() { return mimeType; }
        public void setMimeType(String mimeType) { this.mimeType = mimeType; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public Long getFileSize() { return fileSize; }
        public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    }

    public ExecuteTaskRequest() {}

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }

    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Map<String, Object> getOverrides() { return overrides; }
    public void setOverrides(Map<String, Object> overrides) { this.overrides = overrides; }

    public List<AttachmentInfo> getAttachments() { return attachments; }
    public void setAttachments(List<AttachmentInfo> attachments) { this.attachments = attachments; }
}
