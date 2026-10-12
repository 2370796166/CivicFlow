package com.civicflow.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.civicflow.notification.entity.NotificationTemplateEntity;
import com.civicflow.notification.service.NotificationTemplateRenderer;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NotificationTemplateRendererTest {
    private final NotificationTemplateRenderer renderer = new NotificationTemplateRenderer();

    @Test
    void rendersOnlyDeclaredPlainTextVariables() {
        NotificationTemplateEntity template = template();
        var rendered = renderer.render(template, Map.of("appointmentId", "123"));
        assertThat(rendered.title()).isEqualTo("预约 123");
        assertThat(rendered.body()).isEqualTo("请确认 123");
        assertThatThrownBy(
                        () ->
                                renderer.render(
                                        template,
                                        Map.of("appointmentId", "123", "mobile", "13800000000")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> renderer.render(template, Map.of("appointmentId", "<img src=x>")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () -> renderer.render(template, Map.of("appointmentId", "${env.PASSWORD}")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesUndeclaredPlaceholderInStoredTemplate() {
        NotificationTemplateEntity template = template();
        template.setBodyPattern("联系人 {mobile}");
        assertThatThrownBy(() -> renderer.render(template, Map.of("appointmentId", "123")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static NotificationTemplateEntity template() {
        NotificationTemplateEntity template = new NotificationTemplateEntity();
        template.setAllowedVariables("appointmentId");
        template.setTitlePattern("预约 {appointmentId}");
        template.setBodyPattern("请确认 {appointmentId}");
        return template;
    }
}
