package ezmj.web;

import org.teavm.jso.browser.Window;
import org.teavm.jso.dom.html.HTMLDocument;
import org.teavm.jso.dom.html.HTMLElement;

import ezmj.engine.GameView;
import ezmj.online.RoomSettings;
import ezmj.online.ServerAddress;

/** 網頁的進入點：輸入名字的畫面、房間與牌桌輪流顯示。 */
public final class Main implements OnlineController.Host, OnlinePanel.Actions {

    /** 連了這麼久還沒回應，多半是免費主機睡著了。 */
    private static final int WAKING_NOTICE_MS = 3_000;

    private final HTMLElement app = HTMLDocument.current().getElementById("app");
    private final WebSettings settings = WebSettings.load();
    private final String serverUrl = serverUrl();
    private final OnlinePanel form = new OnlinePanel(this, Browser.meta("ezmj-version"));
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

    private void connecting() {
        form.setBusy("連線中…");
        wakingTimer = Browser.after(WAKING_NOTICE_MS, () -> {
            wakingTimer = -1;
            if (form.isBusy()) {
                form.setBusy("正在喚醒服務器，第一次連線最久要等約一分鐘…");
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
        connecting();
        controller.createRoom(name, roomSettings);
    }

    @Override
    public void playBots(String name, RoomSettings roomSettings) {
        connecting();
        controller.playBots(name, roomSettings);
    }

    @Override
    public void join(String name, String code) {
        connecting();
        controller.joinRoom(name, code);
    }

    @Override
    public void resume(WebProfile profile) {
        connecting();
        controller.resume(profile);
    }

    @Override
    public void cancel() {
        if (controller != null) {
            controller.shutdown();
            controller = null;
        }
        stopWakingNotice();
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
    public void showRoom(RoomPanel room) {
        show(room.element());
    }

    @Override
    public void showTable(TablePanel table) {
        show(table.element());
        table.repaint();
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

    @Override
    public void connected() {
        stopWakingNotice();
    }

    @Override
    public void backToForm(String message) {
        controller = null;
        stopWakingNotice();
        form.setProfile(WebProfile.load());
        show(form.element());
        if (message != null) {
            Dialog.message(message);
        }
    }
}
