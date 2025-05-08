package com.bankapp.standartmock;

import com.bankapp.controller.AccountController;
import com.bankapp.service.AccountService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@SpringBootApplication(scanBasePackages = "com.bankapp")
public class MyMock {
    public static void main(String[] args) {
        SpringApplication.run(MyMock.class, args);
    }
}
//Spring Boot
//Spring Framework
//ApplicationServer - Tomcat
//        Map<String,Object>