package ezmj.web;

import java.util.ArrayList;
import java.util.List;

import org.teavm.jso.dom.html.HTMLButtonElement;
import org.teavm.jso.dom.html.HTMLElement;
import org.teavm.jso.dom.html.HTMLInputElement;
import org.teavm.jso.dom.html.HTMLOptionElement;
import org.teavm.jso.dom.html.HTMLSelectElement;

import ezmj.model.BotLevel;
import ezmj.model.GameSpeed;
import ezmj.online.RoomListing;
import ezmj.online.RoomSettings;

/**
 * 網頁的入口，也就是大廳：輸入名字，從公開的房間挑一間加入、用房號加入（私人房間要用）、開房間或跟電腦打；
 * 牌局中斷過的話也能接回去。服務器是固定的。看得到這個畫面時才去要房間列表。
 */
final class OnlinePanel implements LobbyBrowser.View {

    private static final int MAX_NAME_LENGTH = 16;
    private static final int ROOM_CODE_LENGTH = 6;
    private static final int DEFAULT_WAIT_SECONDS = 30;
    private static final int SEATS = 4;
    private static final int[] CHIP_OPTIONS = {10_000, 20_000, 50_000, 100_000, 500_000, 1_000_000, 5_000_000, 10_000_000};
    private static final int[] BASE_OPTIONS = {50, 100, 300, 500, 1_000, 5_000, 10_000, 50_000, 100_000, 500_000, 1_000_000};
    private static final int[] TAI_OPTIONS = {10, 20, 50, 100, 200, 5_000, 10_000, 50_000, 100_000, 200_000, 500_000,
            1_000_000};
    private static final String[] COLUMNS = {"房間名稱", "房主", "人數", "籌碼／底／台", "等待"};
    /** 每一欄另外加的樣式，手機放不下時藏掉等待秒數。 */
    private static final String[] COLUMN_CLASSES = {"name", "host", "", "", "wait"};
    private static final String LOADING = "正在讀取房間列表，服務器閒置後第一次可能要等一分鐘…";

    /** 按鈕送出的要求。name 檢查過了。 */
    interface Actions {
        void create(String name, RoomSettings settings);

        void playBots(String name, RoomSettings settings);

        void join(String name, String code);

        void resume(WebProfile profile);

        void cancel();

        void settings();

        void help();
    }

    private final Actions actions;
    private final HTMLElement root = Dom.el("div", "form-screen");
    private final HTMLInputElement nameField = input(MAX_NAME_LENGTH);
    private final HTMLInputElement codeField = input(ROOM_CODE_LENGTH);
    private final HTMLInputElement roomNameField = input(RoomSettings.MAX_NAME_LENGTH);
    private final HTMLInputElement privateBox = checkbox();
    private final HTMLSelectElement chipBox = numberSelect(CHIP_OPTIONS, 20_000);
    private final HTMLSelectElement baseBox = numberSelect(BASE_OPTIONS, 100);
    private final HTMLSelectElement taiBox = numberSelect(TAI_OPTIONS, 20);
    private final List<HTMLSelectElement> levelBoxes = new ArrayList<>();
    private final HTMLInputElement waitField = numberInput();
    private final HTMLSelectElement speedBox = speedSelect();
    private final HTMLElement roomList = Dom.el("div", "room-list");
    private final HTMLElement listStatus = Dom.text("div", "muted", LOADING);
    private final HTMLButtonElement listJoinButton;
    private final HTMLButtonElement soloButton;
    private final HTMLButtonElement createButton;
    private final HTMLButtonElement joinButton;
    private final HTMLButtonElement resumeButton;
    private final HTMLButtonElement cancelButton;
    private final HTMLElement status = Dom.text("div", "status", " ");
    private final HTMLElement stakesHint = Dom.text("div", "notice", " ");
    private final LobbyBrowser lobby;
    private List<RoomListing> rooms = List.of();
    /** 和 rooms 同樣的順序。 */
    private final List<HTMLElement> roomRows = new ArrayList<>();
    private String selectedCode;
    private WebProfile profile = new WebProfile("", null, null);
    private boolean busy;

