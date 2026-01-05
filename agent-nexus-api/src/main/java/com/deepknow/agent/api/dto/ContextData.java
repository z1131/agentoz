package com.deepknow.agent.api.dto;

import java.io.Serializable;
import java.util.Map;

public class ContextData implements Serializable {
    private String key;
    private Object value;
    private Map<String, Object> metadata;

    public ContextData() {}

    public ContextData(String key, Object value, Map<String, Object> metadata) {
        this.key = key;
        this.value = value;
        this.metadata = metadata;
    }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public Object getValue() { return value; }
    public void setValue(Object value) { this.value = value; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String key;
        private Object value;
        private Map<String, Object> metadata;

        public Builder key(String key) {
            this.key = key;
            return this;
        }

        public Builder value(Object value) {
            this.value = value;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public ContextData build() {
            return new ContextData(key, value, metadata);
        }
    }
}
