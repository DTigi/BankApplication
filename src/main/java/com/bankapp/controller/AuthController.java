package com.bankapp.controller;

import com.bankapp.model.Client;
import com.bankapp.service.ClientService;
import com.bankapp.util.SessionManager;
import io.micrometer.core.instrument.*;
import io.micrometer.observation.annotation.Observed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final ClientService clientService;
    private final SessionManager sessionManager;

    @Autowired
    private MeterRegistry meterRegistry;

    private final AtomicInteger userCount = new AtomicInteger(0);
    private Counter loginCounter, logoutCounter;
    private Timer loginTimer, logoutTimer;
    private DistributionSummary passwordLengthSummary;

    private Integer currentTimeout = 10;  // Дефолтное значение таймаута

    public AuthController(ClientService clientService, SessionManager sessionManager) {
        this.clientService = clientService;
        this.sessionManager = sessionManager;
    }

        @PostConstruct
        private void initMetrics() {
        // Counter
        this.loginCounter = Counter.builder("auth.login.count")
                .description("Счётчик успешных входов")
                .register(meterRegistry);

        this.logoutCounter = Counter.builder("auth.logout.count")
                .description("Счётчик успешных выходов")
                .register(meterRegistry);

        // Timer
        this.loginTimer = Timer.builder("auth.login.timer")
                .description("Время выполнения логина")
                .register(meterRegistry);

        this.logoutTimer = Timer.builder("auth.logout.timer")
                .description("Время выполнения логаута")
                .register(meterRegistry);

        // DistributionSummary
        this.passwordLengthSummary = DistributionSummary.builder("auth.password.length")
                .description("Длина введённых паролей")
                .register(meterRegistry);

        // Gauge
        Gauge.builder("auth.logged.in.users", userCount, AtomicInteger::get)
                .description("Текущее количество вошедших пользователей")
                .register(meterRegistry);
    }

    @Observed(name = "auth.register", contextualName = "auth#register", lowCardinalityKeyValues = {"endpoint", "register"})
    @Operation(summary = "Регистрация в системе", description = "Регистрирует указанного пользователя в системе",
            requestBody = @RequestBody(description = "Данные для регистрации", content = @Content(mediaType = "application/json")))
    @PostMapping("/register")
    public Client register(@RequestParam String fullName, @RequestParam String phone,
                           @RequestParam String username, @RequestParam String password) {
        return clientService.register(fullName, phone, username, password);
    }

    @Observed(name = "auth.set-timeout", contextualName = "auth#setTimeout", lowCardinalityKeyValues = {"endpoint", "setTimeout"})
    @Operation(summary = "Установка таймаута перед ответом", description = "Устанавливает таймаут перед ответом на запрос пользователя")
    @PostMapping("/setTimeout")
    public ResponseEntity<String> setTimeout(@RequestParam(defaultValue = "10") Integer timeout) {
        this.currentTimeout = timeout;  // Обновляем значение
        return ResponseEntity.ok("Таймаут установлен: " + timeout + "millis");
    }

    @Observed(name = "auth.login", contextualName = "auth#login", lowCardinalityKeyValues = {"endpoint", "login"})
    @Operation(summary = "Вход в систему", description = "Выполняет вход пользователя в систему")
    @PostMapping("/login")
    public String login(@RequestParam String username, @RequestParam String password) {
        return loginTimer.record(() -> {
            loginCounter.increment();
            passwordLengthSummary.record(password.length());

            try {
                Thread.sleep(currentTimeout);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        
            Optional<Client> clientOpt = clientService.login(username, password);
            if (clientOpt.isPresent()) {
                sessionManager.login(clientOpt.get());  // Просто передаем Client
                userCount.incrementAndGet();
                return "✅ Успешный вход: " + username;
            }
            return "❌ Ошибка: Неверный логин или пароль";
        });
    }

    @Observed(name = "auth.logout", contextualName = "auth#logout", lowCardinalityKeyValues = {"endpoint", "logout"})
    @Operation(summary = "Выход из системы", description = "Выполняет выход пользователя из системы")
    @PostMapping("/logout")
    public String logout() {
        return logoutTimer.record(() -> {
            logoutCounter.increment();
            sessionManager.logout();
            userCount.decrementAndGet();
            return "✅ Успешный выход";
        });
    }

    @Observed(name = "auth.current", contextualName = "auth#current", lowCardinalityKeyValues = {"endpoint", "current"})
    @Operation(summary = "Статус авторизации", description = "Возвращает объект пользователя если выполнен вход в систему. " +
            "В противном случае возвращает строку \"не аутентифицирован\"")
    @GetMapping("/current")
    public ResponseEntity<?> getCurrentClient() {
        Client client = sessionManager.getLoggedInClient();
        if (client == null) {
            return ResponseEntity.status(401).body("не аутентифицирован");
        }
        return ResponseEntity.ok(client);
    }
}