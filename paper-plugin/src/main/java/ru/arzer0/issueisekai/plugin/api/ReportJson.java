package ru.arzer0.issueisekai.plugin.api;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public final class ReportJson {
    private static final Gson GSON =
            new GsonBuilder()
                    .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                    .create();

    private ReportJson() {}

    public static String write(Object value) {
        return GSON.toJson(value);
    }

    public static <T> T read(String json, Class<T> type) {
        return GSON.fromJson(json, type);
    }
}