    OnlinePanel(Actions actions, String version, String serverUrl) {
        this.actions = actions;
        lobby = new LobbyBrowser(serverUrl, this);
        for (int i = 0; i < 3; i++) {
            levelBoxes.add(levelSelect());
        }
        // 跟電腦打的房間一定是私人的，不然打完回到房間時別人會從大廳加進來
        soloButton = Dom.button("跟電腦打", Theme.RED, () -> actions.playBots(name(), settings(true)));
        createButton = Dom.button("開房間", Theme.RED, () -> actions.create(name(), settings(false)));
        joinButton = Dom.button("加入房間", Theme.RED, () -> actions.join(name(), code()));
        listJoinButton = Dom.button("加入", Theme.RED, this::joinSelected);
        resumeButton = Dom.button("接回牌局", Theme.BUTTON, () -> actions.resume(profile));
        cancelButton = Dom.button("取消連線", Theme.PANEL_LIGHT, actions::cancel);

        HTMLElement card = Dom.el("div", "card form-card");
        card.appendChild(Dom.text("h1", "title", "EZMJ 台灣十六張麻將"));
        HTMLElement columns = Dom.el("div", "form-columns");
        columns.appendChild(buildLeft());
        columns.appendChild(buildRight());
        card.appendChild(columns);
        card.appendChild(Dom.text("div", "version", "網頁版 " + version));
        root.appendChild(card);

        nameField.addEventListener("input", event -> syncButtons());
        codeField.addEventListener("input", event -> syncButtons());
        for (HTMLSelectElement box : List.of(chipBox, baseBox, taiBox)) {
            box.addEventListener("change", event -> syncButtons());
        }
        codeField.addEventListener("keydown", event -> {
            if ("Enter".equals(((org.teavm.jso.dom.events.KeyboardEvent) event).getKey()) && !joinButton.isDisabled()) {
                joinButton.click();
            }
        });
        roomNameField.addEventListener("input", event -> {
            String limited = limited(roomNameField.getValue(), RoomSettings.MAX_NAME_LENGTH);
            if (!limited.equals(roomNameField.getValue())) {
                roomNameField.setValue(limited);
            }
        });
        renderRooms();
        syncButtons();
    }

    HTMLElement element() {
        return root;
    }

    private HTMLElement buildLeft() {
        HTMLElement left = Dom.el("div", "form-column lobby-column");
        addRow(left, "名字（最多 " + MAX_NAME_LENGTH + " 字）", nameField);
        left.appendChild(soloButton);
        HTMLElement listHeading = Dom.el("div", "list-heading");
        listHeading.appendChild(Dom.text("h2", "section", "公開的房間"));
        listHeading.appendChild(Dom.button("重新整理", Theme.BUTTON, this::refresh));
        left.appendChild(listHeading);
        left.appendChild(roomList);
        HTMLElement listFooter = Dom.el("div", "list-footer");
        listFooter.appendChild(listStatus);
        listFooter.appendChild(listJoinButton);
        left.appendChild(listFooter);
        left.appendChild(Dom.text("h2", "section", "用房號加入"));
        addRow(left, "六碼房號", codeField);
        left.appendChild(joinButton);
        left.appendChild(resumeButton);
        left.appendChild(cancelButton);
        left.appendChild(status);
        HTMLElement row = Dom.el("div", "side-row");
        row.appendChild(Dom.button("環境設定", Theme.PANEL_LIGHT, actions::settings));
        row.appendChild(Dom.button("說明", Theme.PANEL_LIGHT, actions::help));
        left.appendChild(row);
        return left;
    }

    private HTMLElement buildRight() {
        HTMLElement right = Dom.el("div", "form-column");
        right.appendChild(Dom.text("h2", "section", "或開一間新房間"));
        addRow(right, "房間名稱", roomNameField).setAttribute("title",
                "最多 " + RoomSettings.MAX_NAME_LENGTH + " 字，空白就用預設的名稱");
        addRow(right, "私人房間", privateBox).setAttribute("title", "不列在大廳，只能用房號加入");
        addRow(right, "籌碼本金", chipBox);
        addRow(right, "底", baseBox);
        addRow(right, "每台", taiBox);
        for (int i = 0; i < levelBoxes.size(); i++) {
            addRow(right, "補位電腦 " + (i + 1) + " 的等級", levelBoxes.get(i));
        }
        addRow(right, "等待秒數（" + RoomSettings.MIN_WAIT_SECONDS + " 到 " + RoomSettings.MAX_WAIT_SECONDS + "）", waitField);
        addRow(right, "電腦速度", speedBox);
        right.appendChild(stakesHint);
        right.appendChild(createButton);
        return right;
    }

    private static HTMLElement addRow(HTMLElement parent, String label, HTMLElement field) {
        HTMLElement row = Dom.el("label", "form-row");
        row.appendChild(Dom.text("span", "", label));
        row.appendChild(field);
        parent.appendChild(row);
        return row;
    }

    private static HTMLInputElement input(int maxLength) {
        HTMLInputElement input = (HTMLInputElement) Dom.el("input", "field");
        input.setType("text");
        input.setMaxLength(maxLength * 2);
        input.setAttribute("autocomplete", "off");
        return input;
    }

