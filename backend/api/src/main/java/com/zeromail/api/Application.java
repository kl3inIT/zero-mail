package com.zeromail.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.modulith.Modulithic;

@Modulithic(systemName = "zero-mail")
@SpringBootApplication(scanBasePackages = {"com.zeromail.api", "com.zeromail.core"})
@EntityScan(basePackages = {"com.zeromail.core.persistence", "com.zeromail.core.tenant.persistence", "com.zeromail.core.account.persistence"})
@EnableJpaRepositories(basePackages = {"com.zeromail.core.persistence", "com.zeromail.core.tenant.persistence", "com.zeromail.core.account.persistence"})
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
