package ezmj.web;

import java.util.Map;

/**
 * 隨時補位：服務器的 substituteWaiting（只給在等的人）與 substituted（全桌）。seat 是服務器的座位。
 * replaced 是被換掉的那個座位原本的名字；computer 表示開局時就是電腦，不然是離開的玩家留下、由電腦代打的座位。
 * 等待時還不知道自己會用什麼名字上場，name 是空的。畫面上的字都在這裡，不碰瀏覽器，所以 JVM 上測得到。
 */
record Substitution(int seat, String name, String replaced, boolean computer) {

    static final String WAITING_TITLE = "已保留座位，等一下就換你上場";

    /** substituteWaiting：被換掉的座位名字在 replacing。 */
    static Substitution waiting(Map<String, Object> message) {
        return new Substitution(seat(message), "", text(message, "replacing"), flag(message, "computer"));
    }

    static Substitution substituted(Map<String, Object> message) {
        return new Substitution(seat(message), text(message, "name"), text(message, "replaced"), flag(message, "computer"));
    }

    /** 被換掉的那一家：「電腦（阿土伯）」，離開的玩家留下的座位是「電腦（代打「小明」的座位）」。 */
    String replacedLabel() {
        return computer ? "電腦（" + replaced + "）" : "電腦（代打「" + replaced + "」的座位）";
    }

    /** 牌桌上的公告，例如「玩家 小明 替換電腦（阿土伯）加入」。mine 是自己補位上場。 */
    String announcement(boolean mine) {
        return (mine ? "你" : "玩家 " + name + " ") + "替換" + replacedLabel() + "加入";
    }

    /** 公告下面的小字：中途加入的不列入榮譽榜。 */
    static String honorNote(boolean mine) {
        return mine ? "中途加入，這一場不列入榮譽榜" : "中途補位的玩家不列入榮譽榜";
    }

    /** 等待畫面標題下面的說明。 */
    String waitingDetail() {
        return "牌局正在進行，等" + replacedLabel() + "打完這一手就換你上場；這一局先結束的話，下一局開始時上場。"
                + "中途加入的不列入榮譽榜。";
    }

    private static int seat(Map<String, Object> message) {
        Object value = message.get("seat");
        if (!(value instanceof Number)) {
            throw new IllegalArgumentException("補位的訊息沒有座位");
        }
        int seat = ((Number) value).intValue();
        if (seat < 0 || seat > 3) {
            throw new IllegalArgumentException("補位的座位不對：" + seat);
        }
        return seat;
    }

    private static String text(Map<String, Object> message, String key) {
        Object value = message.get(key);
        if (!(value instanceof String)) {
            throw new IllegalArgumentException("補位的訊息沒有 " + key);
        }
        return (String) value;
    }

    private static boolean flag(Map<String, Object> message, String key) {
        return Boolean.TRUE.equals(message.get(key));
    }
}