    private static HTMLInputElement checkbox() {
        HTMLInputElement check = (HTMLInputElement) Dom.el("input", "check");
        check.setType("checkbox");
        return check;
    }

    private static HTMLInputElement numberInput() {
        HTMLInputElement input = (HTMLInputElement) Dom.el("input", "field");
        input.setType("number");
        input.setAttribute("min", String.valueOf(RoomSettings.MIN_WAIT_SECONDS));
        input.setAttribute("max", String.valueOf(RoomSettings.MAX_WAIT_SECONDS));
        input.setAttribute("step", "5");
        input.setValue(String.valueOf(DEFAULT_WAIT_SECONDS));
        return input;
    }

    private static void addOption(HTMLSelectElement select, String value, String label, boolean selected) {
        HTMLOptionElement option = (HTMLOptionElement) Dom.text("option", "", label);
        option.setValue(value);
        option.setSelected(selected);
        select.appendChild(option);
    }

    private static HTMLSelectElement numberSelect(int[] options, int selected) {
        HTMLSelectElement select = (HTMLSelectElement) Dom.el("select", "field");
        for (int option : options) {
            addOption(select, String.valueOf(option), String.valueOf(option), option == selected);
        }
        return select;
    }

    private static HTMLSelectElement levelSelect() {
        HTMLSelectElement select = (HTMLSelectElement) Dom.el("select", "field");
        for (BotLevel level : BotLevel.values()) {
            addOption(select, level.name(), level.label(), level == BotLevel.NORMAL);
        }
        return select;
    }

    private static HTMLSelectElement speedSelect() {
        HTMLSelectElement select = (HTMLSelectElement) Dom.el("select", "field");
        for (GameSpeed speed : GameSpeed.values()) {
            addOption(select, speed.name(), speed.label(), speed == GameSpeed.NORMAL);
        }
        return select;
    }

    /** 最多 max 個字，用 code point 算。貼上的控制字元直接拿掉，服務器不收。 */
    private static String limited(String text, int max) {
        StringBuilder kept = new StringBuilder();
        int count = 0;
        for (int i = 0; i < text.length() && count < max; ) {
            int point = text.codePointAt(i);
            i += Character.charCount(point);
            if (!Character.isISOControl(point)) {
                kept.appendCodePoint(point);
                count++;
            }
        }
        return kept.toString();
    }

    /** 回到這個畫面時用存的資料帶入，上次打到一半的話可以接回。 */
    void setProfile(WebProfile saved) {
        profile = saved;
        if (nameField.getValue().trim().isEmpty()) {
            nameField.setValue(saved.name());
        }
        resumeButton.setInnerText(saved.canResume() ? "接回牌局（房號 " + saved.room() + "）" : "接回牌局");
        setBusy(null);
    }

    /** text 不是 null 時表示正在連線，顯示這段文字，只能按取消。列表照樣更新。 */
    void setBusy(String text) {
        busy = text != null;
        status.setInnerText(busy ? text : " ");
        syncButtons();
    }

    boolean isBusy() {
        return busy;
    }

    /** 換到這個畫面時開始更新列表。 */
    void startLobby() {
        refresh();
    }

    /** 換到房間或牌桌就關掉那條連線，下次回來從頭讀。 */
    void stopLobby() {
        lobby.stop();
        showRooms(List.of());
        listStatus.setInnerText(LOADING);
    }

    private void refresh() {
        if (rooms.isEmpty()) {
            listStatus.setInnerText(LOADING);
        }
        lobby.refresh();
    }

    @Override
    public void showRooms(List<RoomListing> list) {
        // 一樣的列表不重畫，滑鼠停著、選取與連點兩下才不會每 5 秒被打斷
        if (!list.equals(rooms)) {
            rooms = list;
            if (rooms.stream().noneMatch(room -> room.code().equals(selectedCode))) {
                selectedCode = null;
            }
            renderRooms();
        }
        listStatus.setInnerText(list.isEmpty() ? "目前沒有等人的公開房間，可以自己開一間。"
                : list.size() + " 間等人中，選一間按「加入」或連點兩下。");
        syncButtons();
    }

    @Override
    public void showListProblem(String text) {
        showRooms(List.of());
        listStatus.setInnerText(text);
    }

