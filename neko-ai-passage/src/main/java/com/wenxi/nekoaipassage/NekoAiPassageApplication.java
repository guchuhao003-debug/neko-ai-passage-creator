package com.wenxi.nekoaipassage;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@SpringBootApplication
@MapperScan("com.wenxi.nekoaipassage.mapper")
@EnableAspectJAutoProxy(exposeProxy = true)    // 启动 AOP
public class NekoAiPassageApplication {

    public static void main(String[] args) {
        SpringApplication.run(NekoAiPassageApplication.class, args);
    }

}
