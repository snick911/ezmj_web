package ezmj.web;

import java.util.ArrayList;
import java.util.List;

import org.teavm.jso.dom.events.KeyboardEvent;
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
 * 房間列表佔大部分的畫面；開房間與跟電腦打的設定按了按鈕才跳出來，選好再送出。
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
    private static final String[] COLUMNS = {"房間名稱", "房主", "人數", "籌碼／底／台", "等待秒數"};
    /** 每一欄另外加的樣式，很窄的手機放不下時藏掉等待秒數。 */
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

        /** name 是目前填的名字，榜上同名的會標出來，可能是空的。 */
        void honorRoll(String name);
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
    /** 在線人數與進行中的牌局數，舊服務器沒有就藏起來。 */
    private final HTMLElement activity = Dom.el("div", "lobby-activity");
    private final HTMLElement onlineCount = Dom.text("span", "activity-item online", " ");
    private final HTMLElement playingCount = Dom.text("span", "activity-item playing", " ");
    private final HTMLButtonElement listJoinButton;
    private final HTMLButtonElement soloButton;
    private final HTMLButtonElement createButton;
    private final HTMLButtonElement joinButton;
    private final HTMLButtonElement resumeButton;
    private final HTMLButtonElement cancelButton;
    private final HTMLElement status = Dom.text("div", "status", " ");
    private final HTMLElement stakesHint = Dom.text("div", "notice", " ");
    /** 開房間與跟電腦打的設定，跳出來的視窗每次都放這一份，選過的值留著。 */
    private final HTMLElement setupForm = Dom.el("div", "setup-form");
    private HTMLElement roomNameRow;
    private HTMLElement privateRow;
    private final HTMLButtonElement setupConfirm;
    /** 跳出來的設定視窗，沒有開著就是 null。 */
    private Dialog setupDialog;
    /** 設定視窗是跟電腦打的，不是開房間。 */
    private boolean setupSolo;
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
        soloButton = Dom.button("跟電腦打", Theme.RED, () -> openSetup(true));
        createButton = Dom.button("開房間", Theme.RED, () -> openSetup(false));
        setupConfirm = Dom.button(" ", Theme.RED, this::confirmSetup);
        joinButton = Dom.button("加入房間", Theme.RED, () -> actions.join(name(), code()));
        listJoinButton = Dom.button("加入", Theme.RED, this::joinSelected);
        resumeButton = Dom.button("接回牌局", Theme.BUTTON, () -> actions.resume(profile));
        cancelButton = Dom.button("取消連線", Theme.PANEL_LIGHT, actions::cancel);

        HTMLElement card = Dom.el("div", "card lobby-card");
        card.appendChild(Dom.text("h1", "title", "EZMJ 台灣十六張麻將"));
        card.appendChild(buildTop());
        buildList(card);
        buildBottom(card);
        card.appendChild(footer(version));
        root.appendChild(card);
        buildSetupForm();

        nameField.addEventListener("input", event -> syncButtons());
        codeField.addEventListener("input", event -> syncButtons());
        for (HTMLSelectElement box : List.of(chipBox, baseBox, taiBox)) {
            box.addEventListener("change", event -> syncButtons());
        }
        codeField.addEventListener("keydown", event -> {
            if ("Enter".equals(((KeyboardEvent) event).getKey()) && !joinButton.isDisabled()) {
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

    /** 版號與著作權。完整的授權聲明在說明的「關於」。 */
    private static HTMLElement footer(String version) {
        HTMLElement footer = Dom.el("div", "version");
        footer.appendChild(Dom.text("span", "", "網頁版 " + version + "　" + HelpDialog.COPYRIGHT + "　依 "));
        footer.appendChild(Dom.link("AGPL-3.0", HelpDialog.LICENSE_URL));
        footer.appendChild(Dom.text("span", "", " 授權，不提供任何擔保"));
        return footer;
    }

    /** 最上面一列：名字，旁邊是跟電腦打與開房間。窄的螢幕兩顆按鈕換到名字下面。 */
    private HTMLElement buildTop() {
        HTMLElement top = Dom.el("div", "lobby-top");
        HTMLElement nameRow = addRow(top, "名字（最多 " + MAX_NAME_LENGTH + " 字）", nameField);
        nameRow.setClassName("form-row name-row");
        HTMLElement starts = Dom.el("div", "start-buttons");
        starts.appendChild(soloButton);
        starts.appendChild(createButton);
        top.appendChild(starts);
        return top;
    }

    /** 公開的房間：在線人數、列表與加入。列表把剩下的高度都用掉。 */
    private void buildList(HTMLElement card) {
        HTMLElement listHeading = Dom.el("div", "list-heading");
        listHeading.appendChild(Dom.text("h2", "section", "公開的房間"));
        listHeading.appendChild(Dom.button("重新整理", Theme.BUTTON, this::refresh));
        card.appendChild(listHeading);
        activity.appendChild(onlineCount);
        activity.appendChild(playingCount);
        activity.setAttribute("title", "在線人數包含正在看大廳的人；進行中的牌局也包含私人房間與跟電腦打的");
        Dom.visible(activity, false);
        card.appendChild(activity);
        card.appendChild(roomList);
        HTMLElement listFooter = Dom.el("div", "list-footer");
        listFooter.appendChild(listStatus);
        listFooter.appendChild(listJoinButton);
        card.appendChild(listFooter);
    }

    /** 用房號加入排成一列；接著是接回牌局、連線狀態與其他按鈕。 */
    private void buildBottom(HTMLElement card) {
        HTMLElement joinRow = Dom.el("div", "join-row");
        HTMLElement codeLabel = Dom.el("label", "join-code");
        codeLabel.appendChild(Dom.text("span", "section", "用房號加入"));
        codeField.setAttribute("placeholder", "六碼房號");
        codeField.setAttribute("aria-label", "六碼房號");
        codeLabel.appendChild(codeField);
        joinRow.appendChild(codeLabel);
        joinRow.appendChild(joinButton);
        card.appendChild(joinRow);
        card.appendChild(resumeButton);
        card.appendChild(cancelButton);
        card.appendChild(status);
        HTMLElement row = Dom.el("div", "side-row");
        row.appendChild(Dom.button("榮譽榜", Theme.PANEL_LIGHT, () -> actions.honorRoll(name())));
        row.appendChild(Dom.button("環境設定", Theme.PANEL_LIGHT, actions::settings));
        row.appendChild(Dom.button("說明", Theme.PANEL_LIGHT, actions::help));
        card.appendChild(row);
    }

    /** 跳出來的設定。房間名稱與私人房間只有開房間才有，跟電腦打一定是私人的一人房間。 */
    private void buildSetupForm() {
        roomNameRow = addRow(setupForm, "房間名稱", roomNameField);
        roomNameRow.setAttribute("title", "最多 " + RoomSettings.MAX_NAME_LENGTH + " 字，空白就用預設的名稱");
        privateRow = addRow(setupForm, "私人房間", privateBox);
        privateRow.setAttribute("title", "不列在大廳，只能用房號加入");
        addRow(setupForm, "籌碼本金", chipBox);
        addRow(setupForm, "底", baseBox);
        addRow(setupForm, "每台", taiBox);
        // 三家電腦的等級排在同一列，依序是補位電腦 1、2、3；窄的螢幕放不下時三個選單換到標題下面
        HTMLElement levels = Dom.el("div", "level-boxes");
        for (int i = 0; i < levelBoxes.size(); i++) {
            HTMLSelectElement box = levelBoxes.get(i);
            box.setAttribute("aria-label", "補位電腦 " + (i + 1) + " 的等級");
            box.setAttribute("title", "補位電腦 " + (i + 1));
            levels.appendChild(box);
        }
        HTMLElement levelRow = addRow(setupForm, "補位電腦", levels);
        levelRow.setClassName("form-row level-row");
        levelRow.setAttribute("title", "依序是補位電腦 1、2、3，不到四人開局時由電腦補上");
        addRow(setupForm, "等待秒數（" + RoomSettings.MIN_WAIT_SECONDS + " 到 " + RoomSettings.MAX_WAIT_SECONDS + "）",
                waitField);
        addRow(setupForm, "電腦速度", speedBox);
        setupForm.appendChild(stakesHint);
        // Esc 收起視窗；兩個文字框按 Enter 就送出，輸入法還在選字時不算
        setupForm.addEventListener("keydown", event -> {
            if ("Escape".equals(((KeyboardEvent) event).getKey())) {
                closeSetup();
            }
        });
        for (HTMLInputElement field : List.of(roomNameField, waitField)) {
            field.addEventListener("keydown", event -> {
                KeyboardEvent key = (KeyboardEvent) event;
                if ("Enter".equals(key.getKey()) && !Browser.isComposing(key)) {
                    key.preventDefault();
                    confirmSetup();
                }
            });
        }
    }

    /** 按了跟電腦打或開房間，先跳出設定，選好再送出。 */
    private void openSetup(boolean solo) {
        if (busy || !nameValid() || setupDialog != null) {
            return;
        }
        setupSolo = solo;
        Dom.visible(roomNameRow, !solo);
        Dom.visible(privateRow, !solo);
        setupConfirm.setInnerText(solo ? "開始牌局" : "開房間");
        Dialog dialog = new Dialog(true);
        dialog.card.setClassName("card setup-card");
        dialog.card.appendChild(Dom.text("h2", "headline", solo ? "跟電腦打" : "開房間"));
        dialog.card.appendChild(Dom.text("div", "muted", solo
                ? "一人房間，由服務器補三家電腦，不列在大廳。"
                : "開好之後把房號告訴朋友，或等大廳上的人加入；不到四人開局時由電腦補滿。"));
        dialog.card.appendChild(setupForm);
        HTMLElement buttons = Dom.el("div", "button-row");
        buttons.appendChild(setupConfirm);
        buttons.appendChild(Dom.button("取消", Theme.PANEL_LIGHT, this::closeSetup));
        dialog.card.appendChild(buttons);
        setupDialog = dialog;
        syncButtons();
        dialog.show();
        (solo ? (HTMLElement) chipBox : roomNameField).focus();
    }

    private void closeSetup() {
        if (setupDialog != null) {
            setupDialog.close();
            setupDialog = null;
        }
    }

    /** 跟電腦打的房間一定是私人的，不然打完回到房間時別人會從大廳加進來。 */
    private void confirmSetup() {
        if (setupDialog == null || busy || !nameValid() || !stakesValid()) {
            return;
        }
        boolean solo = setupSolo;
        RoomSettings chosen = settings(solo);
        closeSetup();
        if (solo) {
            actions.playBots(name(), chosen);
        } else {
            actions.create(name(), chosen);
        }
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

    /** 最多 max 個字，用 code point 算。貼上的控制字元直接拿掉，服務器不收。聊天的輸入框也用。 */
    static String limited(String text, int max) {
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
        showActivity(-1, -1);
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
        showActivity(-1, -1);
        listStatus.setInnerText(text);
    }

    @Override
    public void showActivity(int online, int playingRooms) {
        boolean known = online >= 0 && playingRooms >= 0;
        Dom.visible(activity, known);
        if (known) {
            onlineCount.setInnerText("在線 " + online + " 人");
            playingCount.setInnerText(playingRooms + " 桌牌局進行中");
        }
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
        soloButton.setDisabled(!ready);
        createButton.setDisabled(!ready);
        setupConfirm.setDisabled(!ready || !stakesValid);
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
