package io.chronoforge.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.embedded.tomcat.TomcatProtocolHandlerCustomizer;
import org.springframework.context.annotation.Bean;

@SpringBootApplication(scanBasePackages = "io.chronoforge")
public class CfApplication {
    public static void main(String[] args) {
        SpringApplication.run(CfApplication.class, args);
    }

    @Bean
    TomcatProtocolHandlerCustomizer<?> vt() {
        return ph -> ph.setExecutor(java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor());
    }
}
