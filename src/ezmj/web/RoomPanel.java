package ezmj.web;

import org.teavm.jso.dom.html.HTMLButtonElement;
import org.teavm.jso.dom.html.HTMLElement;

import ezmj.online.RoomInfo;
import ezmj.online.RoomSettings;

/** 等人開局的房間：房間名稱、房號、房間設定、玩家與連線狀態。房主可以開局，不到四人由電腦補滿。 */
final class RoomPanel {

    private static final int SEATS = 4;
    private static final int LONG_NAME = 14;

    private final HTMLElement root = Dom.el("div", "form-screen");
    private final HTMLElement title = Dom.text("h1", "title", " ");
    private final HTMLElement privateBadge = Dom.text("span", "private-badge", "私人");
    private final HTMLElement expertBadge = Dom.text("span", "expert-badge", "專家模式");
    private final HTMLElement codeLine = Dom.text("div", "code-line", " ");
    private final HTMLElement listedLine = Dom.text("div", "muted", " ");
    private final HTMLElement settingsLine = Dom.text("div", "muted", " ");
    private final HTMLElement players = Dom.el("div", "room-players");
    private final HTMLElement notice = Dom.text("div", "notice", " ");
    private final HTMLButtonElement startButton;
    private String code = "";

    RoomPanel(Runnable onStart, Runnable onLeave) {
        HTMLElement card = Dom.el("div", "card room-card");
        HTMLElement heading = Dom.el("div", "room-heading");
        HTMLElement name = Dom.el("div", "room-name");
        name.appendChild(title);
        name.appendChild(privateBadge);
        // 有哪些不一樣只寫在說明，畫面上只留標籤，和 Swing 版一樣
        expertBadge.setAttribute("title", "專家模式，每次最多等 " + OnlinePanel.EXPERT_MAX_WAIT_SECONDS + " 秒。有哪些不一樣見「說明」");
        name.appendChild(expertBadge);
        heading.appendChild(name);
        heading.appendChild(Dom.button("複製房號", Theme.BUTTON, () -> Browser.copy(code)));
        card.appendChild(heading);
        card.appendChild(codeLine);
        card.appendChild(listedLine);
        card.appendChild(settingsLine);
        card.appendChild(players);
        card.appendChild(notice);
        startButton = Dom.button("開始牌局", Theme.RED, onStart);
        HTMLElement buttons = Dom.el("div", "button-row");
        buttons.appendChild(startButton);
        buttons.appendChild(Dom.button("離開房間", Theme.PANEL_LIGHT, onLeave));
        card.appendChild(buttons);
        root.appendChild(card);
    }

    HTMLElement element() {
        return root;
    }

    /** expert 是房間設定的專家模式，RoomInfo 是 ezmj 的，還沒有這個欄位。 */
    void setRoom(RoomInfo room, boolean expert) {
        code = room.code();
        Dom.visible(expertBadge, expert);
        RoomSettings settings = room.settings();
        // 舊服務器沒有房間名稱，就和以前一樣用房號當標題
        String name = settings.name();
        boolean named = !name.trim().isEmpty();
        title.setInnerText(named ? name : "房號 " + code);
        // 名稱最長 30 字，用原本的大小會把房間撐得比螢幕還寬
        title.setClassName(name.codePointCount(0, name.length()) > LONG_NAME ? "title long" : "title");
        Dom.visible(privateBadge, settings.privateRoom());
        Dom.visible(codeLine, named);
        // 舊服務器也沒有大廳
        Dom.visible(listedLine, named);
        codeLine.setInnerText("房號 " + code);
        listedLine.setInnerText(settings.privateRoom() ? "不列在大廳，把房號告訴朋友就能加入。" : "列在大廳，也可以把房號告訴朋友。");
        settingsLine.setInnerText("籌碼 " + settings.chips() + "　底 " + settings.basePay() + "　每台 " + settings.taiPay()
                + "　每次等待 " + settings.waitSeconds() + " 秒　電腦速度" + settings.speed().label());

        GridTable table = new GridTable(3, 1);
        table.header("座位", "玩家", "狀態");
        for (int seat = 0; seat < SEATS; seat++) {
            addSeat(table, room, seat);
        }
        Dom.clear(players);
        players.appendChild(table.element());

        startButton.setDisabled(!room.isHost());
        startButton.setInnerText(room.isHost() ? "開始牌局" : "等房主開局");
        notice.setInnerText(room.isHost() ? "不到四人時由電腦補滿。" : " ");
    }

    private static void addSeat(GridTable table, RoomInfo room, int seat) {
        RoomInfo.Seat player = room.seats().stream().filter(item -> item.seat() == seat).findFirst().orElse(null);
        if (player == null) {
            // 沒有人坐的座位只有 1、2、3，開局時才由電腦補上
            HTMLElement[] cells = table.row(false, String.valueOf(seat + 1), "（空位）",
                    "開局時由" + room.settings().botLevels().get(seat - 1).label() + "電腦補上");
            for (HTMLElement cell : cells) {
                Dom.color(cell, Theme.MUTED);
            }
            return;
        }
        boolean mine = seat == room.yourSeat();
        String state = (seat == 0 ? "房主・" : "") + (player.connected() ? "連線中" : "斷線");
        HTMLElement[] cells = table.row(mine, String.valueOf(seat + 1), player.name() + (mine ? "（你）" : ""), state);
        if (mine) {
            for (HTMLElement cell : cells) {
                Dom.color(cell, Theme.GOLD);
            }
        }
    }

    /** 連線狀態的提示。 */
    void setNotice(String text) {
        notice.setInnerText(text);
    }
}
