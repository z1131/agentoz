package com.deepknow.agent.starter.config;

import com.deepknow.agent.api.annotation.AgentTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;

/**
 * 扫描带有 @AgentTool 注解的方法
 * 目前仅打印日志，不做实际注册
 */
public class AgentToolScanner implements BeanPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(AgentToolScanner.class);

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        // 扫描 Bean 中的所有方法
        ReflectionUtils.doWithMethods(bean.getClass(), method -> {
            AgentTool annotation = AnnotationUtils.findAnnotation(method, AgentTool.class);
            if (annotation != null) {
                // 找到工具！
                String toolName = annotation.name().isEmpty() ? method.getName() : annotation.name();
                String desc = annotation.description();
                
                log.info(">>> [AgentOZ] 发现工具: {} (Bean: {}, Method: {})", toolName, beanName, method.getName());
                log.info("    描述: {}", desc);
                
                // TODO: 将来这里会收集 ToolDefinition 并调用 AgentService.registerTools()
            }
        });
        
        return bean;
    }
}
