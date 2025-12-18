package designconsultbot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class DesignConsultBotApplicationTests {

    @Test
    void contextLoads() {
        // Тест проверяет, что контекст загружается
    }
}