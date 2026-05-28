package io.github.peterberghuis.goshuin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "io.github.peterberghuis")
public class GoshuinApplication {
    public static void main(String[] args) {
        SpringApplication.run(GoshuinApplication.class, args);
    }
}
