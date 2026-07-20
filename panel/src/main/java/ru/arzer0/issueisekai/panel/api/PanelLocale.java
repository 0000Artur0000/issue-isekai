package ru.arzer0.issueisekai.panel.api;

import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public final class PanelLocale {
    private final String code;
    private final ResourceBundle messages;

    public PanelLocale(@Value("${app.locale:ru}") String code) {
        if (!code.equals("ru") && !code.equals("en")) {
            throw new IllegalArgumentException("APP_LOCALE must be ru or en");
        }
        this.code = code;
        messages = ResourceBundle.getBundle("messages", Locale.forLanguageTag(code));
    }

    public String code() {
        return code;
    }

    public ApiErrorResponse error(String errorCode) {
        return new ApiErrorResponse(
                errorCode, messages.getString("error." + errorCode), List.of());
    }
}
