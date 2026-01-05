package com.deepknow.agent.sdk.model.mcp;

import java.io.Serializable;
import java.util.Map;

public class McpServerConfig implements Serializable {
    private String id;
    private String name;
    private String type; // stdio, sse
    private String command;
    private String[] args;
    private String url;
    private Map<String, String> env;

    public McpServerConfig() {}

    public McpServerConfig(String id, String name, String type, String command, String[] args, String url, Map<String, String> env) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.command = command;
        this.args = args;
        this.url = url;
        this.env = env;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getCommand() { return command; }
    public void setCommand(String command) { this.command = command; }

    public String[] getArgs() { return args; }
    public void setArgs(String[] args) { this.args = args; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public Map<String, String> getEnv() { return env; }
    public void setEnv(Map<String, String> env) { this.env = env; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String name;
        private String type;
        private String command;
        private String[] args;
        private String url;
        private Map<String, String> env;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder command(String command) {
            this.command = command;
            return this;
        }

        public Builder args(String[] args) {
            this.args = args;
            return this;
        }

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public Builder env(Map<String, String> env) {
            this.env = env;
            return this;
        }

        public McpServerConfig build() {
            return new McpServerConfig(id, name, type, command, args, url, env);
        }
    }
}
