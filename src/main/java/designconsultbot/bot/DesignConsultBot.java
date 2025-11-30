package designconsultbot.bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;

@Component
public class DesignConsultBot extends TelegramLongPollingBot {
    private static final Logger log = LoggerFactory.getLogger(DesignConsultBot.class);

    @Autowired
    @Qualifier("customMessageSource")
    private MessageSource messages;

    // ...

    private void handleCallback(CallbackQuery cq) {
        String data = cq.getData();
        long chatId = cq.getFrom().getId();
        ConsultationSession session = sessions.computeIfAbsent(chatId, k -> new ConsultationSession());
        if (data != null && data.startsWith("LANG_")) {
            String code = data.substring("LANG_".length());
            Locale locale = localeFromCode(code);
            session.setLocale(locale);
            AnswerCallbackQuery ack = new AnswerCallbackQuery(cq.getId());
            ack.setText(getMsg("language_set", locale, locale.getDisplayLanguage(locale)));
            try {
                execute(ack);
            } catch (TelegramApiException e) {
                log.error("Error sending callback answer", e);
            }
            send(chatId, getMsg("language_confirm", locale, locale.getDisplayLanguage(locale)));
            send(chatId, getMsg("commands", locale));
        } else {
            AnswerCallbackQuery ack = new AnswerCallbackQuery(cq.getId());
            ack.setText("OK");
            try { execute(ack); } catch (TelegramApiException e) { log.error("Error sending callback answer", e); }
        }
    }

    private String buildSummary(ConsultationSession s) {
        var a = s.getAnswers();
        String type = !a.isEmpty() ? a.get(0) : "";
        String goal = a.size() > 1 ? a.get(1) : "";
        String refs = a.size() > 2 ? a.get(2) : "";
        String budget = a.size() > 3 ? a.get(3) : "";
        String contact = a.size() > 4 ? a.get(4) : "";
        return String.format("Тип продукта: %s%nЦель/задача: %s%nРеференсы/стиль: %s%nБюджет/сроки: %s%nКонтакт: %s",
                type, goal, refs, budget, contact);
    }

    // Вместо printStackTrace()
    private void send(long chatId, String text) {
        SendMessage sm = SendMessage.builder()
                .chatId(String.valueOf(chatId))
                .text(text)
                .build();
        try {
            execute(sm);
        } catch (TelegramApiException e) {
            log.error("TelegramBot send error", e);
        }
    }

}