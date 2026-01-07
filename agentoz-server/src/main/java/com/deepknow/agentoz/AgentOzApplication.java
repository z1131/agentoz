package com.deepknow.agentozoz;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;

@SpringBootApplication
@EnableDubbo
@MapperScan("com.deepknow.agentozoz.infra.repo")
public class AgentOzApplication {
    public static void main(String[] args) {
        SpringApplication.run(AgentOzApplication.class, args);
    }
}