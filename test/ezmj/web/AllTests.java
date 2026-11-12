package ezmj.web;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import ezmj.engine.WallRing;
import ezmj.model.BotLevel;
import ezmj.model.Dice;
import ezmj.model.GameRecord;
import ezmj.model.GameSpeed;
import ezmj.model.HonorRoll;
import ezmj.model.MusicTrack;
import ezmj.model.Suit;
import ezmj.model.Tile;
import ezmj.online.Json;
import ezmj.online.OnlineClient;
import ezmj.online.OnlineGame;
import ezmj.online.RoomInfo;
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
        honorRollFollowsServerOrder();
        honorRollSplitsWinningsByStakes();
        createRoomCarriesEveryOption();
        lobbyReadsRoomFlags();
        substitutionTextNamesTheComputer();
        everyVoiceHasTheSameLength();
        musicMatchesSwing();
        recordingsAreShipped();
        recordedMusicIsOnlyTurnedDown();
        voiceTextUsesTableNames();
        announcerSaysTheStreak();
        diceMatchSwingClient();
        pdfOffsetsPointAtObjects();
        moneyIsGroupedByThousands();
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
        same(Json.write(Messages.joinRoom("K7M2QX")),
                "{\"type\":\"joinRoom\",\"code\":\"K7M2QX\",\"supports\":[\"expert\",\"substitute\"]}");
        same(Json.write(Messages.discard(3, tile)), "{\"type\":\"discard\",\"waitId\":3,\"tile\":\"5z\"}");
        same(Json.write(Messages.declareListen(7)), "{\"type\":\"declareListen\",\"listenId\":7}");
        same(Json.write(Messages.listen(3, tile)), "{\"type\":\"listen\",\"waitId\":3,\"tile\":\"5z\"}");
        same(Json.write(Messages.chat("你好")), "{\"type\":\"chat\",\"text\":\"你好\"}");
        same(Json.write(Messages.kong(3, tile)), "{\"type\":\"kong\",\"waitId\":3,\"tile\":\"5z\"}");
        same(Json.write(Messages.selfWin(3)), "{\"type\":\"selfWin\",\"waitId\":3}");
        same(Json.write(Messages.claim(3, 1)), "{\"type\":\"claim\",\"waitId\":3,\"option\":1}");
        same(Json.write(Messages.pass(3)), "{\"type\":\"pass\",\"waitId\":3}");
        same(Json.write(Messages.ready()), "{\"type\":\"ready\"}");
        same(Json.write(Messages.start()), "{\"type\":\"start\"}");
        same(Json.write(Messages.leave()), "{\"type\":\"leave\"}");
        same(Json.write(Messages.ping()), "{\"type\":\"ping\"}");
        same(Json.write(Messages.listRooms()), "{\"type\":\"listRooms\",\"supports\":[\"substitute\"]}");
        same(Json.write(Messages.honorRoll()), "{\"type\":\"honorRoll\"}");
    }

    /** 欄位照服務器 README 的 honorRoll。讀不懂的那筆略過，其他照樣列出；沒有的類別是空的。 */
    @SuppressWarnings("unchecked")
    private static void honorRollFollowsServerOrder() {
        Map<String, Object> message = (Map<String, Object>) Json.parse("{\"type\":\"honorRoll\",\"wins\":["
                + "{\"name\":\"小明\",\"date\":\"2026-11-04\",\"wins\":9,\"selfDraws\":2,\"winnings\":12400,\"dealIns\":1,"
                + "\"botLevels\":[\"NORMAL\",\"PRO\",\"PRO\"],\"basePay\":100,\"taiPay\":20},"
                + "{\"name\":\"壞掉的\",\"date\":\"2026-11-04\",\"wins\":\"很多\"},"
                + "{\"name\":\"小花\",\"date\":\"2026-11-05\",\"wins\":9,\"selfDraws\":0,\"winnings\":-300,\"dealIns\":4,"
                + "\"botLevels\":[\"BEGINNER\",\"BEGINNER\",\"BEGINNER\"],\"basePay\":50,\"taiPay\":10}],"
                + "\"selfDraws\":[],\"winnings\":[]}");
        Map<HonorRoll.Category, List<GameRecord>> roll = HonorRollMessage.parse(message);
        List<GameRecord> wins = roll.get(HonorRoll.Category.WINS);
        check(wins.size() == 2, "壞掉的那筆略過，剩兩筆，實際 " + wins.size());
        same(wins.get(0).name(), "小明");
        same(wins.get(1).name(), "小花");
        check(wins.get(0).winnings() == 12400 && wins.get(0).taiPay() == 20, "數字照抄");
        same(wins.get(0).botLevelsLabel(), "一般／職業／職業");
        same(wins.get(1).botLevelsLabel(), "初學者");
        check(roll.get(HonorRoll.Category.SELF_DRAWS).isEmpty(), "空的類別");
        check(roll.get(HonorRoll.Category.DEAL_INS).isEmpty(), "沒送的類別也是空的");
        check(HonorRollMessage.winningsByStakes(message).isEmpty(), "沒有贏錢紀錄就沒有底台");
        check(HonorRollMessage.latest(List.of()) == -1, "沒有底台可選");
    }

    /**
     * 贏錢金額照服務器 README 的 winningsByStakes 分底台，依底再依每台由小到大；
     * 0.10.0 以前的服務器只有不分底台的 winnings，自己照底台分開，順序照服務器的。
     */
    @SuppressWarnings("unchecked")
    private static void honorRollSplitsWinningsByStakes() {
        String small = "{\"name\":\"小明\",\"date\":\"2026-11-04\",\"wins\":1,\"selfDraws\":0,\"winnings\":900,\"dealIns\":0,"
                + "\"botLevels\":[\"PRO\",\"PRO\",\"PRO\"],\"basePay\":100,\"taiPay\":20}";
        String small2 = "{\"name\":\"小花\",\"date\":\"2026-11-06\",\"wins\":1,\"selfDraws\":0,\"winnings\":300,\"dealIns\":0,"
                + "\"botLevels\":[\"PRO\",\"PRO\",\"PRO\"],\"basePay\":100,\"taiPay\":20}";
        String big = "{\"name\":\"大戶\",\"date\":\"2026-11-06\",\"wins\":1,\"selfDraws\":0,\"winnings\":90000,\"dealIns\":0,"
                + "\"botLevels\":[\"PRO\",\"PRO\",\"PRO\"],\"basePay\":1000,\"taiPay\":200}";
        Map<String, Object> current = (Map<String, Object>) Json.parse("{\"type\":\"honorRoll\",\"winnings\":["
                + big + "," + small + "," + small2 + "],\"winningsByStakes\":["
                + "{\"basePay\":1000,\"taiPay\":200,\"records\":[" + big + "]},"
                + "{\"basePay\":5,\"taiPay\":\"壞掉\",\"records\":[" + small + "]},"
                + "{\"basePay\":300,\"taiPay\":50,\"records\":[]},"
                + "{\"basePay\":100,\"taiPay\":20,\"records\":[" + small + "," + small2 + "]}]}");
        List<HonorRollMessage.StakesBoard> boards = HonorRollMessage.winningsByStakes(current);
        check(boards.size() == 2, "壞掉的與空的那組略過，剩兩組，實際 " + boards.size());
        same(boards.get(0).label(), "底 100／每台 20");
        same(boards.get(1).label(), "底 1,000／每台 200");
        same(boards.get(0).records().get(0).name(), "小明");
        same(boards.get(0).records().get(1).name(), "小花");
        check(HonorRollMessage.latest(boards) == 1, "最近有新紀錄的，同一天取底台大的");

        Map<String, Object> old = (Map<String, Object>) Json.parse("{\"type\":\"honorRoll\",\"winnings\":["
                + big + "," + small + "," + small2 + "]}");
        List<HonorRollMessage.StakesBoard> split = HonorRollMessage.winningsByStakes(old);
        check(split.size() == 2, "舊服務器的照底台分成兩組");
        same(split.get(0).label(), "底 100／每台 20");
        check(split.get(0).records().size() == 2 && split.get(0).records().get(1).name().equals("小花"), "順序照服務器的");
        same(split.get(1).records().get(0).name(), "大戶");
    }

    private static void createRoomCarriesEveryOption() {
        RoomSettings settings = new RoomSettings(20000, 100, 20,
                List.of(BotLevel.BEGINNER, BotLevel.NORMAL, BotLevel.PRO), 30, GameSpeed.FAST, "週末牌搭子", true);
        Map<String, Object> message = Messages.createRoom(settings, true, true);
        same(String.valueOf(message.get("type")), "createRoom");
        same(String.valueOf(message.get("expert")), "true");
        same(String.valueOf(message.get("substitute")), "true");
        same(String.valueOf(Messages.createRoom(settings, false, false).get("expert")), "false");
        same(String.valueOf(Messages.createRoom(settings, false, false).get("substitute")), "false");
        same(Json.write(message.get("botLevels")), "[\"BEGINNER\",\"NORMAL\",\"PRO\"]");
        same(String.valueOf(message.get("chips")), "20000");
        same(String.valueOf(message.get("basePay")), "100");
        same(String.valueOf(message.get("taiPay")), "20");
        same(String.valueOf(message.get("waitSeconds")), "30");
        same(String.valueOf(message.get("speed")), "FAST");
        same(String.valueOf(message.get("name")), "週末牌搭子");
        same(String.valueOf(message.get("private")), "true");
    }

    /** 大廳的「隨時補位」「專家」兩欄與「牌局中」照服務器 README 的 rooms；舊服務器沒有的欄位當作沒開。 */
    @SuppressWarnings("unchecked")
    private static void lobbyReadsRoomFlags() {
        Map<String, Object> message = (Map<String, Object>) Json.parse("{\"type\":\"rooms\",\"rooms\":["
                + "{\"code\":\"AAAAAA\",\"name\":\"a\",\"host\":\"小明\",\"humans\":1,\"playing\":false,\"openSeats\":3,"
                + "\"options\":{\"expert\":true,\"substitute\":false}},"
                + "{\"code\":\"BBBBBB\",\"name\":\"b\",\"host\":\"小花\",\"humans\":2,\"playing\":true,\"openSeats\":1,"
                + "\"options\":{\"expert\":false,\"substitute\":true}},"
                + "{\"code\":\"CCCCCC\",\"name\":\"c\",\"host\":\"小美\",\"humans\":1,\"options\":{}}]}");
        Map<String, LobbyBrowser.RoomFlags> flags = LobbyBrowser.roomFlags(message);
        check(flags.get("AAAAAA").equals(new LobbyBrowser.RoomFlags(true, false, false)), "專家模式、等人中");
        check(flags.get("BBBBBB").equals(new LobbyBrowser.RoomFlags(false, true, true)), "隨時補位、牌局中");
        check(flags.get("CCCCCC").equals(new LobbyBrowser.RoomFlags(false, false, false)), "舊服務器沒有這些欄位");
        check(LobbyBrowser.roomFlags(Map.<String, Object>of("type", "rooms")).isEmpty(), "沒有列表");
    }

    /** 公告要照使用者要的寫法：「玩家 ○○ 替換電腦（××）加入」，離開的人留下的座位寫出是代打誰的。 */
    @SuppressWarnings("unchecked")
    private static void substitutionTextNamesTheComputer() {
        Substitution bot = Substitution.substituted((Map<String, Object>) Json.parse(
                "{\"type\":\"substituted\",\"seat\":2,\"name\":\"小明\",\"replaced\":\"阿土伯\",\"computer\":true}"));
        check(bot.seat() == 2, "座位");
        same(bot.announcement(false), "玩家 小明 替換電腦（阿土伯）加入");
        same(bot.announcement(true), "你替換電腦（阿土伯）加入");
        Substitution left = Substitution.substituted((Map<String, Object>) Json.parse(
                "{\"type\":\"substituted\",\"seat\":1,\"name\":\"小明\",\"replaced\":\"小花\",\"computer\":false}"));
        same(left.announcement(false), "玩家 小明 替換電腦（代打「小花」的座位）加入");
        Substitution waiting = Substitution.waiting((Map<String, Object>) Json.parse(
                "{\"type\":\"substituteWaiting\",\"seat\":3,\"replacing\":\"阿土伯\",\"computer\":true}"));
        check(waiting.seat() == 3, "保留的座位");
        check(waiting.waitingDetail().contains("等電腦（阿土伯）打完這一手"), "說明在等哪一家：" + waiting.waitingDetail());
        check(waiting.waitingDetail().contains("不列入榮譽榜"), "說明不列入榮譽榜");
        boolean rejected = false;
        try {
            Substitution.substituted(Map.<String, Object>of("type", "substituted", "seat", 9L, "name", "x", "replaced", "y"));
        } catch (IllegalArgumentException e) {
            rejected = true;
        }
        check(rejected, "座位不對的訊息看不懂，照其他看不懂的訊息略過");
    }

    /** 樂譜寫錯時各聲部會對不齊。錄好的曲子沒有樂譜。 */
    private static void everyVoiceHasTheSameLength() {
        for (WebMusic track : WebMusic.values()) {
            if (!track.isSynthesized()) {
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

    /**
     * 網頁版自己列曲目，ezmj 有的每一首都要有，名稱與標題一樣，設定存的名稱才通用。
     * 線上固定編譯的 ezmj 還沒有錄好的曲子時，網頁版多出來的照樣能用；有的話（MusicTrack 有 recording()）檔名也要一樣。
     * 用反射找 recording()，舊版的 ezmj 沒有它也編得過。
     */
    private static void musicMatchesSwing() {
        Method recording;
        try {
            recording = MusicTrack.class.getMethod("recording");
        } catch (NoSuchMethodException e) {
            recording = null;
        }
        for (MusicTrack track : MusicTrack.values()) {
            WebMusic web;
            try {
                web = WebMusic.valueOf(track.name());
            } catch (IllegalArgumentException e) {
                throw new AssertionError("網頁版沒有 " + track + "，ezmj 加了曲子要跟著加");
            }
            same(web.label(), track.label());
            if (recording != null) {
                Object file;
                try {
                    file = recording.invoke(track);
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError("讀不到 " + track + " 的錄音檔名", e);
                }
                check(Objects.equals(web.recording(), file), track + " 的錄音檔名應該是 " + file + "，實際是 " + web.recording());
            }
        }
        check(WebMusic.OFF.ordinal() == 0 && !WebMusic.OFF.isSynthesized() && !WebMusic.OFF.isRecorded(), "關閉不是曲子");
        check(WebMusic.CLEAR_THE_BOARD.isRecorded() && WebMusic.SUNLIGHT.isRecorded(), "兩首錄好的曲子");
        same(WebMusic.SUNLIGHT.recordingUrl(), "music/sunlight_on_the_floorboards.mp3");
        // 選單在第一首錄好的曲子前面畫線，錄好的曲子要排在合成的後面
        boolean recorded = false;
        for (WebMusic track : WebMusic.values()) {
            check(!recorded || track.isRecorded(), track + " 排在錄好的曲子後面");
            recorded |= track.isRecorded();
        }
    }

    /** 網站帶著錄好的曲子，是整首的 MP3；web/music 裡沒有用不到的檔案。 */
    private static void recordingsAreShipped() {
        Path dir = Path.of("web", WebMusic.RECORDING_DIR);
        int recordings = 0;
        for (WebMusic track : WebMusic.values()) {
            if (!track.isRecorded()) {
                continue;
            }
            recordings++;
            Path file = dir.resolve(track.recording());
            byte[] head = new byte[3];
            try (InputStream in = Files.newInputStream(file)) {
                check(in.read(head) == 3, file + " 太短");
                check(Files.size(file) > 1_000_000, file + " 不是整首，只有 " + Files.size(file) + " 位元組");
            } catch (IOException e) {
                throw new AssertionError("網站少了 " + file, e);
            }
            boolean id3 = head[0] == 'I' && head[1] == 'D' && head[2] == '3';
            boolean frame = (head[0] & 0xFF) == 0xFF && (head[1] & 0xE0) == 0xE0;
            check(id3 || frame, file + " 不是 MP3");
        }
        try (Stream<Path> files = Files.list(dir)) {
            check(files.count() == recordings, "web/music 裡有用不到的檔案");
        } catch (IOException e) {
            throw new AssertionError("讀不到 " + dir, e);
        }
    }

    /** 和 Swing 版的 RecordedMusic.level 一樣：比 -17 dBFS 大聲才壓到剛好，小聲的不放大。 */
    private static void recordedMusicIsOnlyTurnedDown() {
        check(WebMusic.TARGET_RMS_DB == -17, "目標和 Swing 版一樣是 -17 dBFS");
        double gain = WebMusic.levelGain(-14.3);
        check(Math.abs(20 * Math.log10(gain) - -2.7) < 1e-9, "-14.3 dBFS 壓 2.7 dB，實際倍率 " + gain);
        check(WebMusic.levelGain(-17) == 1, "剛好不動");
        check(WebMusic.levelGain(-19.9) == 1, "小聲的不放大");
        check(WebMusic.levelGain(Double.NEGATIVE_INFINITY) == 1, "沒有聲音不動");
    }

    /** 司儀的話和 Swing 版的 VoiceLines 一樣；流局由司儀宣布，不在喊牌裡。 */
    private static void announcerSaysTheStreak() {
        same(VoiceLines.chineseNumber(1), "一");
        same(VoiceLines.chineseNumber(10), "十");
        same(VoiceLines.chineseNumber(12), "十二");
        same(VoiceLines.chineseNumber(20), "二十");
        same(VoiceLines.chineseNumber(99), "九十九");
        same(VoiceLines.streakText(1), "連莊，連一拉一");
        same(VoiceLines.streakText(2), "連莊，連二拉二");
        same(VoiceLines.streakText(VoiceLines.MAX_ANNOUNCED_STREAK), "連莊，連二十拉二十");
        same(VoiceLines.streakText(VoiceLines.MAX_ANNOUNCED_STREAK + 1), "連莊");
        same(VoiceLines.streakText(35), "連莊");
        boolean rejected = false;
        try {
            VoiceLines.streakText(0);
        } catch (IllegalArgumentException e) {
            rejected = true;
        }
        check(rejected, "沒有連莊不說");
        same(VoiceLines.DRAWN_GAME_TEXT, "流局");
        for (VoiceLines.Line line : VoiceLines.Line.values()) {
            check(!line.text().equals("流局"), "喊牌不再有流局");
        }
    }

    /**
     * 服務器不送骰子，同桌的網頁與 Swing 版各自用房號等資料算，點數與開門的位置要一樣。
     * 這組數字和 ezmj 的 OnlineTest 一樣，算法改了兩邊要一起改。
     */
    @SuppressWarnings("unchecked")
    private static void diceMatchSwingClient() {
        OnlineGame game = new OnlineGame();
        game.applyRoom(RoomInfo.parse((Map<String, Object>) Json.parse("{\"type\":\"room\",\"code\":\"ABC234\","
                + "\"state\":\"playing\",\"yourSeat\":2,\"options\":{\"chips\":5000,\"basePay\":100,\"taiPay\":20,"
                + "\"botLevels\":[\"NORMAL\",\"PRO\",\"BEGINNER\"],\"waitSeconds\":30,\"speed\":\"FAST\"},\"players\":["
                + "{\"seat\":0,\"name\":\"小明\",\"human\":true,\"connected\":true,\"gender\":null},"
                + "{\"seat\":1,\"name\":\"阿土\",\"human\":false,\"connected\":true,\"gender\":\"MALE\"},"
                + "{\"seat\":2,\"name\":\"小花\",\"human\":true,\"connected\":true,\"gender\":null},"
                + "{\"seat\":3,\"name\":\"小美\",\"human\":true,\"connected\":true,\"gender\":null}]}")));
        game.applyRoundStart((Map<String, Object>) Json.parse(
                "{\"type\":\"roundStart\",\"number\":7,\"dealer\":0,\"roundWind\":1,\"streak\":1,\"dealMs\":3000}"));
        check(game.dice().equals(new Dice(1, 3, 1)), "骰子應該是 1、3、1，實際是 " + game.dice());
        same(WallRing.announce(game, game.dice()), "小明 擲出 1、3、1，共 5 點，從小明的牌牆開門");
    }

    /** 閱讀器靠 xref 找物件，位移錯一個位元組就打不開或要修復。 */
    private static void pdfOffsetsPointAtObjects() {
        PdfWriter pdf = new PdfWriter(842, 595);
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, 1, 2, 3, (byte) 0xFF, (byte) 0xD9};
        pdf.addPage(jpeg, 4, 3);
        pdf.addPage(jpeg, 4, 3);
        byte[] bytes = pdf.toBytes("EZMJ 輸贏歷程", "D:20261106213000");
        String text = latin1(bytes, 0, bytes.length);
        check(text.startsWith("%PDF-1.4\n"), "開頭是 PDF 的版本");
        check(text.endsWith("%%EOF\n"), "結尾是 %%EOF");
        check(text.contains("/Count 2"), "兩頁");
        check(text.contains("/Title <FEFF0045005A004D004A00208F388D0F6B777A0B>"), "標題用 UTF-16BE");
        int start = text.lastIndexOf("startxref\n") + "startxref\n".length();
        int xref = Integer.parseInt(text.substring(start, text.indexOf('\n', start)));
        check(text.startsWith("xref\n0 10\n", xref), "startxref 指到 xref，共 9 個物件加上 0 號");
        String[] lines = text.substring(xref).split("\n");
        for (int object = 1; object <= 9; object++) {
            String entry = lines[2 + object];
            check(entry.length() == 19 && entry.endsWith(" 00000 n "), "xref 每筆 20 個位元組：" + entry);
            int offset = Integer.parseInt(entry.substring(0, 10));
            check(text.startsWith(object + " 0 obj\n", offset), object + " 號物件的位移");
        }
        check(text.contains("/Length 7 >>\nstream\n" + latin1(jpeg, 0, jpeg.length) + "\nendstream"), "JPEG 原封不動放進去");
    }

    private static String latin1(byte[] bytes, int from, int to) {
        StringBuilder text = new StringBuilder();
        for (int i = from; i < to; i++) {
            text.append((char) (bytes[i] & 0xFF));
        }
        return text.toString();
    }

    private static void moneyIsGroupedByThousands() {
        same(GameReport.signed(1234567), "+1,234,567");
        same(GameReport.signed(-1000), "-1,000");
        same(GameReport.signed(0), "0");
        same(GameReport.grouped(999), "999");
        same(GameReport.signed(Integer.MIN_VALUE), "-2,147,483,648");
    }

    private static void voiceTextUsesTableNames() {
        same(VoiceLines.tileText(Tile.of(Suit.WIND, 1)), "東風");
        same(VoiceLines.tileText(Tile.of(Suit.DRAGON, 1)), "紅中");
        same(VoiceLines.tileText(Tile.of(Suit.WAN, 5)), "五萬");
    }
}
