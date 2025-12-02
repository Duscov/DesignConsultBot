package designconsultbot.bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DesignConsultBot extends TelegramLongPollingBot {
    private static final Logger log = LoggerFactory.getLogger(DesignConsultBot.class);

    @Value("${telegram.bot.username:design_consult_bot}")
    private String botUsername;

    @Value("${telegram.bot.token}")
    private String botToken;

    @Value("${telegram.bot.owner-chat-id:0}")
    private long ownerChatId;

    private final ConcurrentHashMap<Long, ConsultationSession> sessions = new ConcurrentHashMap<>();
    private final MessageSource messages;

    public DesignConsultBot(@Qualifier("messageSource") MessageSource messages) {
        this.messages = messages;
    }

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
        try {
            if (update.hasCallbackQuery()) {
                handleCallback(update.getCallbackQuery());
                return;
            }
            if (!update.hasMessage() || !update.getMessage().hasText()) return;

            Message msg = update.getMessage();
            long chatId = msg.getChatId();
            String text = msg.getText().trim();

            if (text.startsWith("/start")) {
                send(chatId, getMsg("welcome", Locale.forLanguageTag("ru")));
                sendLanguageKeyboard(chatId, Locale.forLanguageTag("ru"));
                return;
            }

            if (text.startsWith("/lang")) {
                sendLanguageKeyboard(chatId, getSessionLocaleOrDefault(chatId));
                return;
            }

            if (text.startsWith("/getid")) {
                var locale = getSessionLocaleOrDefault(chatId);
                send(chatId, getMsg("getid_response", locale, String.valueOf(chatId)));
                return;
            }

            if (text.startsWith("/consult")) {
                ConsultationSession session = sessions.computeIfAbsent(chatId, k -> {
                    var s = new ConsultationSession();
                    s.setLocale(getSessionLocaleOrDefault(chatId));
                    return s;
                });
                send(chatId, getMsg("ask.type", session.getLocale()));
                return;
            }

            // Работа с активной сессией
            ConsultationSession session = sessions.get(chatId);
            if (session == null) {
                send(chatId, getMsg("unknown.help", getSessionLocaleOrDefault(chatId)));
                return;
            }

            // Сохраняем ответ и двигаем шаг
            session.addAnswer(text);

            switch (session.getStep()) {
                case 1 -> {
                    send(chatId, getMsg("ask.goal", session.getLocale()));
                    session.nextStep();
                }
                case 2 -> {
                    send(chatId, getMsg("ask.refs", session.getLocale()));
                    session.nextStep();
                }
                case 3 -> {
                    send(chatId, getMsg("ask.budget", session.getLocale()));
                    session.nextStep();
                }
                case 4 -> {
                    send(chatId, getMsg("ask.contact", session.getLocale()));
                    session.nextStep();
                }
                case 5 -> {
                    String summary = buildSummary(session);
                    Locale userLocale = session.getLocale();
                    if (ownerChatId > 0) {
                        String header = getMsg("new_lead_header", Locale.forLanguageTag("ru"), userLocale.getLanguage());
                        send(ownerChatId, header + "\n" + summary);
                        send(chatId, getMsg("thanks_sent", userLocale));
                    } else {
                        send(chatId, getMsg("test_summary", userLocale) + "\n" + summary);
                        send(chatId, getMsg("thanks_test", userLocale));
                    }
                    sessions.remove(chatId);
                }
                default -> {
                    send(chatId, getMsg("error", session.getLocale()));
                    sessions.remove(chatId);
                }
            }
        } catch (Exception e) {
            log.error("Error processing update", e);
            if (update.hasMessage()) {
                long chatId = update.getMessage().getChatId();
                send(chatId, getMsg("error", getSessionLocaleOrDefault(chatId)));
            }
        }
    }

    private void handleCallback(CallbackQuery cq) {
        String data = cq.getData();
        long chatId = cq.getFrom().getId();
        ConsultationSession session = sessions.computeIfAbsent(chatId, k -> new ConsultationSession());
        if (data != null && data.startsWith("LANG_")) {
            String code = data.substring("LANG_".length()); // ru или en
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
            try {
                execute(ack);
            } catch (TelegramApiException e) {
                log.error("Error sending callback answer", e);
            }
        }
    }

    private Locale localeFromCode(String code) {
        if ("en".equalsIgnoreCase(code)) return Locale.ENGLISH;
        if ("ru".equalsIgnoreCase(code)) return Locale.forLanguageTag("ru");
        return Locale.ENGLISH;
    }

    private Locale getSessionLocaleOrDefault(long chatId) {
        ConsultationSession s = sessions.get(chatId);
        if (s != null && s.getLocale() != null) return s.getLocale();
        return Locale.forLanguageTag("ru");
    }

    private void sendLanguageKeyboard(long chatId, Locale localeForPrompt) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        InlineKeyboardButton ru = InlineKeyboardButton.builder()
                .text(getMsg("btn.russian", localeForPrompt))
                .callbackData("LANG_ru")
                .build();
        InlineKeyboardButton en = InlineKeyboardButton.builder()
                .text(getMsg("btn.english", localeForPrompt))
                .callbackData("LANG_en")
                .build();
        markup.setKeyboard(List.of(List.of(ru, en)));
        SendMessage sm = SendMessage.builder()
                .chatId(String.valueOf(chatId))
                .text(getMsg("choose_language", localeForPrompt))
                .replyMarkup(markup)
                .build();
        try {
            execute(sm);
        } catch (TelegramApiException e) {
            log.error("Error sending language keyboard", e);
        }
    }

    private String getMsg(String key, Locale locale, Object... args) {
        try {
            return messages.getMessage(key, args, locale);
        } catch (Exception e) {
            log.warn("Message key not found: {}", key);
            return "[" + key + "]";
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