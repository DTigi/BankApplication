package com.bankapp.util;

import com.bankapp.model.Account;
import com.bankapp.model.Client;
import com.bankapp.repository.ClientRepository;
import com.bankapp.repository.AccountRepository;
import com.github.javafaker.Faker;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Random;

@Component
public class TestDataInitializer implements CommandLineRunner {
    AccountRepository accountRepository;
    private final Faker faker = new Faker();
    private final Random random = new Random();
    private static final String CSV_HEADER = "username,password,fullName,AccountNumber,initialBalance\n";
    private static final String CSV_FILE_PATH = "test_accounts.csv";

    public TestDataInitializer(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("📌 Генерация тестовых данных...");
        try (FileWriter writer = new FileWriter(CSV_FILE_PATH)) {
            writer.write(CSV_HEADER);

        for (int i = 0; i < 10; i++) {
            // Генерируем имя, телефон, логин и пароль
            String fullName = faker.name().fullName();
            String phone = "+79" + (random.nextInt(900000000) + 100000000);
            String username = "user" + (i + 1);
            String password = "pass" + (i + 1);

            // Создаем клиента
            Client client = new Client(fullName, phone, username, password);
            ClientRepository.save(client);
            System.out.println("✅ Создан клиент: " + fullName + " (" + phone + ") | Логин: " + username + ", Пароль: " + password);

            // Создаем случайное количество счетов (от 1 до 3)
            int accountCount = random.nextInt(3) + 1;
            for (int j = 0; j < accountCount; j++) {
                Account account = new Account();
                double initialBalance = random.nextInt(9000) + 1000; // Баланс от 1000 до 10000₽
                account.setBalance(initialBalance);
                client.getAccounts().add(account);
                accountRepository.save(account);
                System.out.println("  ➕ Счет: " + account.getAccountNumber() + " | Карта: " + account.getCardNumber() + " | Баланс: " + initialBalance + "₽");
                // Записываем данные в CSV
                String csvLine = String.format("%s,%s,%s,%s,%.2f\n",
                        username,
                        password,
                        fullName,
                        account.getAccountNumber(),
                        initialBalance);
                writer.write(csvLine);
            }
        }

        System.out.println("🎉 Генерация тестовых данных завершена!");
        System.out.println("💾 CSV файл сохранен как: " + CSV_FILE_PATH);
    } catch (IOException e) {
        System.err.println("❌ Ошибка при записи в CSV файл: " + e.getMessage());
    }
}
}