package com.dongxiang;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class DongxiangApplication {
    public static void main(String[] args) { SpringApplication.run(DongxiangApplication.class, args); }
}
