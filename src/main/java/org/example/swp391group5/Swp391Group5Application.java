package org.example.swp391group5;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {"org.example.swp391group5", "controller", "service", "repository"})
@EntityScan(basePackages = "entity")
@EnableJpaRepositories(basePackages = "repository")
public class Swp391Group5Application {

    public static void main(String[] args) {
        SpringApplication.run(Swp391Group5Application.class, args);
    }

}
