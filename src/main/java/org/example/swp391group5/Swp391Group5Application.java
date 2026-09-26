package org.example.swp391group5;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;

@SpringBootApplication(scanBasePackages = {"org.example.swp391group5", "controller", "service", "repository"})
@AutoConfigurationPackage(basePackages = {"org.example.swp391group5", "entity", "repository"})
public class Swp391Group5Application {

    public static void main(String[] args) {
        SpringApplication.run(Swp391Group5Application.class, args);
    }

}
