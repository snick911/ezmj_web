package ezmj.web;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import ezmj.model.BotLevel;
import ezmj.model.GameRecord;
import ezmj.model.HonorRoll;

/**
 * 讀服務器的 honorRoll 訊息，欄位見 ezmjService 的 README。不碰瀏覽器，AllTests 直接測。
 * 線上的網頁版編的是比較舊的引擎，還沒有 HonorRoll.Stakes，所以底台的分組在這裡自己做。
 */
final class HonorRollMessage {

    /** 四個類別的欄位，順序和 HonorRoll.Category 一樣。 */
    private static final String[] FIELDS = {"wins", "selfDraws", "winnings", "dealIns"};

    /** 一組底台的贏錢榜，由高到低。 */
    record StakesBoard(int basePay, int taiPay, List<GameRecord> records) {

        String label() {
            return "底 " + GameReport.grouped(basePay) + "／每台 " + GameReport.grouped(taiPay);
        }

        /** 日期是 yyyy-MM-dd，照字串比就是照時間比。 */
        String newestDate() {
            String newest = "";
            for (GameRecord record : records) {
                if (record.date().compareTo(newest) > 0) {
                    newest = record.date();
                }
            }
            return newest;
        }
    }

    private HonorRollMessage() {
    }

    /**
     * 依類別由高到低，順序照服務器的。讀不懂的那筆略過，其他照樣列出，
     * 和 Swing 版讀本機榜的做法一樣；沒有送的類別就是空的。贏錢金額是所有底台合起來的，畫面上看 winningsByStakes。
     */
    static Map<HonorRoll.Category, List<GameRecord>> parse(Map<String, Object> message) {
        Map<HonorRoll.Category, List<GameRecord>> roll = new EnumMap<>(HonorRoll.Category.class);
        HonorRoll.Category[] categories = HonorRoll.Category.values();
        for (int i = 0; i < categories.length; i++) {
            roll.put(categories[i], records(message.get(FIELDS[i])));
        }
        return roll;
    }

    /**
     * 贏錢金額照底台分的榜，依底再依每台由小到大，不同底台不放在一起比。服務器送了 winningsByStakes 就照它的；
     * 0.10.0 以前的服務器只有不分底台的 winnings，就把那十筆照底台分開，順序照服務器的。
     * 讀不懂的那筆或那組略過，沒有紀錄的底台不列。
     */
    static List<StakesBoard> winningsByStakes(Map<String, Object> message) {
        List<StakesBoard> boards = new ArrayList<>();
        Object groups = message.get("winningsByStakes");
        if (groups instanceof List) {
            for (Object group : (List<?>) groups) {
                StakesBoard board = group instanceof Map ? board((Map<?, ?>) group) : null;
                if (board != null && !board.records().isEmpty()) {
                    boards.add(board);
                }
            }
        } else {
            for (GameRecord record : records(message.get("winnings"))) {
                StakesBoard board = find(boards, record.basePay(), record.taiPay());
                if (board == null) {
                    board = new StakesBoard(record.basePay(), record.taiPay(), new ArrayList<>());
                    boards.add(board);
                }
                board.records().add(record);
            }
        }
        boards.sort((a, b) -> a.basePay() != b.basePay() ? Integer.compare(a.basePay(), b.basePay())
                : Integer.compare(a.taiPay(), b.taiPay()));
        return boards;
    }

    /** 最近有新紀錄的那組的位置，同一天的取底台大的，和 Swing 版一樣；沒有任何一組是 -1。打開時先看這組。 */
    static int latest(List<StakesBoard> boards) {
        int latest = -1;
        String latestDate = "";
        for (int i = 0; i < boards.size(); i++) {
            String date = boards.get(i).newestDate();
            if (latest < 0 || date.compareTo(latestDate) >= 0) {
                latest = i;
                latestDate = date;
            }
        }
        return latest;
    }

    private static StakesBoard find(List<StakesBoard> boards, int basePay, int taiPay) {
        for (StakesBoard board : boards) {
            if (board.basePay() == basePay && board.taiPay() == taiPay) {
                return board;
            }
        }
        return null;
    }

    private static StakesBoard board(Map<?, ?> group) {
        try {
            return new StakesBoard(number(group, "basePay"), number(group, "taiPay"), records(group.get("records")));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static List<GameRecord> records(Object entries) {
        List<GameRecord> records = new ArrayList<>();
        if (entries instanceof List) {
            for (Object entry : (List<?>) entries) {
                GameRecord record = entry instanceof Map ? record((Map<?, ?>) entry) : null;
                if (record != null) {
                    records.add(record);
                }
            }
        }
        return records;
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
