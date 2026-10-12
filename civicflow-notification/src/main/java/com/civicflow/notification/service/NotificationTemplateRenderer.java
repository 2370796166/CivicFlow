package com.civicflow.notification.service;

import com.civicflow.notification.entity.NotificationTemplateEntity;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class NotificationTemplateRenderer {
    private static final Pattern TOKEN = Pattern.compile("\\{([a-zA-Z][a-zA-Z0-9]*)\\}");
    private static final Pattern SAFE_VALUE = Pattern.compile("[\\p{L}\\p{N} _.:/-]{1,64}");

    public Rendered render(NotificationTemplateEntity template, Map<String, String> variables) {
        if (template == null || variables == null || variables.size() > 8) {
            throw new IllegalArgumentException("Unsupported notification template");
        }
        Set<String> allowed = Set.of(template.getAllowedVariables().split(","));
        if (!allowed.containsAll(variables.keySet())) {
            throw new IllegalArgumentException("Unapproved notification variable");
        }
        for (String value : variables.values()) {
            if (value == null || !SAFE_VALUE.matcher(value).matches()) {
                throw new IllegalArgumentException("Unsafe notification variable");
            }
        }
        String title = substitute(template.getTitlePattern(), variables, allowed);
        String body = substitute(template.getBodyPattern(), variables, allowed);
        if (title.length() > 128 || body.length() > 512) {
            throw new IllegalArgumentException("Notification exceeds size limit");
        }
        return new Rendered(title, body);
    }

    private static String substitute(
            String pattern, Map<String, String> variables, Set<String> allowed) {
        Matcher matcher = TOKEN.matcher(pattern);
        StringBuilder output = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!allowed.contains(name) || !variables.containsKey(name)) {
                throw new IllegalArgumentException("Missing notification variable");
            }
            matcher.appendReplacement(output, Matcher.quoteReplacement(variables.get(name)));
        }
        matcher.appendTail(output);
        if (output.indexOf("{") >= 0 || output.indexOf("}") >= 0) {
            throw new IllegalArgumentException("Malformed notification template");
        }
        return output.toString();
    }

    public record Rendered(String title, String body) {}
}
