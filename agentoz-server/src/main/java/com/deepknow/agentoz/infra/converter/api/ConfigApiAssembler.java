package com.deepknow.agentoz.infra.converter.api;

import com.deepknow.agentoz.api.dto.ModelOverridesDTO;
import com.deepknow.agentoz.api.dto.McpServerConfigDTO;
import com.deepknow.agentoz.api.dto.ProviderConfigDTO;
import com.deepknow.agentoz.api.dto.SessionSourceDTO;
import com.deepknow.agentoz.dto.config.ModelOverridesVO;
import com.deepknow.agentoz.dto.config.McpServerConfigVO;
import com.deepknow.agentoz.dto.config.ProviderConfigVO;
import com.deepknow.agentoz.dto.config.SessionSourceVO;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * DTO转换器 - API层DTO到Server层VO
 *
 * <p>负责将API层的DTO(ProviderConfigDTO, McpServerConfigDTO等)
 * 转换为Server层的VO(ProviderConfigVO, McpServerConfigVO等)。</p>
 *
 * <h3>🔄 转换映射</h3>
 * <pre>
 * API层DTO                   →  Server层VO
 *   ProviderConfigDTO        →   ProviderConfigVO
 *   McpServerConfigDTO       →   McpServerConfigVO
 *   ModelOverridesDTO        →   ModelOverridesVO
 *   SessionSourceDTO         →   SessionSourceVO
 * </pre>
 *
 * @see com.deepknow.agentoz.api.dto
 * @see com.deepknow.agentoz.dto.config
 */
@Component
public class ConfigApiAssembler {

    /**
     * 转换ProviderConfigDTO → ProviderConfigVO
     */
    public static ProviderConfigVO toProviderConfig(ProviderConfigDTO dto) {
        if (dto == null) {
            return null;
        }

        return ProviderConfigVO.builder()
                .name(dto.getName())
                .baseUrl(dto.getBaseUrl())
                .apiKey(dto.getApiKey())
                .wireApi(dto.getWireApi())
                .build();
    }

    /**
     * 转换McpServerConfigDTO → McpServerConfigVO
     */
    public static McpServerConfigVO toMcpServerConfig(McpServerConfigDTO dto) {
        if (dto == null) {
            return null;
        }

        return McpServerConfigVO.builder()
                .command(dto.getCommand())
                .args(dto.getArgs())
                .env(dto.getEnv())
                .build();
    }

    /**
     * 转换ModelOverridesDTO → ModelOverridesVO
     */
    public static ModelOverridesVO toModelOverrides(ModelOverridesDTO dto) {
        if (dto == null) {
            return null;
        }

        return ModelOverridesVO.builder()
                .shellType(dto.getShellType())
                .supportsParallelToolCalls(dto.getSupportsParallelToolCalls())
                .applyPatchToolType(dto.getApplyPatchToolType())
                .contextWindow(dto.getContextWindow())
                .autoCompactTokenLimit(dto.getAutoCompactTokenLimit())
                .build();
    }

    /**
     * 转换SessionSourceDTO → SessionSourceVO
     */
    public static SessionSourceVO toSessionSource(SessionSourceDTO dto) {
        if (dto == null) {
            return null;
        }

        return SessionSourceVO.builder()
                .sourceType(dto.getSourceType())
                .integrationName(dto.getIntegrationName())
                .integrationVersion(dto.getIntegrationVersion())
                .build();
    }

    /**
     * 转换Map<String, McpServerConfigDTO> → Map<String, McpServerConfigVO>
     */
    public static Map<String, McpServerConfigVO> toMcpServerConfigMap(Map<String, McpServerConfigDTO> dtoMap) {
        if (dtoMap == null || dtoMap.isEmpty()) {
            return new HashMap<>();
        }

        return dtoMap.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> toMcpServerConfig(entry.getValue())
                ));
    }
}
