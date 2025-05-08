package com.bankapp.controller;

import com.bankapp.model.Account;
import com.bankapp.service.AccountService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/accounts")
public class AccountController {
    public AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping("/create")
    public Account create(@RequestParam String clientId) {
        return accountService.createAccount(clientId);
    }
}
