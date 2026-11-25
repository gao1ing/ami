package com.example.ami;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 启动 AMI 采集管理服务。 */
@SpringBootApplication
public class AmiApplication {
    /** 启动 Spring 应用。 */
    public static void main(String[] args) {
        SpringApplication.run(AmiApplication.class, args);
    }
}
