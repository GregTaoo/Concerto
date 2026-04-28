package top.gregtao.concerto.paper.util;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class I18n {

    public static final I18n INSTANCE = new I18n(Locale.getDefault());
    public static String get(String key, Object... args) {
        return INSTANCE.translate(key, args);
    }

    private final Map<Locale, Map<String, String>> languages = new HashMap<>();
    private Locale currentLocale;

    public I18n(Locale locale) {
        this.currentLocale = locale;
    }

    public void loadFile(Locale locale, InputStream in) {
        Gson gson = new Gson();
        Map<String, String> map = gson.fromJson(
                new InputStreamReader(in, StandardCharsets.UTF_8),
                new TypeToken<Map<String, String>>() {}.getType()
        );
        this.languages.put(locale, map);
    }

    public void setLocale(Locale locale) {
        this.currentLocale = locale;
    }

    public String translate(String key, Object... args) {
        Map<String, String> bundle = this.languages.getOrDefault(
                this.currentLocale, this.languages.get(Locale.ENGLISH));

        if (bundle == null) return key;

        String pattern = bundle.getOrDefault(key, key);

        return args.length == 0
                ? pattern
                : MessageFormat.format(pattern, args);
    }
}