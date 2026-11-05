package ezmj.web;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.teavm.jso.dom.events.Event;
import org.teavm.jso.dom.events.KeyboardEvent;
import org.teavm.jso.dom.html.HTMLButtonElement;
import org.teavm.jso.dom.html.HTMLElement;
import org.teavm.jso.dom.html.HTMLInputElement;

import ezmj.engine.GameView;
import ezmj.engine.RoundEvent;
import ezmj.engine.RoundView;
import ezmj.model.Dice;
import ezmj.model.Player;
import ezmj.model.Tile;

/**
 * 牌桌主畫面：中間是牌桌，下方是動作列，右側是計分板、紀錄與聊天。多人遊戲不能電腦代打，所以沒有那顆按鈕。
 * 紀錄一律預設收成一行，空出來的地方給聊天，對應 Swing 版的同名類別。
 * 手機橫向時動作列蓋在牌桌上：提示在左下、按鈕在右下、選單與全螢幕在右上角，位置由牌桌算好。
 */
final class TablePanel {

    /** 聊天一則最多幾個字，和服務器一樣。 */
    static final int MAX_CHAT_LENGTH = 100;

    /** 動作列上的一個按鈕。 */
    record ActionItem(String label, String color, Runnable run) {
    }

    private final HTMLElement root = Dom.el("div", "table-screen");
    private final TableCanvas canvas;
    private final HTMLElement hint = Dom.text("div", "hint", " ");
    private final HTMLElement actionButtons = Dom.el("div", "action-buttons");
    private final HTMLElement roundInfo = Dom.text("div", "round-info", " ");
    private final HTMLElement stakesInfo = Dom.text("div", "stakes-info", " ");
    private final HTMLElement roomInfo = Dom.text("div", "stakes-info", " ");
    /** 每家一列，欄位依序是門風、玩家、籌碼。 */
    private final HTMLElement[][] scoreCells = new HTMLElement[4][];
    private final HTMLElement logArea = Dom.el("div", "log");
    private boolean logExpanded;
    /** 紀錄收起來時只顯示最新一則。 */
    private final HTMLElement latestLog = Dom.text("span", "log-latest", " ");
    /** 箭頭是 CSS 畫的，按鈕本身沒有字，說明放在 title 與 aria-label。 */
    private final HTMLButtonElement logToggle = Dom.button("", Theme.PANEL_LIGHT, () -> setLogExpanded(!logExpanded));
    private final HTMLElement chatBox = Dom.el("div", "chat");
    private final HTMLElement chatList = Dom.el("div", "chat-list");
    private final HTMLElement chatNotice = Dom.text("div", "chat-notice", " ");
    private final HTMLInputElement chatField = (HTMLInputElement) Dom.el("input", "field chat-field");
    private final HTMLElement sidebar = Dom.el("div", "sidebar");
    private final HTMLElement shade = Dom.el("div", "drawer-shade");
    private final HTMLElement menu;
    private boolean drawerOpen;
    /** 聊天送出的地方；沒有聊天時是 null。 */
    private Consumer<String> chatSender;

    TablePanel(Consumer<Tile> onDiscard, Runnable onLeave, Runnable onShowHistory, Runnable onSettings, Runnable onHelp) {
        canvas = new TableCanvas(onDiscard);
        HTMLElement main = Dom.el("div", "table-main");
        main.appendChild(canvas.element());
        HTMLElement actionBar = Dom.el("div", "action-bar");
        // 手機直向時兩顆按鈕分在動作列左右上角，橫向時一起浮在牌桌右上角
        HTMLElement corner = Dom.el("div", "corner-buttons");
        menu = Dom.button("選單", Theme.PANEL_LIGHT, () -> showDrawer(true));
        menu.setClassName("btn menu-button");
        corner.appendChild(menu);
        if (Browser.canFullscreen()) {
            HTMLElement fullscreen = Dom.button("全螢幕", Theme.PANEL_LIGHT, Browser::toggleFullscreen);
            fullscreen.setClassName("btn fullscreen-button");
            corner.appendChild(fullscreen);
        }
        actionBar.appendChild(corner);
        actionBar.appendChild(hint);
        actionBar.appendChild(actionButtons);
        main.appendChild(actionBar);
        canvas.setOverlay(actionBar);
        root.appendChild(main);
        shade.addEventListener("click", event -> showDrawer(false));
        root.appendChild(shade);
        buildSidebar(onLeave, onShowHistory, onSettings, onHelp);
        root.appendChild(sidebar);
        setLogExpanded(false);
    }

    /** 手機放不下側欄，要按選單才滑出來。打開就收掉選單上有新聊天的記號。 */
    private void showDrawer(boolean open) {
        drawerOpen = open;
        sidebar.setClassName(open ? "sidebar open" : "sidebar");
        shade.setClassName(open ? "drawer-shade open" : "drawer-shade");
        if (open) {
            menu.setClassName("btn menu-button");
        }
    }

