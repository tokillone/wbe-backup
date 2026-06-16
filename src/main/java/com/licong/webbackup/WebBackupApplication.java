package com.licong.webbackup;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.mybatis.spring.annotation.MapperScan;

@SpringBootApplication
@MapperScan("com.licong.webbackup.mapper")
public class WebBackupApplication {

    public static void main(String[] args) {
        SpringApplication.run(WebBackupApplication.class, args);
    }

}
