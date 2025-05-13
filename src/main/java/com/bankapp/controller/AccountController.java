package com.bankapp.controller;

import com.bankapp.model.Account;
import com.bankapp.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/accounts")
public class AccountController {
    public AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @Operation(summary = "Создать аккаунт",
            description = "Создать аккаунт")
    @PostMapping("/create")
    public Account create(@RequestParam String clientId) {
        return accountService.createAccount(clientId);
    }
}
