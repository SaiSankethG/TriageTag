package com.saisanketh.triage.ai;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Test LLM: returns the given answers in order (a RuntimeException in the list is thrown instead), records prompts. */
public class ScriptedTriageLlm implements TriageLlm {

    private final Deque<Object> answers = new ArrayDeque<>();
    public final List<Prompt> prompts = new ArrayList<>();

    public ScriptedTriageLlm(Object... answers) {
        this.answers.addAll(List.of(answers));
    }

    @Override
    public String model() {
        return "scripted";
    }

    @Override
    public LlmReply complete(Prompt prompt) {
        prompts.add(prompt);
        Object next = answers.removeFirst();
        if (next instanceof RuntimeException e) {
            throw e;
        }
        return new LlmReply((String) next, 100, 10);
    }
}
