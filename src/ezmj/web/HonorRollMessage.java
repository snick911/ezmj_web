package ezmj.web;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import ezmj.model.BotLevel;
import ezmj.model.GameRecord;
import ezmj.model.HonorRoll;

/** 讀服務器的 honorRoll 訊息，欄位見 ezmjService 的 README。不碰瀏覽器，AllTests 直接測。 */
final class HonorRollMessage {

    /** 四個類別的欄位，順序和 HonorRoll.Category 一樣。 */
    private static final String[] FIELDS = {"wins", "selfDraws", "winnings", "dealIns"};

    private HonorRollMessage() {
    }

    /**
     * 依類別由高到低，順序照服務器的。讀不懂的那筆略過，其他照樣列出，
     * 和 Swing 版讀本機榜的做法一樣；沒有送的類別就是空的。
     */
    static Map<HonorRoll.Category, List<GameRecord>> parse(Map<String, Object> message) {
        Map<HonorRoll.Category, List<GameRecord>> roll = new EnumMap<>(HonorRoll.Category.class);
        HonorRoll.Category[] categories = HonorRoll.Category.values();
        for (int i = 0; i < categories.length; i++) {
            List<GameRecord> records = new ArrayList<>();
            Object entries = message.get(FIELDS[i]);
            if (entries instanceof List) {
                for (Object entry : (List<?>) entries) {
                    GameRecord record = entry instanceof Map ? record((Map<?, ?>) entry) : null;
                    if (record != null) {
                        records.add(record);
                    }
                }
            }
            roll.put(categories[i], records);
        }
        return roll;
    }

    private static GameRecord record(Map<?, ?> entry) {
        try {
            Object rawLevels = entry.get("botLevels");
            if (!(rawLevels instanceof List)) {
                return null;
            }
            List<BotLevel> levels = new ArrayList<>();
            for (Object level : (List<?>) rawLevels) {
                levels.add(BotLevel.valueOf(String.valueOf(level)));
            }
            return new GameRecord(text(entry, "name"), text(entry, "date"), number(entry, "wins"),
                    number(entry, "selfDraws"), number(entry, "winnings"), number(entry, "dealIns"), levels,
                    number(entry, "basePay"), number(entry, "taiPay"));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String text(Map<?, ?> entry, String key) {
        Object value = entry.get(key);
        if (!(value instanceof String)) {
            throw new IllegalArgumentException(key + " 不是字串");
        }
        return (String) value;
    }

    private static int number(Map<?, ?> entry, String key) {
        Object value = entry.get(key);
        if (!(value instanceof Number)) {
            throw new IllegalArgumentException(key + " 不是數字");
        }
        return ((Number) value).intValue();
    }
}
