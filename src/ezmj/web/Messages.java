package ezmj.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ezmj.model.Tile;
import ezmj.online.OnlineClient;
import ezmj.online.RoomSettings;

/** 送給服務器的訊息，協定見 ezmjService 的 README。不碰瀏覽器，所以可以在 JVM 上測試。 */
final class Messages {

    /** 和 Swing 版用同一個值，協定改了不用另外改網頁，重新編譯就跟上了。 */
    static final int PROTOCOL = OnlineClient.PROTOCOL;

    private Messages() {
    }

    /** 依序是鍵、值、鍵、值。 */
    private static Map<String, Object> object(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }

    static Map<String, Object> ping() {
        return object("type", "ping");
    }

    static Map<String, Object> helloName(String name) {
        return object("type", "hello", "protocol", PROTOCOL, "name", name);
    }

    static Map<String, Object> helloToken(String token) {
        return object("type", "hello", "protocol", PROTOCOL, "token", token);
    }

    static Map<String, Object> createRoom(RoomSettings settings) {
        List<String> levels = new ArrayList<>();
        settings.botLevels().forEach(level -> levels.add(level.name()));
        return object("type", "createRoom", "chips", settings.chips(), "basePay", settings.basePay(),
                "taiPay", settings.taiPay(), "botLevels", levels, "waitSeconds", settings.waitSeconds(),
                "speed", settings.speed().name(), "name", settings.name(), "private", settings.privateRoom());
    }

    /** 不用登入。 */
    static Map<String, Object> listRooms() {
        return object("type", "listRooms");
    }

    static Map<String, Object> joinRoom(String code) {
        return object("type", "joinRoom", "code", code);
    }

    static Map<String, Object> leave() {
        return object("type", "leave");
    }

    static Map<String, Object> start() {
        return object("type", "start");
    }

    static Map<String, Object> ready() {
        return object("type", "ready");
    }

    static Map<String, Object> discard(int waitId, Tile tile) {
        return object("type", "discard", "waitId", waitId, "tile", tile.code());
    }

    static Map<String, Object> listen(int waitId, Tile tile) {
        return object("type", "listen", "waitId", waitId, "tile", tile.code());
    }

    static Map<String, Object> selfWin(int waitId) {
        return object("type", "selfWin", "waitId", waitId);
    }

    static Map<String, Object> kong(int waitId, Tile tile) {
        return object("type", "kong", "waitId", waitId, "tile", tile.code());
    }

    static Map<String, Object> claim(int waitId, int option) {
        return object("type", "claim", "waitId", waitId, "option", option);
    }

    static Map<String, Object> pass(int waitId) {
        return object("type", "pass", "waitId", waitId);
    }
}
