package com.deepknow.agent.api.dto;

import java.io.Serializable;
import java.util.List;

public class MessageListResponse implements Serializable {
    private List<MessageDTO> messages;
    private long total;

    public MessageListResponse() {}

    public MessageListResponse(List<MessageDTO> messages, long total) {
        this.messages = messages;
        this.total = total;
    }

    public List<MessageDTO> getMessages() { return messages; }
    public void setMessages(List<MessageDTO> messages) { this.messages = messages; }

    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private List<MessageDTO> messages;
        private long total;

        public Builder messages(List<MessageDTO> messages) {
            this.messages = messages;
            return this;
        }

        public Builder total(long total) {
            this.total = total;
            return this;
        }

        public MessageListResponse build() {
            return new MessageListResponse(messages, total);
        }
    }
}
