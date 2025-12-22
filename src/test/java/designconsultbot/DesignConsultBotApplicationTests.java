package designconsultbot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

class DesignConsultBotApplicationTests {

    @Test
    void contextLoads() {
        // Активируем профиль test и отключаем потенциально проблемные автоконфигурации
        System.setProperty("spring.profiles.active", "test");
        System.setProperty("spring.main.web-application-type", "none");
        System.setProperty("spring.autoconfigure.exclude",
                "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
                        "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration");
        System.setProperty("spring.flyway.enabled", "false");
        System.setProperty("spring.liquibase.enabled", "false");

        try (ConfigurableApplicationContext ctx =
                     SpringApplication.run(DesignConsultBotApplication.class)) {
            // Контекст успешно стартовал и корректно закроется по try-with-resources
        }
    }
}