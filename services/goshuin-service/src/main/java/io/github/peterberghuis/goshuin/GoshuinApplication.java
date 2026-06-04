package io.github.peterberghuis.goshuin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = "io.github.peterberghuis")
@EnableFeignClients
public class GoshuinApplication {
    public static void main(String[] args) {
        SpringApplication.run(GoshuinApplication.class, args);
    }
}
