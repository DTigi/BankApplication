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

import java.util.List;
import java.util.Optional;
import jakarta.annotation.PostConstruct;

@RestController
@RequestMapping("/transactions")
public class TransactionController {
    private final SessionManager sessionManager;
    @Autowired
    private MeterRegistry meterRegistry;

    private Counter transferCounter, select_recipientCounter;
    private DistributionSummary amountSummary;
    private Timer transferTimer, select_recipientTimer;

    public TransactionController(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @PostConstruct
    private void initMetrics() {
        // Метрики
        this.transferCounter = meterRegistry.counter("transactions.count");
        this.select_recipientCounter = meterRegistry.counter("select_recipient.count");
        this.amountSummary = DistributionSummary.builder("transactions.amounts")
                .baseUnit("rubles")
                .description("Суммы переводов")
                .register(meterRegistry);
        this.transferTimer = Timer.builder("transactions.transfer.time")
                .description("Время выполнения перевода")
                .publishPercentiles(0.9)
                .publishPercentileHistogram()
                .register(meterRegistry);
        this.select_recipientTimer = Timer.builder("select.recipient.time")
                .description("Время выбора получателя")
                .publishPercentiles(0.9)
                .publishPercentileHistogram()
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
        return select_recipientTimer.record(() -> {
            select_recipientCounter.increment();

        Client sender = sessionManager.getLoggedInClient();
        if (sender == null) {
            return "❌ Ошибка: Сначала войдите в систему!";
        }

        Optional<Account> senderAccountOpt = sender.getAccounts().stream().findFirst();
        if (senderAccountOpt.isEmpty()) {
            return "❌ Ошибка: У отправителя нет счета!";
        }

        Account senderAccount = senderAccountOpt.get();

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

        // Сохраняем в сессию вместо полей класса
        sessionManager.setRecipientClient(recipientOpt.get());
        sessionManager.setRecipientAccount(recipientAccountOpt.get());


        return "✅ Получатель выбран: " + recipientOpt.get().getFullName() +
                " (Счет: " + recipientAccountOpt.get().getAccountNumber() + ")\n" +
                "Баланс отправителя: " + senderAccount.getBalance();
        });
    }

    @PostMapping("/transfer")
    @Observed(name = "transactions.transfer")
    public String transfer(@RequestParam double amount) {
        return transferTimer.record(() -> {
            Client sender = sessionManager.getLoggedInClient();
            if (sender == null) {
                return "❌ Ошибка: Сначала войдите в систему!";
            }

            // Получаем данные из сессии
            Client recipientClient = sessionManager.getRecipientClient();
            Account recipientAccount = sessionManager.getRecipientAccount();

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

            // Очищаем данные получателя после успешного перевода
            sessionManager.clearRecipientData();

            // Метрики
            transferCounter.increment();
            amountSummary.record(amount);

            return "✅ Перевод завершен! " + amount + "₽ переведено на счет " + recipientAccount.getAccountNumber();
        });
    }
}