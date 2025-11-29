package cohort_65.java.designconsultbot.bot;

import java.util.ArrayList;
import java.util.List;

public class ConsultationSession {
    private int step = 1;
    private final List<String> answers = new ArrayList<>();

    public int getStep() {
        return step;
    }

    public void nextStep() {
        step++;
    }

    public List<String> getAnswers() {
        return answers;
    }

    public void addAnswer(String answer) {
        answers.add(answer);
    }

    public void reset() {
        step = 1;
        answers.clear();
    }
}