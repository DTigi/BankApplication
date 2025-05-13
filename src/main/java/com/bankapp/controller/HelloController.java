package com.bankapp.controller;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @Operation(summary = "Привет",
            description = "Выводит строку приветствия")
    @GetMapping("/hello")
    public String sayHello(@RequestParam(defaultValue = "Гость") String name) {
        return "Привет, " + name + "!";
    }
}