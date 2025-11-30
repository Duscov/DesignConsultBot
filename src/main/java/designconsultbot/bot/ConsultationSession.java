package designconsultbot.bot;

import lombok.*;


import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Getter
public class ConsultationSession {
    private int step = 1;
    private final List<String> answers = new ArrayList<>();

    @Setter
    private Locale locale = Locale.forLanguageTag("ru");

    public void nextStep() { step++; }
    public void addAnswer(String answer) { answers.add(answer); }
}