    HTMLElement element() {
        return root;
    }

    /** 大小改了之後要重畫，畫布會自己跟著容器調整。 */
    void repaint() {
        canvas.repaint();
    }

    /** 連線牌局沒有思考時間的紀錄，這一行顯示房號。 */
    void showRoomCode(String code) {
        roomInfo.setInnerText("房號 " + code);
    }

    private void buildSidebar(Runnable onLeave, Runnable onShowHistory, Runnable onSettings, Runnable onHelp) {
        HTMLElement side = sidebar;
        HTMLElement close = Dom.button("關閉選單", Theme.BUTTON, () -> showDrawer(false));
        close.setClassName("btn menu-close");
        side.appendChild(close);
        side.appendChild(roundInfo);
        side.appendChild(stakesInfo);
        side.appendChild(buildScoreTable());
        side.appendChild(roomInfo);
        side.appendChild(buildLogHeader());
        side.appendChild(logArea);
        side.appendChild(buildChatBox());
        side.appendChild(Dom.button("輸贏歷程", Theme.PANEL_LIGHT, onShowHistory));
        HTMLElement row = Dom.el("div", "side-row");
        row.appendChild(Dom.button("環境設定", Theme.PANEL_LIGHT, onSettings));
        row.appendChild(Dom.button("說明", Theme.PANEL_LIGHT, onHelp));
        side.appendChild(row);
        side.appendChild(Dom.button("離開牌局", Theme.PANEL_LIGHT, onLeave));
    }

    /** 「紀錄」一行：收起來時顯示最新一則，右邊的箭頭展開或收合，點這一行也可以。 */
    private HTMLElement buildLogHeader() {
        HTMLElement header = Dom.el("div", "log-header");
        header.appendChild(Dom.text("span", "side-title", "紀錄"));
        header.appendChild(latestLog);
        // 按鈕自己會切換，不要再傳到這一行又切回去
        logToggle.addEventListener("click", Event::stopPropagation);
        header.appendChild(logToggle);
        header.addEventListener("click", event -> setLogExpanded(!logExpanded));
        return header;
    }

    /** 聊天：訊息列表、一行說明、輸入框與送出，按 Enter 也送出。有聊天時才出現。 */
    private HTMLElement buildChatBox() {
        chatBox.appendChild(Dom.text("div", "side-title", "聊天"));
        chatBox.appendChild(chatList);
        chatBox.appendChild(chatNotice);
        chatField.setType("text");
        chatField.setMaxLength(MAX_CHAT_LENGTH * 2);
        chatField.setAttribute("autocomplete", "off");
        chatField.setAttribute("placeholder", "最多 " + MAX_CHAT_LENGTH + " 字");
        chatField.addEventListener("input", event -> {
            String limited = OnlinePanel.limited(chatField.getValue(), MAX_CHAT_LENGTH);
            if (!limited.equals(chatField.getValue())) {
                chatField.setValue(limited);
            }
        });
        chatField.addEventListener("keydown", event -> {
            if ("Enter".equals(((KeyboardEvent) event).getKey())) {
                sendChat();
            }
        });
        HTMLElement input = Dom.el("div", "chat-input");
        input.appendChild(chatField);
        input.appendChild(Dom.button("送出", Theme.BUTTON, this::sendChat));
        chatBox.appendChild(input);
        Dom.visible(chatBox, false);
        return chatBox;
    }

    /** 紀錄一律預設收起來，只留「紀錄」那一行，空出來的地方給聊天。箭頭朝下是展開，朝上是收合。 */
    void setLogExpanded(boolean expanded) {
        logExpanded = expanded;
        Dom.visible(logArea, expanded);
        Dom.visible(latestLog, !expanded);
        logToggle.setClassName(expanded ? "btn log-toggle expanded" : "btn log-toggle");
        String action = expanded ? "收合紀錄" : "展開紀錄";
        logToggle.setAttribute("title", action);
        logToggle.setAttribute("aria-label", action);
        logToggle.setAttribute("aria-expanded", String.valueOf(expanded));
        if (expanded) {
            logArea.setScrollTop(logArea.getScrollHeight());
        }
    }

    /** 連線的牌桌都有聊天，跟電腦打的一人房間也有，sender 是送出的地方。傳 null 就藏起來。 */
    void setChat(Consumer<String> sender) {
        chatSender = sender;
        Dom.visible(chatBox, sender != null);
    }

    /** 名字與文字來自網路，一律用 innerText。手機的選單收著時，在選單上標出有新訊息。 */
    void appendChat(String name, String text, boolean mine) {
        HTMLElement line = Dom.el("div", mine ? "chat-line mine" : "chat-line");
        line.appendChild(Dom.text("span", "chat-name", (mine ? "我" : name) + "："));
        line.appendChild(Dom.text("span", "", text));
        chatList.appendChild(line);
        chatList.setScrollTop(chatList.getScrollHeight());
        chatNotice.setInnerText(" ");
        if (!mine && !drawerOpen) {
            menu.setClassName("btn menu-button unread");
        }
    }

