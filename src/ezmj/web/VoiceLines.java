package ezmj.web;

import ezmj.model.Gender;
import ezmj.model.Tile;

/**
 * 喊牌語音與司儀。Swing 版靠 Windows 語音合成，瀏覽器不能用，改用 Web Speech API，聲音會因裝置而異。
 * 沒有中文語音的裝置就不出聲，遊戲其他部分不受影響。
 * <p>
 * 司儀和 Swing 版一樣是喊牌以外的另一個女聲，宣布流局，連莊時新局開始說「連莊，連二拉二」：
 * 念得稍慢，不調音高、不加驚嘆號，聽起來穩重清楚。挑哪個聲音見 Browser.speak。
 */
final class VoiceLines {

    /** 流局由司儀宣布，不在這裡，和 Swing 版一樣。 */
    enum Line {
        CHOW("吃"), PONG("碰"), KONG("槓"), WIN("胡"), SELF_WIN("自摸"), LISTEN("聽牌");

        private final String text;

        Line(String text) {
            this.text = text;
        }

        String text() {
            return text;
        }
    }

    /** 連莊念到連幾拉幾，再多就只說「連莊」，和 Swing 版一樣。 */
    static final int MAX_ANNOUNCED_STREAK = 20;
    static final String DRAWN_GAME_TEXT = "流局";

    private static final String[] DRAGON_TEXTS = {"紅中", "發財", "白板"};
    private static final String[] DIGITS = {"", "一", "二", "三", "四", "五", "六", "七", "八", "九"};
    /** 自摸要喊得高亢興奮。 */
    private static final double EXCITED_PITCH = 1.5;
    private static final double EXCITED_RATE = 1.25;
    /** 牌局節奏快，喊慢了會跟不上。 */
    private static final double NORMAL_RATE = 1.3;
    /** 司儀念得稍慢，和 Swing 版的放慢 5% 一樣；音高不動。 */
    private static final double ANNOUNCER_RATE = .95;

    private static int volume = 100;

    private VoiceLines() {
    }

    /** 喊牌與司儀一起調。 */
    static void setVolume(int percent) {
        volume = percent;
    }

    /** 喊牌時的講法。風牌和三元牌單念一個字聽不清楚，用牌桌上的叫法。 */
    static String tileText(Tile tile) {
        switch (tile.suit()) {
            case WIND:   return tile.label() + "風";
            case DRAGON: return DRAGON_TEXTS[tile.rank() - 1];
            default:     return tile.label();
        }
    }

    /** 吃、碰、槓、胡這些一定會念，司儀還在說的話排在後面。 */
    static void play(Line line, Gender gender) {
        if (line == Line.SELF_WIN) {
            say(line.text(), gender, EXCITED_PITCH, EXCITED_RATE, false);
        } else {
            say(line.text(), gender, 1, NORMAL_RATE, false);
        }
    }

    /** 喊出打出的這張牌。司儀還在說的話就不喊了，免得越落越後面。 */
    static void play(Tile tile, Gender gender) {
        say(tileText(tile), gender, 1, NORMAL_RATE, true);
    }

    /** 1 到 99 的中文數字：2 是「二」、10 是「十」、12 是「十二」、20 是「二十」。 */
    static String chineseNumber(int n) {
        if (n < 1 || n > 99) {
            throw new IllegalArgumentException("只念 1 到 99：" + n);
        }
        int tens = n / 10;
        String ones = DIGITS[n % 10];
        if (tens == 0) {
            return ones;
        }
        return (tens == 1 ? "" : DIGITS[tens]) + "十" + ones;
    }

    /** 連莊的局開始時司儀說的話，例如連二是「連莊，連二拉二」；超過 MAX_ANNOUNCED_STREAK 只說「連莊」。 */
    static String streakText(int streak) {
        if (streak < 1) {
            throw new IllegalArgumentException("沒有連莊：" + streak);
        }
        if (streak > MAX_ANNOUNCED_STREAK) {
            return "連莊";
        }
        String n = chineseNumber(streak);
        return "連莊，連" + n + "拉" + n;
    }

    /** 連莊的局開始時司儀說「連莊，連X拉X」，沒有連莊就不說。 */
    static void announceStreak(int streak) {
        if (streak > 0) {
            announce(streakText(streak));
        }
    }

    /** 流局時司儀宣布「流局」。 */
    static void announceDrawnGame() {
        announce(DRAWN_GAME_TEXT);
    }

    private static void announce(String text) {
        if (volume > 0) {
            Browser.speak(text, Browser.VOICE_ANNOUNCER, volume / 100.0, 1, ANNOUNCER_RATE, false);
        }
    }

    private static void say(String text, Gender gender, double pitch, double rate, boolean droppable) {
        if (volume > 0) {
            Browser.speak(text, gender == Gender.MALE ? Browser.VOICE_MALE : Browser.VOICE_FEMALE,
                    volume / 100.0, pitch, rate, droppable);
        }
    }
}
