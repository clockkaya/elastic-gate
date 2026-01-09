package com.sama.notice;

import org.apache.dubbo.config.spring.context.annotation.DubboComponentScan;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @Author HP
 * @Date 18/3/2024 09:40
 * @Version 1.0
 */

@DubboComponentScan
@SpringBootApplication(scanBasePackages = {"com.sama", "com.core4ct"})
@MapperScan("com.sama.notice.mapper")
public class SamaNoticeApplication {
    public static void main(String[] args) {
        SpringApplication.run(SamaNoticeApplication.class, args);
        System.out.println(
                " _____                      ___  _____  _____ \n" +
                        "/  __ \\                    /   |/  __ \\|_   _|\n" +
                        "| /  \\/  ___   _ __  ___  / /| || /  \\/  | |  \n" +
                        "| |     / _ \\ | '__|/ _ \\/ /_| || |      | |  \n" +
                        "| \\__/\\| (_) || |  |  __/\\___  || \\__/\\  | |  \n" +
                        " \\____/ \\___/ |_|   \\___|    |_/ \\____/  \\_/ \n" +
                        "----------------Run Success-------------------"
        );
    }
}