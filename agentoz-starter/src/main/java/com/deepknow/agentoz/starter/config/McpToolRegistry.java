package com.deepknow.agentoz.starter.config;

import com.deepknow.agentoz.starter.annotation.AgentParam;
import com.deepknow.agentoz.starter.annotation.AgentTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

/**
 * MCP 工具注册中心
 * <p>
 * 自动扫描带有 @AgentTool 注解的 Bean，并将其注册为 MCP Tool。
 * </p>
 */
@Slf4j
public class McpToolRegistry implements ApplicationContextAware {

    private ApplicationContext applicationContext;
    private final ObjectMapper objectMapper;
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    public McpToolRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    /**
     * 扫描并生成 Tool Specifications
     */
    public List<SyncToolSpecification> scanAndBuildTools() {
        List<SyncToolSpecification> specs = new ArrayList<>();
        String[] beanNames = applicationContext.getBeanDefinitionNames();

        for (String beanName : beanNames) {
            // 1. 跳过自身和基础设施 Bean，防止循环依赖
            if ("mcpStatelessSyncServer".equals(beanName) || 
                "mcpToolRegistry".equals(beanName) ||
                "mcpServerAutoConfiguration".equals(beanName) ||
                beanName.startsWith("org.springframework")) {
                continue;
            }

            try {
                // 2. 安全获取 Bean (如果 Bean 正在创建中导致循环依赖，这里会抛异常)
                Object bean = applicationContext.getBean(beanName);
                
                // 处理 AOP 代理，获取原始类
                Class<?> beanClass = AopUtils.getTargetClass(bean);

                ReflectionUtils.doWithMethods(beanClass, method -> {
                    if (method.isAnnotationPresent(AgentTool.class)) {
                        try {
                            specs.add(buildToolSpec(bean, method));
                            log.info("🔨 [MCP] 注册工具: {} -> {}.{}", 
                                    getToolName(method), beanClass.getSimpleName(), method.getName());
                        } catch (Exception e) {
                            log.error("❌ [MCP] 注册工具失败: {}.{}", beanClass.getSimpleName(), method.getName(), e);
                        }
                    }
                });
            } catch (Exception e) {
                // 忽略无法初始化的 Bean (通常是因为循环依赖或其他配置问题)
                // 这保证了 MCP Server 的启动不会因为某个无关 Bean 的错误而崩溃
                log.debug("⚠️ [MCP] 跳过 Bean 扫描 (可能是循环依赖): {} - {}", beanName, e.getMessage());
            }
        }
        return specs;
    }

    private String getToolName(Method method) {
        AgentTool annotation = method.getAnnotation(AgentTool.class);
        return StringUtils.hasText(annotation.name()) ? annotation.name() : method.getName();
    }

    private SyncToolSpecification buildToolSpec(Object bean, Method method) {
        AgentTool annotation = method.getAnnotation(AgentTool.class);
        String name = getToolName(method);
        String description = annotation.description();

        // 1. 生成 Input Schema
        Map<String, Object> inputSchema = generateInputSchema(method);
        String inputSchemaJson;
        try {
            inputSchemaJson = objectMapper.writeValueAsString(inputSchema);
        } catch (Exception e) {
            throw new RuntimeException("Schema 生成失败", e);
        }
        
        McpSchema.Tool toolDefinition = McpSchema.Tool.builder()
                .name(name)
                .description(description)
                .inputSchema(inputSchemaJson)
                .build();

        // 2. 构建执行闭包 (Context, Request) -> Result
        return new SyncToolSpecification(toolDefinition, (ctx, request) -> {
            try {
                // 参数映射
                Map<String, Object> argsMap = request.arguments();
                Object[] args = resolveArguments(method, argsMap);
                
                // 执行调用
                Object result = method.invoke(bean, args);
                
                // 处理结果
                String resultStr = result != null ? parseResult(result) : "execution_success";
                return new CallToolResult(List.of(new TextContent(resultStr)), false);
            } catch (Exception e) {
                log.error("工具执行异常: {}", name, e);
                // 获取根本原因
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                return new CallToolResult(List.of(new TextContent("Error: " + cause.getMessage())), true);
            }
        });
    }

    private String parseResult(Object result) {
        if (result instanceof String) {
            return (String) result;
        }
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            return result.toString();
        }
    }

    /**
     * 生成 JSON Schema (Draft 2020-12)
     */
    private Map<String, Object> generateInputSchema(Method method) {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        
        Map<String, Object> properties = new HashMap<>();
        List<String> required = new ArrayList<>();
        
        Parameter[] parameters = method.getParameters();
        String[] paramNames = parameterNameDiscoverer.getParameterNames(method);

        for (int i = 0; i < parameters.length; i++) {
            Parameter param = parameters[i];
            
            AgentParam paramAnnotation = param.getAnnotation(AgentParam.class);
            
            // 获取参数名：注解 > 反射 > argN
            String paramName = (paramAnnotation != null && StringUtils.hasText(paramAnnotation.name()))
                    ? paramAnnotation.name()
                    : (paramNames != null && paramNames.length > i ? paramNames[i] : param.getName());

            Map<String, Object> propDef = new HashMap<>();
            
            // 类型推断
            Class<?> type = param.getType();
            if (type == String.class) {
                propDef.put("type", "string");
            } else if (type == int.class || type == Integer.class || type == long.class || type == Long.class) {
                propDef.put("type", "integer");
            } else if (type == boolean.class || type == Boolean.class) {
                propDef.put("type", "boolean");
            } else if (type == double.class || type == float.class) {
                propDef.put("type", "number");
            } else {
                propDef.put("type", "string"); // 默认为 string，复杂对象暂不支持自动 schema
            }

            if (paramAnnotation != null) {
                propDef.put("description", paramAnnotation.value());
                if (paramAnnotation.required()) {
                    required.add(paramName);
                }
            } else {
                // 没有注解默认必填
                required.add(paramName);
            }

            properties.put(paramName, propDef);
        }

        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        
        return schema;
    }

    /**
     * 将 Map arguments 映射为 Java 方法参数
     */
    private Object[] resolveArguments(Method method, Map<String, Object> arguments) {
        Parameter[] parameters = method.getParameters();
        String[] paramNames = parameterNameDiscoverer.getParameterNames(method);
        Object[] args = new Object[parameters.length];

        for (int i = 0; i < parameters.length; i++) {
            Parameter param = parameters[i];
            
            AgentParam paramAnnotation = param.getAnnotation(AgentParam.class);
            
            String paramName = (paramAnnotation != null && StringUtils.hasText(paramAnnotation.name()))
                    ? paramAnnotation.name()
                    : (paramNames != null && paramNames.length > i ? paramNames[i] : param.getName());
            
            Object val = arguments.get(paramName);
            
            // 简单类型转换
            if (val == null) {
                args[i] = null; // 可能会报 NPE 如果是基本类型，暂不处理
            } else {
                args[i] = convertValue(val, param.getType());
            }
        }
        return args;
    }

    private Object convertValue(Object val, Class<?> targetType) {
        if (targetType.isInstance(val)) {
            return val;
        }
        // 使用 Jackson 转换
        return objectMapper.convertValue(val, targetType);
    }
}
