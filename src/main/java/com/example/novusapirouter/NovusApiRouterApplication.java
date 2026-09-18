package com.example.novusapirouter;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.example.novusapirouter.server.mapper")
public class NovusApiRouterApplication {

    public static void main(String[] args) {
        SpringApplication.run(NovusApiRouterApplication.class, args);
    }

}
