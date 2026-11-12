package ezmj.web;

import java.util.LinkedHashMap;
import java.util.Map;

import ezmj.model.Gender;
import ezmj.online.Json;

/** 環境設定，存在瀏覽器裡，預設值和 Swing 版一樣。等待時間與電腦速度看房間的設定，這裡沒有。 */
final class WebSettings {

    private static final String KEY = "ezmj.settings";

    int fontPercent = 100;
    int tilePercent = 100;
    WebMusic music = WebMusic.TEAHOUSE;
    int musicVolume = 50;
    int soundVolume = 100;
    int voiceVolume = 100;
    boolean sound = true;
    boolean voice = true;
    Gender playerGender = Gender.FEMALE;
    boolean animation = true;

    /** 讀不懂的欄位就用預設值，設定壞了不該讓遊戲打不開。 */
    static WebSettings load() {
        WebSettings settings = new WebSettings();
        String saved = Browser.load(KEY);
        if (saved == null) {
            return settings;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> values = (Map<String, Object>) Json.parse(saved);
            settings.fontPercent = percent(values, "fontPercent", settings.fontPercent, 85, 150);
            settings.tilePercent = percent(values, "tilePercent", settings.tilePercent, 100, 150);
            settings.musicVolume = percent(values, "musicVolume", settings.musicVolume, 0, 100);
            settings.soundVolume = percent(values, "soundVolume", settings.soundVolume, 0, 100);
            settings.voiceVolume = percent(values, "voiceVolume", settings.voiceVolume, 0, 100);
            settings.sound = !Boolean.FALSE.equals(values.get("sound"));
            settings.voice = !Boolean.FALSE.equals(values.get("voice"));
            settings.animation = !Boolean.FALSE.equals(values.get("animation"));
            settings.music = WebMusic.valueOf(String.valueOf(values.get("music")));
            settings.playerGender = Gender.valueOf(String.valueOf(values.get("playerGender")));
        } catch (RuntimeException e) {
            Browser.warn("環境設定讀不懂：" + e.getMessage());
        }
        return settings;
    }

    private static int percent(Map<String, Object> values, String key, int fallback, int min, int max) {
        Object value = values.get(key);
        return value instanceof Long ? Math.max(min, Math.min(max, ((Long) value).intValue())) : fallback;
    }

    void save() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("fontPercent", fontPercent);
        values.put("tilePercent", tilePercent);
        values.put("music", music.name());
        values.put("musicVolume", musicVolume);
        values.put("soundVolume", soundVolume);
        values.put("voiceVolume", voiceVolume);
        values.put("sound", sound);
        values.put("voice", voice);
        values.put("playerGender", playerGender.name());
        values.put("animation", animation);
        Browser.save(KEY, Json.write(values));
    }
}
