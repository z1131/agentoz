package com.deepknow.agent.api.dto;

import java.io.Serializable;
import java.util.Map;

public class ContextDTO implements Serializable {
    private String id;
    private String key;
    private Object value;
    private Map<String, Object> metadata;

    public ContextDTO() {}

    public ContextDTO(String id, String key, Object value, Map<String, Object> metadata) {
        this.id = id;
        this.key = key;
        this.value = value;
        this.metadata = metadata;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

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
        private String id;
        private String key;
        private Object value;
        private Map<String, Object> metadata;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

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

        public ContextDTO build() {
            return new ContextDTO(id, key, value, metadata);
        }
    }
}
