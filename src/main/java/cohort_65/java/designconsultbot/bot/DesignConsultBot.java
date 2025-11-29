package cohort_65.java.designconsultbot.bot;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class DesignConsultBot extends TelegramLongPollingBot {

    @Value("${telegram.bot.username:design_consult_bot}")
    private String botUsername;

    @Value("${telegram.bot.token}")
    private String botToken;

    @Value("${telegram.bot.owner-chat-id:0}")
    private long ownerChatId;

    private final ConcurrentHashMap<Long, ConsultationSession> sessions = new ConcurrentHashMap<>();

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) return;

        Message msg = update.getMessage();
        long chatId = msg.getChatId();
        String text = msg.getText().trim();

        try {
            if (text.startsWith("/start")) {
                send(chatId, "Привет! Я помогу собрать первичную информацию по дизайну.\n" +
                        "Команды:\n/consult — начать консультацию\n/getid — получить ваш chatId");
                return;
            }

            if (text.startsWith("/consult")) {
                ConsultationSession session = new ConsultationSession();
                sessions.put(chatId, session);
                send(chatId, "Какой тип продукта нужно оформить? (например: карточка товара, баннер, презентация)");
                return;
            }

            if (text.startsWith("/getid")) {
                send(chatId, "Ваш chatId: " + chatId);
                return;
            }

            // Работа с активной сессией
            ConsultationSession session = sessions.get(chatId);
            if (session == null) {
                send(chatId, "Не понял. Напишите /consult чтобы начать консультацию.");
                return;
            }

            // Сохраняем ответ и двигаем шаг
            session.addAnswer(text);

            switch (session.getStep()) {
                case 1 -> {
                    send(chatId, "Опишите цель/задачу (коротко).");
                    session.nextStep();
                }
                case 2 -> {
                    send(chatId, "Есть ли референсы / желаемый стиль? (ссылки/коротко)");
                    session.nextStep();
                }
                case 3 -> {
                    send(chatId, "Укажите желаемый бюджет и сроки.");
                    session.nextStep();
                }
                case 4 -> {
                    send(chatId, "Оставьте контакт (телефон, @telegram или email).");
                    session.nextStep();
                }
                case 5 -> {
                    String summary = buildSummary(session);
                    if (ownerChatId > 0) {
                        send(ownerChatId, "Новый лид:\n" + summary);
                        send(chatId, "Спасибо! Ваши данные отправлены, скоро с вами свяжутся.");
                    } else {
                        send(chatId, "Тест — сводка для владельца:\n" + summary);
                        send(chatId, "Спасибо! Для реального пересыла установите OWNER_CHAT_ID.");
                    }
                    sessions.remove(chatId);
                }
                default -> {
                    send(chatId, "Что-то пошло не так. Попробуйте /consult ещё раз.");
                    sessions.remove(chatId);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            send(chatId, "Произошла ошибка, попробуйте позже.");
        }
    }

    private String buildSummary(ConsultationSession s) {
        var a = s.getAnswers();
        String type = a.size() > 0 ? a.get(0) : "";
        String goal = a.size() > 1 ? a.get(1) : "";
        String refs = a.size() > 2 ? a.get(2) : "";
        String budget = a.size() > 3 ? a.get(3) : "";
        String contact = a.size() > 4 ? a.get(4) : "";
        return String.format("Тип продукта: %s%nЦель/задача: %s%nРеференсы/стиль: %s%nБюджет/сроки: %s%nКонтакт: %s",
                type, goal, refs, budget, contact);
    }

    private void send(long chatId, String text) {
        SendMessage sm = SendMessage.builder()
                .chatId(String.valueOf(chatId))
                .text(text)
                .build();
        try {
            execute(sm);
        } catch (TelegramApiException e) {
            // логируем, но не ломаем поток
            e.printStackTrace();
        }
    }
}