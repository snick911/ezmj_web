package ezmj.web;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.teavm.jso.dom.html.HTMLElement;

import ezmj.engine.GameView;
import ezmj.engine.RoundEvent;
import ezmj.engine.RoundView;
import ezmj.model.Player;
import ezmj.model.Tile;

/** 牌桌主畫面：中間是牌桌，下方是動作列，右側是計分板與紀錄。多人遊戲不能電腦代打，所以沒有那顆按鈕。 */
final class TablePanel {

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
    private final HTMLElement sidebar = Dom.el("div", "sidebar");
    private final HTMLElement shade = Dom.el("div", "drawer-shade");

    TablePanel(Consumer<Tile> onDiscard, Runnable onLeave, Runnable onShowHistory, Runnable onSettings, Runnable onHelp) {
        canvas = new TableCanvas(onDiscard);
        HTMLElement main = Dom.el("div", "table-main");
        main.appendChild(canvas.element());
        HTMLElement actionBar = Dom.el("div", "action-bar");
        HTMLElement menu = Dom.button("選單", Theme.PANEL_LIGHT, () -> showDrawer(true));
        menu.setClassName("btn menu-button");
        actionBar.appendChild(menu);
        if (Browser.canLockLandscape()) {
            HTMLElement rotate = Dom.button("橫向", Theme.PANEL_LIGHT, Browser::lockLandscape);
            rotate.setClassName("btn rotate-button");
            actionBar.appendChild(rotate);
        }
        actionBar.appendChild(hint);
        actionBar.appendChild(actionButtons);
        main.appendChild(actionBar);
        root.appendChild(main);
        shade.addEventListener("click", event -> showDrawer(false));
        root.appendChild(shade);
        buildSidebar(onLeave, onShowHistory, onSettings, onHelp);
        root.appendChild(sidebar);
    }

    /** 手機放不下側欄，要按選單才滑出來。 */
    private void showDrawer(boolean open) {
        sidebar.setClassName(open ? "sidebar open" : "sidebar");
        shade.setClassName(open ? "drawer-shade open" : "drawer-shade");
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
        side.appendChild(logArea);
        side.appendChild(Dom.button("輸贏歷程", Theme.PANEL_LIGHT, onShowHistory));
        HTMLElement row = Dom.el("div", "side-row");
        row.appendChild(Dom.button("環境設定", Theme.PANEL_LIGHT, onSettings));
        row.appendChild(Dom.button("說明", Theme.PANEL_LIGHT, onHelp));
        side.appendChild(row);
        side.appendChild(Dom.button("離開牌局", Theme.PANEL_LIGHT, onLeave));
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

    void bindRound(RoundView round) {
        Dom.clear(logArea);
        canvas.setRound(round);
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
    }

    /** 由目前狀態更新計分板與牌桌。 */
    void refresh(GameView game, RoundView round) {
        String streak = round.streak() > 0 ? " 連" + round.streak() : "";
        roundInfo.setInnerText(Tile.windName(round.roundWind()) + "風圈" + streak);
        stakesInfo.setInnerText("底 " + game.basePay() + " / 每台 " + game.taiPay());
        for (int seat = 0; seat < 4; seat++) {
            Player player = game.player(seat);
            scoreCells[seat][0].setInnerText(Tile.windName(round.seatWind(seat)) + (round.dealer() == seat ? " 莊" : ""));
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

    private void setButtons(List<ActionItem> actions) {
        Dom.clear(actionButtons);
        for (ActionItem action : actions) {
            actionButtons.appendChild(Dom.button(action.label(), action.color(), action.run()));
        }
    }
}
