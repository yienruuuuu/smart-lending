package io.github.yienruuuuu.smartlending;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Smart Lending 應用程式啟動點。
 *
 * <p>負責載入 `.env` 設定、啟動 Spring Boot，並開啟排程功能。
 */
@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class SmartLendingApplication {

    public static void main(String[] args) {
        loadDotenv();
        SpringApplication.run(SmartLendingApplication.class, args);
    }

    private static void loadDotenv() {
        Dotenv dotenv = Dotenv.configure()
                .ignoreIfMalformed()
                .ignoreIfMissing()
                .load();

        dotenv.entries().forEach(entry -> {
            if (System.getProperty(entry.getKey()) == null && System.getenv(entry.getKey()) == null) {
                System.setProperty(entry.getKey(), entry.getValue());
            }
        });
    }
}
