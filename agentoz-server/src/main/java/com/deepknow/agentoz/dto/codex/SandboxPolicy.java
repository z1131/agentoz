package com.deepknow.agentozoz.dto.codex;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

/**
 * 沙箱策略定义
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type"
)
@JsonSubTypes({
    @JsonSubTypes.Type(value = SandboxPolicy.DangerFullAccess.class, name = "danger-full-access"),
    @JsonSubTypes.Type(value = SandboxPolicy.ReadOnly.class, name = "read-only"),
    @JsonSubTypes.Type(value = SandboxPolicy.WorkspaceWrite.class, name = "workspace-write")
})
public abstract class SandboxPolicy {

    @NoArgsConstructor
    public static class DangerFullAccess extends SandboxPolicy {}

    @NoArgsConstructor
    public static class ReadOnly extends SandboxPolicy {}

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkspaceWrite extends SandboxPolicy {
        @JsonProperty("writable_roots")
        private List<String> writableRoots;

        @JsonProperty("network_access")
        private boolean networkAccess = false;
    }
}
