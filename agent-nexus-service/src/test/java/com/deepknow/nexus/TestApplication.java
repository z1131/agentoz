package com.deepknow.nexus;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 测试应用启动类
 *
 * 测试时不启用 Dubbo
 */
@SpringBootApplication(exclude = {
    org.apache.dubbo.spring.boot.autoconfigure.DubboAutoConfiguration.class,
    org.apache.dubbo.spring.boot.autoconfigure.DubboRelaxedBinding2AutoConfiguration.class
})
@MapperScan("com.deepknow.platform.infrastructure.persistence")
public class TestApplication {
    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }
}
