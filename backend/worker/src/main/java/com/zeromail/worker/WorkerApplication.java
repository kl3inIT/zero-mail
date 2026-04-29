package com.zeromail.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.zeromail.worker", "com.zeromail.core"})
@ConfigurationPropertiesScan(basePackages = "com.zeromail")
@EntityScan(basePackages = "com.zeromail.core")
@EnableJpaRepositories(basePackages = "com.zeromail.core")
@EnableScheduling
public class WorkerApplication {
    public static void main(String[] args) {
        SpringApplication.run(WorkerApplication.class, args);
    }
}
