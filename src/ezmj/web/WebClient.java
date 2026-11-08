package ezmj.web;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.teavm.jso.dom.events.MessageEvent;
import org.teavm.jso.websocket.WebSocket;

import ezmj.online.Json;
import ezmj.online.OnlineClient;
import ezmj.online.RoomListing;
import ezmj.online.RoomSettings;
import ezmj.model.Tile;

/**
 * 到 EZMJ Server 的連線，對應 Swing 版的 OnlineClient，時間設定也一樣。
 * 這裡只管連線：登入、閒置時送 ping、保存 token、斷線後自己重連。訊息內容交給上層看。
 *
 * 瀏覽器會放慢背景分頁的計時器，所以收到訊息時也順便檢查該不該送 ping：
 * 訊息事件不會被放慢，牌局進行中服務器一直有訊息，就不會因為計時器太慢被踢掉。
 */
final class WebClient {

    /** 免費主機閒置後會睡著，第一個連線要等它醒來，最久大約一分鐘。 */
    private static final int OPEN_TIMEOUT_MS = 90_000;
    /** 服務器 60 秒沒收到訊息就斷線，所以閒置時要比這個頻繁許多。 */
    private static final int PING_MS = 20_000;
    /** ping 都有回應，這段時間什麼都沒收到就是斷了。 */
    private static final int READ_TIMEOUT_MS = 50_000;
    private static final int[] RETRY_DELAYS_MS = {500, 1_000, 2_000, 4_000, 5_000};
    /** 全桌都斷線 2 分鐘服務器就關房間，再等也回不去，所以比那久一點就放棄。 */
    private static final int RETRY_FOR_MS = 180_000;
    /**
     * 第一次連線（還沒收到 welcome）連不上時一直重試，到這麼久才放棄。服務器在 Render 重新部署的一分多鐘裡
     * 會直接拒絕連線，從睡眠醒來時偶爾也會，等一下就好了。
     */
    static final int FIRST_CONNECT_FOR_MS = 120_000;
    private static final int CHECK_MS = 5_000;

    private final String url;
    private final OnlineClient.Listener listener;
    /** 整則 rooms 訊息，大廳要看列表以外的欄位，例如在線人數。 */
    private Consumer<Map<String, Object>> roomsMessage = message -> { };

    private WebSocket socket;
    /** 每開一條連線加一，舊連線晚到的通知就認得出來。 */
    private int serial;
    private boolean opened;
    private boolean loggedIn;
    /** 不登入，只看大廳的房間列表。 */
    private boolean browsing;
    private String token;
    private boolean finished;
    /** 收過 welcome 了。在那之前斷掉的是第一次連線，照 firstDeadline 重試，不算斷線重連。 */
    private boolean welcomedOnce;
    /** 第一次連線的 hello，重試時再送一次。 */
    private Map<String, Object> firstHello;
    private double firstDeadline;
    private boolean lostReported;
    private double retryDeadline;
    private int attempt;
    private int retryTimer = -1;
    private int checkTimer = -1;
    private double lastSent;
    private double lastReceived;
    private double openedAt;

    WebClient(String url, OnlineClient.Listener listener) {
        this.url = url;
        this.listener = listener;
    }

    /** 服務器發的身分，還沒登入時是 null。不能給別人。 */
    String token() {
        return token;
    }

    /** 用名字登入，第一次進服務器時用。連不上會重試，見 FIRST_CONNECT_FOR_MS。 */
    void loginWithName(String name) {
        connectFirst(Messages.helloName(name));
    }

    /** 用之前的 token 接回來。連不上會重試，見 FIRST_CONNECT_FOR_MS。 */
    void loginWithToken(String savedToken) {
        token = savedToken;
        connectFirst(Messages.helloToken(savedToken));
    }

    private void connectFirst(Map<String, Object> hello) {
        firstHello = hello;
        firstDeadline = Browser.now() + FIRST_CONNECT_FOR_MS;
        open(hello);
    }

