package com.deepknow.agent.sdk.model;

import java.io.Serializable;
import java.time.LocalDateTime;

public class Message implements Serializable {
    private String role;
    private String content;
    private LocalDateTime timestamp;

    public Message() {}

    public Message(String role, String content, LocalDateTime timestamp) {
        this.role = role;
        this.content = content;
        this.timestamp = timestamp;
    }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String role;
        private String content;
        private LocalDateTime timestamp;

        public Builder role(String role) {
            this.role = role;
            return this;
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder timestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Message build() {
            return new Message(role, content, timestamp);
        }
    }
}
