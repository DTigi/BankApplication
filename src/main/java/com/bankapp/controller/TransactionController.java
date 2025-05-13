package com.bankapp.controller;

import com.bankapp.model.Account;
import com.bankapp.model.Client;
import com.bankapp.repository.ClientRepository;
import com.bankapp.util.SessionManager;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/transactions")
public class TransactionController {
    private final SessionManager sessionManager;
    private Client recipientClient;
    private Account recipientAccount;

    public TransactionController(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    // 1️⃣ Получить список всех клиентов перед переводом
    @Operation(summary = "Список клиентов",
            description = "Выводит список всех клиентов перед переводом")
    @GetMapping("/clients")
    public List<Client> getAllClients() {
        return List.copyOf(ClientRepository.getAllClients());
    }

    // 2️⃣ Выбрать получателя перевода по имени и номеру счета
    @Operation(summary = "Выбор получателя перевода",
            description = "Выбрать получателя перевода по телефону и номеру счета")
    @PostMapping("/select-recipient")
    public String selectRecipient(@RequestParam String username, @RequestParam String accountNumber) {
        RestTemplate template = new RestTemplate();
        try {
            String response = template.getForEntity("http://localhost:8081/auth/current", String.class).getBody();
//            System.out.println(response);

            Optional<Client> recipientOpt = ClientRepository.findByUsername(username);
            if (recipientOpt.isEmpty()) {
                return "❌ Ошибка: Получатель не найден!";
            }

            Optional<Account> recipientAccountOpt = recipientOpt.get().getAccounts()
                    .stream()
                    .filter(a -> a.getAccountNumber().equals(accountNumber))
                    .findFirst();

            if (recipientAccountOpt.isEmpty()) {
                return "❌ Ошибка: У получателя нет такого счета!";
            }

            this.recipientClient = recipientOpt.get();
            this.recipientAccount = recipientAccountOpt.get();

            return "✅ Получатель выбран: " + recipientClient.getFullName() + " (Счет: " + recipientAccount.getAccountNumber() + ")";

        } catch (Exception e) {
            return "❌ Ошибка: Сначала войдите в систему!";
        }
    }

    // 3️⃣ Выполнить перевод (указать сумму и изменить баланс)
    @Operation(summary = "Выполнить перевод",
            description = "Выполнить перевод (указать сумму и изменить баланс)")
    @PostMapping("/transfer")
    public String transfer(@RequestParam double amount) {
        RestTemplate template = new RestTemplate();
        try {
            ResponseEntity<Client> response = template.getForEntity("http://localhost:8081/auth/current", Client.class);
            Client sender = response.getBody();
            System.out.println("Клиент: " + sender.getUsername());

            if (recipientClient == null || recipientAccount == null) {
                return "❌ Ошибка: Сначала выберите получателя!";
            }

            Optional<Account> senderAccountOpt = sender.getAccounts().stream().findFirst();
            if (senderAccountOpt.isEmpty()) {
                return "❌ Ошибка: У вас нет счета!";
            }

            Account senderAccount = senderAccountOpt.get();

            if (senderAccount.getBalance() < amount) {
                return "❌ Ошибка: Недостаточно средств на счете!";
            }

            // Обновляем балансы
            senderAccount.setBalance(senderAccount.getBalance() - amount);
            recipientAccount.setBalance(recipientAccount.getBalance() + amount);

            return "✅ Перевод завершен! " + amount + "₽ переведено на счет " + recipientAccount.getAccountNumber();
        } catch (HttpClientErrorException e) {
            System.out.println("HTTP ошибка: " + e.getStatusCode() + " — " + e.getResponseBodyAsString());
            return "❌ Ошибка: Сначала войдите в систему!";
        } catch (Exception e) {
            System.out.println("Ошибка десериализации или подключения: " + e.getMessage());
            return "❌ Ошибка: Ошибка десериализации или подключения";
        }
    }
}