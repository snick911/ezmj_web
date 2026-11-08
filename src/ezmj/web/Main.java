package ezmj.web;

import org.teavm.jso.browser.Window;
import org.teavm.jso.dom.html.HTMLDocument;
import org.teavm.jso.dom.html.HTMLElement;

import ezmj.engine.GameView;
import ezmj.online.RoomSettings;
import ezmj.online.ServerAddress;

/** 網頁的進入點：輸入名字的畫面、房間與牌桌輪流顯示。 */
public final class Main implements OnlineController.Host, OnlinePanel.Actions {

    /** 連了這麼久還沒回應，多半是免費主機睡著了，或正在重新部署，連不上會一直重試，見 WebClient.FIRST_CONNECT_FOR_MS。 */
    private static final int WAKING_NOTICE_MS = 3_000;
    private static final String WAKING = "正在喚醒服務器，最久要等一兩分鐘…";

    private final HTMLElement app = HTMLDocument.current().getElementById("app");
    private final WebSettings settings = WebSettings.load();
    private final String serverUrl = serverUrl();
    private final OnlinePanel form = new OnlinePanel(this, Browser.meta("ezmj-version"), serverUrl);
    private final WaitingOverlay waiting = new WaitingOverlay();
    private OnlineController controller;
    private HistoryDialog history;
    private HelpDialog help;
    private int wakingTimer = -1;

    private Main() {
    }

    public static void main(String[] args) {
        new Main().start();
    }

    private void start() {
        Theme.setFontPercent(settings.fontPercent);
        Theme.setTilePercent(settings.tilePercent);
        SoundEffects.setVolume(settings.soundVolume);
        BackgroundMusic.setVolume(settings.musicVolume);
        VoiceLines.setVolume(settings.voiceVolume);
        Browser.onGesture(() -> {
            AudioOut.unlock();
            BackgroundMusic.select(settings.music);
        });
        Browser.onFontsLoaded(() -> {
            TilePainter.clearCache();
            repaint();
        });
        Window.current().addEventListener("resize", event -> repaint());
        Browser.onVisible(() -> {
            if (controller != null) {
                controller.wake();
            }
        });
        backToForm(null);
    }

    /**
     * 服務器是固定的，畫面上不能改。token 會送給服務器，如果網址能指定服務器，別人就能用一個連結把 token 騙走，
     * 所以只有網頁本身開在本機時才看 server 參數，那是測試用的，不會碰到線上的服務器。
     */
    private static String serverUrl() {
        String host = Browser.hostName();
        String override = Browser.queryParameter("server");
        if (override != null && (host.equals("localhost") || host.equals("127.0.0.1"))) {
            return override;
        }
        return ServerAddress.RENDER;
    }

    private void show(HTMLElement screen) {
        Dom.clear(app);
        app.appendChild(screen);
    }

    private void repaint() {
        if (controller != null) {
            controller.repaint();
        }
    }

    /** waitText 是等服務器時蓋在畫面上的那行字，連線期間可以取消。 */
    private void connecting(String waitText) {
        form.setBusy("連線中…");
        waiting.show(waitText, this::cancel);
        wakingTimer = Browser.after(WAKING_NOTICE_MS, () -> {
            wakingTimer = -1;
            if (form.isBusy()) {
                form.setBusy(WAKING);
                waiting.setDetail(WAKING);
            }
        });
        controller = new OnlineController(this, settings, serverUrl);
    }

    private void stopWakingNotice() {
        if (wakingTimer >= 0) {
            Browser.cancelAfter(wakingTimer);
            wakingTimer = -1;
        }
    }

    @Override
    public void create(String name, RoomSettings roomSettings) {
        connecting("房間建立中，請耐心等待…");
        controller.createRoom(name, roomSettings);
    }

    @Override
    public void playBots(String name, RoomSettings roomSettings) {
        connecting(OnlineController.STARTING);
        controller.playBots(name, roomSettings);
    }

    @Override
    public void join(String name, String code) {
        connecting("加入房間中，請耐心等待…");
        controller.joinRoom(name, code);
    }

    @Override
    public void resume(WebProfile profile) {
        connecting("接回牌局中，請耐心等待…");
        controller.resume(profile);
    }

    @Override
    public void cancel() {
        if (controller != null) {
            controller.shutdown();
            controller = null;
        }
        stopWakingNotice();
        waiting.hide();
        form.setBusy(null);
    }

    @Override
    public void settings() {
        showSettings();
    }

    @Override
    public void help() {
        showHelp();
    }

    @Override
    public void honorRoll(String name) {
        new HonorRollDialog(serverUrl, name).show();
    }

    @Override
    public void showRoom(RoomPanel room) {
        form.stopLobby();
        show(room.element());
    }

    @Override
    public void showTable(TablePanel table) {
        form.stopLobby();
        waiting.hide();
        show(table.element());
        table.repaint();
    }

    @Override
    public void showWaiting(String text) {
        waiting.show(text, null);
    }

    @Override
    public void hideWaiting() {
        waiting.hide();
    }

    @Override
    public void showHistory(GameView game) {
        closeHistory();
        history = new HistoryDialog(game);
        history.show();
    }

    @Override
    public void refreshHistory() {
        if (history != null && history.isOpen()) {
            history.refresh();
        }
    }

    @Override
    public void closeHistory() {
        if (history != null) {
            history.close();
            history = null;
        }
    }

    @Override
    public void showSettings() {
        new SettingsDialog(settings, this::repaint).show();
    }

    @Override
    public void showHelp() {
        if (help == null || !help.isOpen()) {
            help = new HelpDialog();
            help.show();
        }
    }

    /** 連上了，接著等服務器開房間或加入，正在喚醒的說明收掉。 */
    @Override
    public void connected() {
        stopWakingNotice();
        waiting.setDetail("");
    }

    @Override
    public void backToForm(String message) {
        controller = null;
        stopWakingNotice();
        waiting.hide();
        form.setProfile(WebProfile.load());
        show(form.element());
        // 房間列表只在看得到大廳時更新
        form.startLobby();
        if (message != null) {
            Dialog.message(message);
        }
    }
}
