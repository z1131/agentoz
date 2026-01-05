package com.deepknow.nexus;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;

@SpringBootApplication
@EnableDubbo
@MapperScan("com.deepknow.nexus.infra.repo")
public class AgentNexusApplication {
    public static void main(String[] args) {
        SpringApplication.run(AgentNexusApplication.class, args);
    }
}