package ezmj.web;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.teavm.jso.dom.events.Event;
import org.teavm.jso.dom.events.EventListener;
import org.teavm.jso.dom.events.KeyboardEvent;
import org.teavm.jso.dom.html.HTMLButtonElement;
import org.teavm.jso.dom.html.HTMLDocument;
import org.teavm.jso.dom.html.HTMLElement;
import org.teavm.jso.dom.html.HTMLInputElement;

import ezmj.engine.GameView;
import ezmj.engine.RoundEvent;
import ezmj.engine.RoundView;
import ezmj.model.BadWords;
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
    /** 牌桌上的聊天泡泡：每家最多疊幾則，停留時間照字數，到了就淡出。 */
    private static final int MAX_BUBBLES = 3;
    private static final int BUBBLE_MIN_MS = 3_500;
    private static final int BUBBLE_PER_CHAR_MS = 90;
    private static final int BUBBLE_MAX_MS = 9_000;
    /** 和 style.css 的 .chat-bubble.leaving 一樣長。 */
    private static final int BUBBLE_FADE_MS = 400;
    /** 牌桌上的公告停多久，例如有人補位上場；淡出和 style.css 的 .table-announce.leaving 一樣長。 */
    private static final int ANNOUNCE_MS = 8_000;
    private static final int ANNOUNCE_FADE_MS = 600;

    /** 動作列上的一個按鈕。translucent 的按鈕半透明，表示可以用但不一定要按。 */
    record ActionItem(String label, String color, Runnable run, boolean translucent) {
        ActionItem(String label, String color, Runnable run) {
            this(label, color, run, false);
        }
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
    /** 專家模式：紀錄一直只顯示最新一則，不能展開。 */
    private boolean logLocked;
    /** 紀錄收起來時只顯示最新一則。 */
    private final HTMLElement latestLog = Dom.text("span", "log-latest", " ");
    /** 箭頭是 CSS 畫的，按鈕本身沒有字，說明放在 title 與 aria-label。 */
    private final HTMLButtonElement logToggle = Dom.button("", Theme.PANEL_LIGHT, () -> setLogExpanded(!logExpanded));
    private final HTMLElement chatBox = Dom.el("div", "chat");
    private final HTMLElement chatList = Dom.el("div", "chat-list");
    private final HTMLElement chatNotice = Dom.text("div", "chat-notice", " ");
    private final HTMLInputElement chatField = (HTMLInputElement) Dom.el("input", "field chat-field");
    /** 蓋在牌桌上的聊天泡泡，點擊會穿過去；四家各一疊，位置由牌桌算好。 */
    private final HTMLElement chatLayer = Dom.el("div", "chat-layer");
    private final HTMLElement[] chatSeats = new HTMLElement[4];
    /** 牌桌上方的公告，同時有好幾則就往下疊，和聊天泡泡同一層，點擊會穿過去。 */
    private final HTMLElement announceStack = Dom.el("div", "announce-stack");
    /** 在牌桌上按 Enter 跳出來的輸入框，送出或按 Esc 就收起來。 */
    private final HTMLElement quickChat = Dom.el("div", "quick-chat");
    private final HTMLInputElement quickField = (HTMLInputElement) Dom.el("input", "field quick-chat-field");
    private final EventListener<Event> hotkey = event -> onHotkey((KeyboardEvent) event);
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
        buildChatLayer();
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

    /** 牌桌上的聊天泡泡與快速輸入框。按鍵聽整頁的，牌桌換掉之後第一次按鍵時就不再聽。 */
    private void buildChatLayer() {
        for (int seat = 0; seat < chatSeats.length; seat++) {
            chatSeats[seat] = Dom.el("div", "chat-seat seat-" + seat);
            chatLayer.appendChild(chatSeats[seat]);
        }
        announceStack.setAttribute("role", "status");
        announceStack.setAttribute("aria-live", "polite");
        chatLayer.appendChild(announceStack);
        quickField.setType("text");
        quickField.setMaxLength(MAX_CHAT_LENGTH * 2);
        quickField.setAttribute("autocomplete", "off");
        quickField.setAttribute("enterkeyhint", "send");
        quickField.setAttribute("placeholder", "說點什麼，Enter 送出，Esc 取消");
        quickField.setAttribute("aria-label", "聊天");
        quickField.addEventListener("input", event -> {
            String limited = OnlinePanel.limited(quickField.getValue(), MAX_CHAT_LENGTH);
            if (!limited.equals(quickField.getValue())) {
                quickField.setValue(limited);
            }
        });
        quickField.addEventListener("keydown", event -> {
            KeyboardEvent key = (KeyboardEvent) event;
            if (Browser.isComposing(key)) {
                return;
            }
            // 不要再傳到整頁的按鍵，不然送出後輸入框又被同一個 Enter 打開
            if ("Enter".equals(key.getKey())) {
                key.preventDefault();
                key.stopPropagation();
                sendQuickChat();
            } else if ("Escape".equals(key.getKey())) {
                key.preventDefault();
                key.stopPropagation();
                closeQuickChat();
            }
        });
        // 點到別的地方就收起來，打到一半的字留著，下次打開還在
        quickField.addEventListener("blur", event -> Dom.visible(quickChat, false));
        quickChat.appendChild(quickField);
        Dom.visible(quickChat, false);
        chatLayer.appendChild(quickChat);
        canvas.element().appendChild(chatLayer);
        canvas.setChatLayer(chatLayer);
        HTMLDocument.current().addEventListener("keydown", hotkey);
    }

    /** 在牌桌上按 Enter 就跳出輸入框，游標在別的輸入框、按鈕上或有視窗蓋著時不搶。 */
    private void onHotkey(KeyboardEvent event) {
        if (root.getParentNode() == null) {
            HTMLDocument.current().removeEventListener("keydown", hotkey);
            return;
        }
        if (chatSender == null || !"Enter".equals(event.getKey()) || event.isCtrlKey() || event.isAltKey()
                || event.isMetaKey() || event.isShiftKey() || Browser.isComposing(event) || Browser.keyBelongsElsewhere()) {
            return;
        }
        event.preventDefault();
        Dom.visible(quickChat, true);
        quickField.focus();
    }

    /** 有不雅字眼就不送，輸入框留著讓他改。 */
    private void sendQuickChat() {
        String text = quickField.getValue().trim();
        if (chatSender != null && BadWords.contains(text)) {
            showChatNotice(BadWords.CHAT_REJECTED);
            return;
        }
        if (chatSender != null && !text.isEmpty()) {
            chatSender.accept(text);
            quickField.setValue("");
        }
        closeQuickChat();
    }

    private void closeQuickChat() {
        Dom.visible(quickChat, false);
        quickField.blur();
    }

    /** 聊天：訊息列表、一行說明、輸入框與送出，按 Enter 也送出。有聊天時才出現。 */
    private HTMLElement buildChatBox() {
        HTMLElement heading = Dom.el("div", "chat-heading");
        heading.appendChild(Dom.text("span", "side-title", "聊天"));
        if (!Browser.isTouchDevice()) {
            heading.appendChild(Dom.text("span", "chat-tip", "牌桌上按 Enter 直接打字"));
        }
        chatBox.appendChild(heading);
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

    /**
     * 專家模式：紀錄收起來只留最新一則，展開的箭頭藏起來；牌桌的棄牌每家只留最後一張，中間不寫剩幾張。
     * 一般模式照舊。
     */
    void setExpert(boolean on) {
        logLocked = on;
        Dom.visible(logToggle, !on);
        setLogExpanded(false);
        canvas.setExpert(on);
    }

    /** 每個事件都會送來，有沒有動畫都一樣。 */
    void noteEvent(RoundEvent event) {
        canvas.noteEvent(event);
    }

    /** 紀錄一律預設收起來，只留「紀錄」那一行，空出來的地方給聊天。箭頭朝下是展開，朝上是收合。 */
    void setLogExpanded(boolean expanded) {
        if (logLocked && expanded) {
            return;
        }
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

    /**
     * 名字與文字來自網路，一律用 innerText。seat 是畫面座位，0 是自己。
     * 牌桌上在說話的那家旁邊跳出泡泡，幾秒後消失；手機的選單收著時，在選單上標出有新訊息。
     */
    void appendChat(int seat, String name, String text, boolean mine) {
        HTMLElement line = Dom.el("div", mine ? "chat-line mine" : "chat-line");
        line.appendChild(Dom.text("span", "chat-name", (mine ? "我" : name) + "："));
        line.appendChild(Dom.text("span", "", text));
        chatList.appendChild(line);
        chatList.setScrollTop(chatList.getScrollHeight());
        chatNotice.setInnerText(" ");
        showBubble(seat, mine ? "我" : name, text, mine ? "chat-bubble mine" : "chat-bubble");
        if (!mine && !drawerOpen) {
            menu.setClassName("btn menu-button unread");
        }
    }

    /**
     * 牌桌上方跳出醒目的公告，停幾秒再淡出，例如有人補位上場。名字來自網路，一律用 innerText。
     * detail 是下面一行小字，可以是 null。
     */
    void announce(String title, String detail) {
        HTMLElement box = Dom.el("div", "table-announce");
        box.appendChild(Dom.text("div", "announce-title", title));
        if (detail != null) {
            box.appendChild(Dom.text("div", "announce-detail", detail));
        }
        announceStack.appendChild(box);
        Browser.after(ANNOUNCE_MS, () -> {
            box.setClassName("table-announce leaving");
            Browser.after(ANNOUNCE_FADE_MS, () -> Dom.remove(box));
        });
    }

    /** 聊天區裡不是誰說的話，例如有人補位上場，置中的金色字，事後還看得到。 */
    void appendSystemLine(String text) {
        chatList.appendChild(Dom.text("div", "chat-line system", text));
        chatList.setScrollTop(chatList.getScrollHeight());
    }

    /** 這一家旁邊跳出大字，seat 是畫面座位。 */
    void showSeatBanner(int seat, String text) {
        canvas.showSeatBanner(seat, text);
    }

    /** 聊天送不出去的原因，例如說太快或舊服務器不能聊天。用快速輸入框的人看不到側欄，所以牌桌上也跳一下。 */
    void showChatNotice(String text) {
        chatNotice.setInnerText(text);
        showBubble(0, null, text, "chat-bubble notice");
    }

    /** 太多就先拿掉最舊的。name 是 null 時只有一行字。 */
    private void showBubble(int seat, String name, String text, String cssClass) {
        if (seat < 0 || seat >= chatSeats.length) {
            return;
        }
        HTMLElement stack = chatSeats[seat];
        while (stack.getChildNodes().getLength() >= MAX_BUBBLES) {
            stack.removeChild(stack.getFirstChild());
        }
        HTMLElement bubble = Dom.el("div", cssClass);
        if (name != null) {
            bubble.appendChild(Dom.text("span", "bubble-name", name));
        }
        bubble.appendChild(Dom.text("span", "bubble-text", text));
        stack.appendChild(bubble);
        int ms = Math.min(BUBBLE_MAX_MS, BUBBLE_MIN_MS + BUBBLE_PER_CHAR_MS * text.codePointCount(0, text.length()));
        Browser.after(ms, () -> {
            bubble.setClassName(cssClass + " leaving");
            Browser.after(BUBBLE_FADE_MS, () -> Dom.remove(bubble));
        });
    }

    /** 有不雅字眼就不送，字留在輸入框讓他改。 */
    private void sendChat() {
        String text = chatField.getValue().trim();
        if (chatSender == null || text.isEmpty()) {
            return;
        }
        if (BadWords.contains(text)) {
            showChatNotice(BadWords.CHAT_REJECTED);
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
                scoreCells[seat][0].appendChild(dealerBadge(round.streak()));
            }
            scoreCells[seat][1].setInnerText(player.name());
            String traits = game.traits(seat);
            scoreCells[seat][1].setAttribute("title", traits == null ? player.name() : player.name() + "（" + traits + "）");
            scoreCells[seat][2].setInnerText(String.valueOf(player.chips()));
        }
        canvas.repaint();
    }

    /**
     * 計分表裡莊家的標記：紅色圓圈框住紅字「莊」，連莊時圓圈右邊再寫紅色粗體的連莊次數，照 Swing 版的 DealerBadge。
     * 滑鼠停著會寫「莊家」或「莊家，連N拉N」。
     */
    private static HTMLElement dealerBadge(int streak) {
        HTMLElement badge = Dom.el("span", "dealer-mark");
        badge.appendChild(Dom.text("span", "dealer-badge", "莊"));
        if (streak > 0) {
            badge.appendChild(Dom.text("span", "dealer-streak", String.valueOf(streak)));
        }
        badge.setAttribute("title", streak > 0 ? "莊家，連" + streak + "拉" + streak : "莊家");
        return badge;
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
        canvas.startPick(tiles, first, onPick, false);
        setButtons(actions);
    }

    /** 和 showPick 一樣只有 tiles 裡的手牌能點，這些牌另外加上金框，例如聽牌時打出去會聽牌的牌。 */
    void showMarkedPick(String hintText, Set<Tile> tiles, Consumer<Tile> onPick, List<ActionItem> actions) {
        hint.setInnerText(hintText);
        canvas.startPick(tiles, null, onPick, true);
        setButtons(actions);
    }

    /**
     * 吃碰槓胡這些宣告做得大又醒目，胡與自摸再閃動；過、取消這種淺色的維持原本的大小。
     * 半透明的按鈕（打牌前的聽牌）和宣告一樣大，但不閃，滑鼠移上去才不透明。
     */
    private void setButtons(List<ActionItem> actions) {
        Dom.clear(actionButtons);
        for (ActionItem action : actions) {
            HTMLElement button = Dom.button(action.label(), action.color(), action.run());
            if (action.translucent()) {
                button.setClassName("btn claim-button translucent-button");
            } else if (Theme.RED.equals(action.color())) {
                button.setClassName("btn claim-button win-button");
            } else if (!Theme.PANEL_LIGHT.equals(action.color())) {
                button.setClassName("btn claim-button");
            }
            actionButtons.appendChild(button);
        }
    }
}