    /** 還在第一次連線：要登入，但還沒收過 welcome。只看大廳的連線不重試，大廳自己會再要。 */
    private boolean connectingFirst() {
        return !welcomedOnce && !browsing && firstHello != null;
    }

    /** 不登入，連上就要一次房間列表。沒有 token，所以斷線不重連，直接通知 closed；錯誤也照一般訊息通知。 */
    void browse() {
        query(Messages.listRooms());
    }

    /** 和 browse 一樣不登入，連上就送 request，例如查榮譽榜。回應從 Listener.message 通知。 */
    void query(Map<String, Object> request) {
        browsing = true;
        open(request);
    }

    void listRooms() {
        send(Messages.listRooms());
    }

    /** 收到 rooms 時，在 Listener.rooms 之後把整則訊息交給 action。 */
    void onRoomsMessage(Consumer<Map<String, Object>> action) {
        roomsMessage = action;
    }

    void createRoom(RoomSettings settings) {
        send(Messages.createRoom(settings));
    }

    void joinRoom(String code) {
        send(Messages.joinRoom(code));
    }

    void leave() {
        send(Messages.leave());
    }

    void start() {
        send(Messages.start());
    }

    void ready() {
        send(Messages.ready());
    }

    void discard(int waitId, Tile tile) {
        send(Messages.discard(waitId, tile));
    }

    /** 打出這張牌並報聽。 */
    void listen(int waitId, Tile tile) {
        send(Messages.listen(waitId, tile));
    }

    /** 報聽，listenId 是服務器 canListen 給的編號。 */
    void declareListen(int listenId) {
        send(Messages.declareListen(listenId));
    }

    /** 房間裡的聊天，服務器會轉給房間裡每一位連著的真人，也包括自己。 */
    void chat(String text) {
        send(Messages.chat(text));
    }

    void selfWin(int waitId) {
        send(Messages.selfWin(waitId));
    }

    void kong(int waitId, Tile tile) {
        send(Messages.kong(waitId, tile));
    }

    void claim(int waitId, int option) {
        send(Messages.claim(waitId, option));
    }

    void pass(int waitId) {
        send(Messages.pass(waitId));
    }

    /** 目前有可以用的連線。 */
    boolean isConnected() {
        return loggedIn;
    }

    /** 斷線中就丟掉：服務器牌局不等人，接回來時會補送還在等的詢問。 */
    private void send(Map<String, Object> message) {
        if (socket != null && opened) {
            socket.send(Json.write(message));
            lastSent = Browser.now();
        }
    }

    /** 分頁回到前景。正在等下一次重試就馬上試，不然確認連線還活著。 */
    void wake() {
        if (finished) {
            return;
        }
        if (retryTimer >= 0) {
            Browser.cancelAfter(retryTimer);
            retryTimer = -1;
            retry();
        } else {
            check();
        }
    }

    /** 結束這個連線，不再重連也不再通知。 */
    void close() {
        finished = true;
        loggedIn = false;
        stopTimers();
        serial++;
        if (socket != null) {
            socket.close();
            socket = null;
        }
    }

    private void stopTimers() {
        if (retryTimer >= 0) {
            Browser.cancelAfter(retryTimer);
            retryTimer = -1;
        }
        if (checkTimer >= 0) {
            Browser.cancelEvery(checkTimer);
            checkTimer = -1;
        }
    }

    private void open(Map<String, Object> hello) {
        int mine = ++serial;
        opened = false;
        loggedIn = false;
        openedAt = Browser.now();
        lastReceived = openedAt;
        lastSent = openedAt;
        try {
            socket = new WebSocket(url);
        } catch (RuntimeException e) {
            // 網址不對或頁面不准連，重試也沒有用
            finish("連不上服務器 " + url);
            return;
        }
        socket.onOpen(event -> {
            if (mine == serial) {
                opened = true;
                lastReceived = Browser.now();
                send(hello);
            }
        });
        socket.onMessage((MessageEvent event) -> {
            if (mine == serial) {
                received(event.getDataAsString());
            }
        });
        socket.onClose(event -> {
            if (mine == serial) {
                lost();
            }
        });
        if (checkTimer < 0) {
            checkTimer = Browser.every(CHECK_MS, this::check);
        }
    }

