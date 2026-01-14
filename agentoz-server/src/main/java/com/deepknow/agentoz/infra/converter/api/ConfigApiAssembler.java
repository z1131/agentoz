package com.deepknow.agentoz.infra.converter.api;

import com.deepknow.agentoz.api.dto.McpServerConfigDTO;
import com.deepknow.agentoz.api.dto.ProviderConfigDTO;
import com.deepknow.agentoz.dto.config.McpServerConfigVO;
import com.deepknow.agentoz.dto.config.ModelProviderInfoVO;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * DTO转换器 - API层DTO到Server层VO (对齐 adapter.proto)
 *
 * <p>负责将API层的DTO转换为Server层的VO。</p>
 *
 * <h3>🔄 转换映射</h3>
 * <pre>
 * API层DTO                   →  Server层VO
 *   ProviderConfigDTO        →   ModelProviderInfoVO
 *   McpServerConfigDTO       →   McpServerConfigVO
 * </pre>
 *
 * @see com.deepknow.agentoz.api.dto
 * @see com.deepknow.agentoz.dto.config
 */
@Component
public class ConfigApiAssembler {

    /**
     * 转换 ProviderConfigDTO → ModelProviderInfoVO
     *
     * <p>适配 adapter.proto 中的 ModelProviderInfo 结构</p>
     */
    public static ModelProviderInfoVO toModelProviderInfo(ProviderConfigDTO dto) {
        if (dto == null) {
            return null;
        }

        return ModelProviderInfoVO.builder()
                .name(dto.getName())
                .baseUrl(dto.getBaseUrl())
                .experimentalBearerToken(dto.getApiKey())  // apiKey → experimentalBearerToken
                .wireApi(dto.getWireApi())
                .requiresOpenaiAuth(false)  // 默认值
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