    /** 房間名稱與房主的名字來自網路，GridTable 一律用 innerText。滑鼠停著看得到完整的名稱與電腦速度。 */
    private void renderRooms() {
        GridTable table = new GridTable(2);
        markColumns(table.header(COLUMNS));
        roomRows.clear();
        for (RoomListing room : rooms) {
            RoomSettings settings = room.settings();
            HTMLElement[] cells = table.row(false, room.name(), room.host(), room.humans() + "/" + SEATS,
                    settings.chips() + "/" + settings.basePay() + "/" + settings.taiPay(), settings.waitSeconds() + " 秒");
            markColumns(cells);
            HTMLElement row = (HTMLElement) cells[0].getParentNode();
            row.setAttribute("title", room.name() + "　房主 " + room.host() + "　電腦速度" + settings.speed().label());
            row.addEventListener("click", event -> select(room.code()));
            row.addEventListener("dblclick", event -> {
                select(room.code());
                joinSelected();
            });
            roomRows.add(row);
        }
        select(selectedCode);
        Dom.clear(roomList);
        roomList.appendChild(table.element());
    }

    private static void markColumns(HTMLElement[] cells) {
        for (int column = 0; column < cells.length; column++) {
            if (!COLUMN_CLASSES[column].isEmpty()) {
                cells[column].setClassName(cells[column].getClassName() + " " + COLUMN_CLASSES[column]);
            }
        }
    }

    /** 只換樣式，不重畫：重畫的話連點兩下的第二下就點到新的一列，瀏覽器不算連點。 */
    private void select(String code) {
        selectedCode = code;
        for (int i = 0; i < rooms.size(); i++) {
            roomRows.get(i).setClassName(rooms.get(i).code().equals(code) ? "selected" : "");
        }
        syncButtons();
    }

    private void joinSelected() {
        if (selectedCode == null || busy) {
            return;
        }
        if (!nameValid()) {
            status.setInnerText("先輸入名字再加入。");
            nameField.focus();
            return;
        }
        actions.join(name(), selectedCode);
    }

    private void syncButtons() {
        boolean ready = !busy && nameValid();
        if (ready) {
            // 收掉「先輸入名字」的提示
            status.setInnerText(" ");
        }
        // 服務器也會檢查，這裡先擋，不用連上去才知道
        boolean stakesValid = stakesValid();
        stakesHint.setInnerText(stakesValid ? " " : "底和每台不能大於籌碼本金。");
        Dom.visible(stakesHint, !stakesValid);
        soloButton.setDisabled(!ready || !stakesValid);
        createButton.setDisabled(!ready || !stakesValid);
        joinButton.setDisabled(!ready || code().length() != ROOM_CODE_LENGTH);
        listJoinButton.setDisabled(!ready || selectedCode == null);
        resumeButton.setDisabled(busy);
        Dom.visible(resumeButton, profile.canResume());
        Dom.visible(cancelButton, busy);
        // 不填房間名稱時服務器用這個
        roomNameField.setAttribute("placeholder", (nameValid() ? name() : "你的名字") + "的房間");
    }

    /** 底和每台都不能大於籌碼本金，等於可以。 */
    private boolean stakesValid() {
        int chips = Integer.parseInt(chipBox.getValue());
        return Integer.parseInt(baseBox.getValue()) <= chips && Integer.parseInt(taiBox.getValue()) <= chips;
    }

    private boolean nameValid() {
        String name = name();
        int length = name.codePointCount(0, name.length());
        return length > 0 && length <= MAX_NAME_LENGTH;
    }

    private String name() {
        return nameField.getValue().trim();
    }

    private String code() {
        return codeField.getValue().trim().toUpperCase();
    }

    /** solo 是跟電腦打：一定是私人房間，名稱用預設的。 */
    private RoomSettings settings(boolean solo) {
        List<BotLevel> levels = new ArrayList<>();
        for (HTMLSelectElement box : levelBoxes) {
            levels.add(BotLevel.valueOf(box.getValue()));
        }
        return new RoomSettings(Integer.parseInt(chipBox.getValue()), Integer.parseInt(baseBox.getValue()),
                Integer.parseInt(taiBox.getValue()), levels, waitSeconds(), GameSpeed.valueOf(speedBox.getValue()),
                solo ? "" : roomNameField.getValue().trim(), solo || privateBox.isChecked());
    }

    /** 亂填就回到預設值，超出範圍就夾回去，服務器也會再檢查。 */
    private int waitSeconds() {
        int seconds;
        try {
            seconds = Integer.parseInt(waitField.getValue().trim());
        } catch (NumberFormatException e) {
            seconds = DEFAULT_WAIT_SECONDS;
        }
        return Math.max(RoomSettings.MIN_WAIT_SECONDS, Math.min(RoomSettings.MAX_WAIT_SECONDS, seconds));
    }
}