    /** 太久沒有訊息就當作斷了，例如對方電腦關機時不會有任何通知。閒置太久就送 ping。 */
    private void check() {
        if (finished || socket == null) {
            return;
        }
        double now = Browser.now();
        if (!opened) {
            // 第一次連線到期限了也不再等這一條，不然最後一次重試會拖到 OPEN_TIMEOUT_MS
            if (now - openedAt >= OPEN_TIMEOUT_MS || (connectingFirst() && now > firstDeadline)) {
                dropSocket();
                lost();
            }
        } else if (now - lastReceived >= READ_TIMEOUT_MS) {
            dropSocket();
            lost();
        } else if (now - lastSent >= PING_MS) {
            send(Messages.ping());
        }
    }

    private void dropSocket() {
        serial++;
        socket.close();
    }

    private void lost() {
        socket = null;
        boolean wasOpen = opened;
        loggedIn = false;
        opened = false;
        if (finished) {
            return;
        }
        if (connectingFirst()) {
            // 畫面還蓋著「正在喚醒服務器」，不用通知上層，安靜地再試
            if (Browser.now() > firstDeadline) {
                finish("連不上服務器，請稍後再試一次。");
            } else {
                retryLater();
            }
            return;
        }
        if (token == null) {
            finish(wasOpen ? "連線中斷" : "連不上服務器");
            return;
        }
        if (!lostReported) {
            lostReported = true;
            attempt = 0;
            retryDeadline = Browser.now() + RETRY_FOR_MS;
            listener.connectionLost();
        }
        if (Browser.now() > retryDeadline) {
            finish("重新連線失敗，連不上服務器");
            return;
        }
        retryLater();
    }

    private void retryLater() {
        int delay = RETRY_DELAYS_MS[Math.min(attempt++, RETRY_DELAYS_MS.length - 1)];
        retryTimer = Browser.after(delay, () -> {
            retryTimer = -1;
            retry();
        });
    }

    /** 第一次連線再送一樣的 hello；登入過就用 token 接回來。 */
    private void retry() {
        if (!finished) {
            open(connectingFirst() ? firstHello : Messages.helloToken(token));
        }
    }

    /** 不再重試，然後通知上層。 */
    private void finish(String reason) {
        close();
        listener.closed(reason);
    }

    private void received(String line) {
        lastReceived = Browser.now();
        check();
        if (line.trim().isEmpty()) {
            return;
        }
        Map<String, Object> message;
        try {
            Object parsed = Json.parse(line);
            if (!(parsed instanceof Map)) {
                throw new IllegalArgumentException("訊息要是 JSON 物件");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> object = (Map<String, Object>) parsed;
            message = object;
        } catch (IllegalArgumentException e) {
            listener.badMessage(line, e);
            return;
        }
        Object type = message.get("type");
        if ("pong".equals(type)) {
            return;
        }
        if ("rooms".equals(type)) {
            List<RoomListing> rooms;
            try {
                rooms = RoomListing.parse(message);
            } catch (IllegalArgumentException e) {
                listener.badMessage(line, e);
                return;
            }
            listener.rooms(rooms);
            roomsMessage.accept(message);
        } else if (loggedIn || browsing) {
            listener.message(message);
        } else if ("welcome".equals(type)) {
            welcomed(message);
        } else if ("error".equals(type)) {
            String code = String.valueOf(message.get("code"));
            String reason = String.valueOf(message.get("message"));
            close();
            listener.loginRejected(code, reason);
        }
    }

    private void welcomed(Map<String, Object> welcome) {
        boolean resumed = token != null;
        token = String.valueOf(welcome.get("token"));
        loggedIn = true;
        welcomedOnce = true;
        attempt = 0;
        lostReported = false;
        listener.loggedIn(welcome, resumed);
    }
}
