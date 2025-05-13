package com.bankapp.controller;

import com.bankapp.model.Account;
import com.bankapp.model.Client;
import com.bankapp.repository.ClientRepository;
import com.bankapp.util.SessionManager;
import io.micrometer.core.instrument.*;
import io.micrometer.observation.annotation.Observed;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
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

    private final MeterRegistry meterRegistry;
    private final Counter transferCounter;
    private final DistributionSummary amountSummary;
    private final Timer transferTimer;

    private Client recipientClient;
    private Account recipientAccount;

    @Autowired
    public TransactionController(SessionManager sessionManager, MeterRegistry meterRegistry) {
        this.sessionManager = sessionManager;
        this.meterRegistry = meterRegistry;

        // Метрики
        this.transferCounter = meterRegistry.counter("transactions.count");
        this.amountSummary = DistributionSummary.builder("transactions.amounts")
                .baseUnit("rubles")
                .description("Суммы переводов")
                .register(meterRegistry);
        this.transferTimer = Timer.builder("transactions.transfer.time")
                .description("Время выполнения перевода")
                .register(meterRegistry);

        Gauge.builder("transactions.clients.total", () -> ClientRepository.getAllClients().size())
                .description("Количество клиентов")
                .register(meterRegistry);
    }

    @Operation(summary = "Список клиентов", description = "Выводит список всех клиентов перед переводом")
    @GetMapping("/clients")
    @Observed(name = "transactions.getAllClients")
    public List<Client> getAllClients() {
        return List.copyOf(ClientRepository.getAllClients());
    }

    @Operation(summary = "Выбор получателя перевода", description = "Выбрать получателя перевода по телефону и номеру счета")
    @PostMapping("/select-recipient")
    @Observed(name = "transactions.selectRecipient")
    public String selectRecipient(@RequestParam String username, @RequestParam String accountNumber) {
        RestTemplate template = new RestTemplate();
        try {
            String response = template.getForEntity("http://localhost:8081/auth/current", String.class).getBody();

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

            return "✅ Получатель выбран: " + recipientClient.getFullName() +
                    " (Счет: " + recipientAccount.getAccountNumber() + ")";
        } catch (Exception e) {
            return "❌ Ошибка: Сначала войдите в систему!";
        }
    }

    @Operation(summary = "Выполнить перевод", description = "Выполнить перевод (указать сумму и изменить баланс)")
    @PostMapping("/transfer")
    @Observed(name = "transactions.transfer")
    public String transfer(@RequestParam double amount) {
        return transferTimer.record(() -> {
            RestTemplate template = new RestTemplate();
            try {
                ResponseEntity<Client> response = template.getForEntity("http://localhost:8081/auth/current", Client.class);
                Client sender = response.getBody();

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

                // Метрики
                transferCounter.increment();
                amountSummary.record(amount);

                return "✅ Перевод завершен! " + amount + "₽ переведено на счет " + recipientAccount.getAccountNumber();
            } catch (HttpClientErrorException e) {
                return "❌ Ошибка: Сначала войдите в систему!";
            } catch (Exception e) {
                return "❌ Ошибка: Ошибка десериализации или подключения";
            }
        });
    }
}
