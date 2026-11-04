package com.saisanketh.triage.ai;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads prompt files from {@code src/main/resources/prompts/} and fills in their {@code {{placeholders}}}.
 *
 * <p>Versioned by file name: {@code missing-details-v1.txt}. A changed prompt is a NEW file (v2), so every stored
 * result says exactly which wording produced it, and old results stay explainable.
 */
@Component
public class PromptTemplates {

    static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([a-z_]+)}}");

    /** file name -> its text; files don't change while the app runs */
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    /**
     * @param name      e.g. "missing-details"
     * @param version   e.g. "v1": the file is prompts/missing-details-v1.txt
     * @param variables a value for every placeholder in the file (extra ones are an error too: probably a typo)
     */
    public Prompt render(String name, String version, Map<String, String> variables) {
        String template = load(name + "-" + version + ".txt");
        Map<String, String> used = new LinkedHashMap<>();
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder text = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            String value = variables.get(key);
            if (value == null) {
                throw new IllegalArgumentException("prompt " + name + "-" + version + " needs a value for {{" + key + "}}");
            }
            used.put(key, value);
            m.appendReplacement(text, Matcher.quoteReplacement(value));
        }
        m.appendTail(text);
        if (!used.keySet().equals(variables.keySet())) {
            throw new IllegalArgumentException("prompt " + name + "-" + version + " has no placeholder for "
                    + variables.keySet().stream().filter(k -> !used.containsKey(k)).toList());
        }
        return new Prompt(name, version, text.toString(), Map.copyOf(variables));
    }

    private String load(String fileName) {
        return cache.computeIfAbsent(fileName, f -> {
            ClassPathResource resource = new ClassPathResource("prompts/" + f);
            try {
                return resource.getContentAsString(StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException("prompt file not found: prompts/" + f, e);
            }
        });
    }
}
