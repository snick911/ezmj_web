package ezmj.web;

import org.teavm.jso.dom.html.HTMLButtonElement;
import org.teavm.jso.dom.html.HTMLElement;

import ezmj.online.RoomInfo;
import ezmj.online.RoomSettings;

/** 等人開局的房間：房號、房間設定、玩家與連線狀態。房主可以開局，不到四人由電腦補滿。 */
final class RoomPanel {

    private static final int SEATS = 4;

    private final HTMLElement root = Dom.el("div", "form-screen");
    private final HTMLElement title = Dom.text("h1", "title", " ");
    private final HTMLElement settingsLine = Dom.text("div", "muted", " ");
    private final HTMLElement players = Dom.el("div", "room-players");
    private final HTMLElement notice = Dom.text("div", "notice", " ");
    private final HTMLButtonElement startButton;
    private String code = "";

    RoomPanel(Runnable onStart, Runnable onLeave) {
        HTMLElement card = Dom.el("div", "card room-card");
        HTMLElement heading = Dom.el("div", "room-heading");
        heading.appendChild(title);
        heading.appendChild(Dom.button("複製房號", Theme.BUTTON, () -> Browser.copy(code)));
        card.appendChild(heading);
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

    void setRoom(RoomInfo room) {
        code = room.code();
        title.setInnerText("房號 " + code);
        RoomSettings settings = room.settings();
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
