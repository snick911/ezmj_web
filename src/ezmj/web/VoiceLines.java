package ezmj.web;

import ezmj.model.Gender;
import ezmj.model.Tile;

/**
 * 喊牌語音。Swing 版靠 Windows 語音合成，瀏覽器不能用，改用 Web Speech API，聲音會因裝置而異。
 * 沒有中文語音的裝置就不出聲，遊戲其他部分不受影響。
 */
final class VoiceLines {

    enum Line {
        CHOW("吃"), PONG("碰"), KONG("槓"), WIN("胡"), SELF_WIN("自摸"), LISTEN("聽牌"), DRAWN_GAME("流局");

        private final String text;

        Line(String text) {
            this.text = text;
        }

        String text() {
            return text;
        }
    }

    private static final String[] DRAGON_TEXTS = {"紅中", "發財", "白板"};
    /** 自摸要喊得高亢興奮。 */
    private static final double EXCITED_PITCH = 1.5;
    private static final double EXCITED_RATE = 1.25;
    /** 牌局節奏快，喊慢了會跟不上。 */
    private static final double NORMAL_RATE = 1.3;

    private static int volume = 100;

    private VoiceLines() {
    }

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

    static void play(Line line, Gender gender) {
        if (line == Line.SELF_WIN) {
            say(line.text(), gender, EXCITED_PITCH, EXCITED_RATE);
        } else {
            say(line.text(), gender, 1, NORMAL_RATE);
        }
    }

    /** 喊出打出的這張牌。 */
    static void play(Tile tile, Gender gender) {
        say(tileText(tile), gender, 1, NORMAL_RATE);
    }

    private static void say(String text, Gender gender, double pitch, double rate) {
        if (volume > 0) {
            Browser.speak(text, gender == Gender.MALE, volume / 100.0, pitch, rate);
        }
    }
}