    /** 聊天送不出去的原因，例如說太快或舊服務器不能聊天。 */
    void showChatNotice(String text) {
        chatNotice.setInnerText(text);
    }

    private void sendChat() {
        String text = chatField.getValue().trim();
        if (chatSender == null || text.isEmpty()) {
            return;
        }
        chatSender.accept(text);
        chatField.setValue("");
    }

    private HTMLElement buildScoreTable() {
        GridTable table = new GridTable(2, 1);
        table.header("門風", "玩家", "籌碼");
        for (int seat = 0; seat < 4; seat++) {
            scoreCells[seat] = table.row(seat == 0, " ", " ", " ");
            for (HTMLElement cell : scoreCells[seat]) {
                Dom.color(cell, seat == 0 ? Theme.GOLD : Theme.TEXT);
            }
            // 名字太長時只縮玩家這欄，後面用刪節號，籌碼才不會被擠掉
            scoreCells[seat][1].setClassName("left stretch ellipsis");
        }
        return table.element();
    }

    /** 換一局。dice 是這局開門的骰子，牌桌照它畫牌牆。 */
    void bindRound(RoundView round, Dice dice) {
        Dom.clear(logArea);
        latestLog.setInnerText(" ");
        setDiscardPrompt(false);
        canvas.setRound(round, dice);
    }

    /**
     * 吃碰槓之後要打一張牌：動作列的提示放大成金色粗體並閃動，手牌外框也一明一暗，直到打出牌。
     * 一般的提示是灰色的小字。
     */
    void setDiscardPrompt(boolean on) {
        canvas.setDiscardPrompt(on);
        hint.setClassName(on ? "hint prompt" : "hint");
    }

    void animate(RoundEvent event) {
        canvas.animate(event);
    }

    void animateDeal() {
        canvas.animateDeal();
    }

    void appendLog(String message) {
        logArea.appendChild(Dom.text("div", "", message));
        logArea.setScrollTop(logArea.getScrollHeight());
        latestLog.setInnerText(message);
        latestLog.setAttribute("title", message);
    }

    /** 由目前狀態更新計分板與牌桌。 */
    void refresh(GameView game, RoundView round) {
        String streak = round.streak() > 0 ? " 連" + round.streak() : "";
        roundInfo.setInnerText(TableCanvas.roundLabel(round) + streak);
        stakesInfo.setInnerText("底 " + game.basePay() + " / 每台 " + game.taiPay());
        for (int seat = 0; seat < 4; seat++) {
            Player player = game.player(seat);
            scoreCells[seat][0].setInnerText(Tile.windName(round.seatWind(seat)));
            if (round.dealer() == seat) {
                HTMLElement badge = Dom.text("span", "dealer-badge", "莊");
                badge.setAttribute("title", "莊家");
                scoreCells[seat][0].appendChild(badge);
            }
            scoreCells[seat][1].setInnerText(player.name());
            String traits = game.traits(seat);
            scoreCells[seat][1].setAttribute("title", traits == null ? player.name() : player.name() + "（" + traits + "）");
            scoreCells[seat][2].setInnerText(String.valueOf(player.chips()));
        }
        canvas.repaint();
    }

    /** 只更新提示文字，按鈕不動。 */
    void setHint(String hintText) {
        hint.setInnerText(hintText);
    }

    void showActions(String hintText, boolean discardEnabled, List<ActionItem> actions) {
        hint.setInnerText(hintText);
        canvas.setDiscardEnabled(discardEnabled);
        setButtons(actions);
    }

    /** 進入選牌：只有 tiles 裡的手牌能點，其他蓋上遮罩。first 是已經選好的牌，沒有就是 null。 */
    void showPick(String hintText, Set<Tile> tiles, Tile first, Consumer<Tile> onPick, List<ActionItem> actions) {
        hint.setInnerText(hintText);
        canvas.startPick(tiles, first, onPick);
        setButtons(actions);
    }

    /** 吃碰槓胡這些宣告做得大又醒目，胡與自摸再閃動；過、取消這種淺色的維持原本的大小。 */
    private void setButtons(List<ActionItem> actions) {
        Dom.clear(actionButtons);
        for (ActionItem action : actions) {
            HTMLElement button = Dom.button(action.label(), action.color(), action.run());
            if (Theme.RED.equals(action.color())) {
                button.setClassName("btn claim-button win-button");
            } else if (!Theme.PANEL_LIGHT.equals(action.color())) {
                button.setClassName("btn claim-button");
            }
            actionButtons.appendChild(button);
        }
    }
}
