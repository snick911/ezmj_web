package ezmj.web;

import java.util.List;
import java.util.Map;

import ezmj.model.BotLevel;
import ezmj.model.GameSpeed;
import ezmj.model.MusicTrack;
import ezmj.model.Suit;
import ezmj.model.Tile;
import ezmj.online.Json;
import ezmj.online.OnlineClient;
import ezmj.online.RoomSettings;

/**
 * 測試不依賴框架，直接執行 main。這裡只測不碰瀏覽器的部分，畫面與連線用 tools/autoplay.js 在瀏覽器裡實際打一場。
 */
public final class AllTests {

    private static int passed;

    private AllTests() {
    }

    public static void main(String[] args) {
        protocolVersionFollowsSwingClient();
        messagesMatchServerReadme();
        createRoomCarriesEveryOption();
        everyVoiceHasTheSameLength();
        voiceTextUsesTableNames();
        System.out.println("通過 " + passed + " 項");
    }

    private static void check(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError(what);
        }
        passed++;
    }

    private static void same(String actual, String expected) {
        check(actual.equals(expected), "應該是 " + expected + "，實際是 " + actual);
    }

    private static void protocolVersionFollowsSwingClient() {
        check(Messages.PROTOCOL == OnlineClient.PROTOCOL, "協定版本要和 Swing 版一樣");
    }

    private static void messagesMatchServerReadme() {
        Tile tile = Tile.of(Suit.DRAGON, 1);
        same(Json.write(Messages.helloName("小明")), "{\"type\":\"hello\",\"protocol\":1,\"name\":\"小明\"}");
        same(Json.write(Messages.helloToken("t")), "{\"type\":\"hello\",\"protocol\":1,\"token\":\"t\"}");
        same(Json.write(Messages.joinRoom("K7M2QX")), "{\"type\":\"joinRoom\",\"code\":\"K7M2QX\"}");
        same(Json.write(Messages.discard(3, tile)), "{\"type\":\"discard\",\"waitId\":3,\"tile\":\"5z\"}");
        same(Json.write(Messages.listen(3, tile)), "{\"type\":\"listen\",\"waitId\":3,\"tile\":\"5z\"}");
        same(Json.write(Messages.kong(3, tile)), "{\"type\":\"kong\",\"waitId\":3,\"tile\":\"5z\"}");
        same(Json.write(Messages.selfWin(3)), "{\"type\":\"selfWin\",\"waitId\":3}");
        same(Json.write(Messages.claim(3, 1)), "{\"type\":\"claim\",\"waitId\":3,\"option\":1}");
        same(Json.write(Messages.pass(3)), "{\"type\":\"pass\",\"waitId\":3}");
        same(Json.write(Messages.ready()), "{\"type\":\"ready\"}");
        same(Json.write(Messages.start()), "{\"type\":\"start\"}");
        same(Json.write(Messages.leave()), "{\"type\":\"leave\"}");
        same(Json.write(Messages.ping()), "{\"type\":\"ping\"}");
        same(Json.write(Messages.listRooms()), "{\"type\":\"listRooms\"}");
    }

    private static void createRoomCarriesEveryOption() {
        RoomSettings settings = new RoomSettings(20000, 100, 20,
                List.of(BotLevel.BEGINNER, BotLevel.NORMAL, BotLevel.PRO), 30, GameSpeed.FAST, "週末牌搭子", true);
        Map<String, Object> message = Messages.createRoom(settings);
        same(String.valueOf(message.get("type")), "createRoom");
        same(Json.write(message.get("botLevels")), "[\"BEGINNER\",\"NORMAL\",\"PRO\"]");
        same(String.valueOf(message.get("chips")), "20000");
        same(String.valueOf(message.get("basePay")), "100");
        same(String.valueOf(message.get("taiPay")), "20");
        same(String.valueOf(message.get("waitSeconds")), "30");
        same(String.valueOf(message.get("speed")), "FAST");
        same(String.valueOf(message.get("name")), "週末牌搭子");
        same(String.valueOf(message.get("private")), "true");
    }

    /** 樂譜寫錯時各聲部會對不齊。 */
    private static void everyVoiceHasTheSameLength() {
        for (MusicTrack track : MusicTrack.values()) {
            if (track == MusicTrack.OFF) {
                continue;
            }
            MusicScores.Score score = MusicScores.score(track);
            for (MusicScores.Voice voice : score.voices()) {
                double beats = 0;
                for (String note : voice.notes().trim().split("\\s+")) {
                    if (!note.equals("|")) {
                        beats += Double.parseDouble(note.substring(note.indexOf(':') + 1));
                    }
                }
                check(Math.abs(beats - score.totalBeats()) < 1e-9, track + " 有一個聲部是 " + beats + " 拍");
            }
        }
    }

    private static void voiceTextUsesTableNames() {
        same(VoiceLines.tileText(Tile.of(Suit.WIND, 1)), "東風");
        same(VoiceLines.tileText(Tile.of(Suit.DRAGON, 1)), "紅中");
        same(VoiceLines.tileText(Tile.of(Suit.WAN, 5)), "五萬");
    }
}
