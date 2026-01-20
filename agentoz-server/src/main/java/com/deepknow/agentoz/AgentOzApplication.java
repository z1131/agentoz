package com.deepknow.agentoz;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDubbo
@EnableDiscoveryClient
@MapperScan("com.deepknow.agentoz.mapper")
public class AgentOzApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentOzApplication.class, args);
    }
}
