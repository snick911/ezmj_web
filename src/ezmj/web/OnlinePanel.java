package ezmj.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.teavm.jso.dom.events.KeyboardEvent;
import org.teavm.jso.dom.html.HTMLButtonElement;
import org.teavm.jso.dom.html.HTMLElement;
import org.teavm.jso.dom.html.HTMLInputElement;
import org.teavm.jso.dom.html.HTMLOptionElement;
import org.teavm.jso.dom.html.HTMLSelectElement;

import ezmj.model.BadWords;
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
    private static final String[] COLUMNS = {"房間名稱", "房主", "人數", "籌碼／底／台", "等待秒數", "隨時補位", "專家"};
    /** 每一欄另外加的樣式，很窄的手機放不下時藏掉等待秒數。最後兩欄有開就打勾，置中。 */
    private static final String[] COLUMN_CLASSES = {"name", "host", "", "", "wait", "check", "check"};
    private static final String CHECK = "✓";
    /** 加入牌局中的房間之前先說清楚：要等一下才上場，也不列入榮譽榜。 */
    static final String SUBSTITUTE_CONFIRM = "這間正在打牌。加入會替換一家電腦，等它打完這一手就換你上場；"
            + "這一局先結束的話，下一局開始時上場。\n\n中途加入的不列入榮譽榜。要加入嗎？";
    private static final String LOADING = "正在讀取房間列表，服務器閒置後第一次可能要等一分鐘…";
    private static final String STAKES_TOO_HIGH = "底和每台不能大於籌碼本金。";
    /**
     * 專家模式每次最多等幾秒。和 Swing 版的 GameConfig.EXPERT_MAX_WAIT_SECONDS、服務器的 RoomOptions 一樣，
     * 服務器也會把比較長的改成這個。
     */
    static final int EXPERT_MAX_WAIT_SECONDS = 10;

    /** 按鈕送出的要求。name 檢查過了。 */
    interface Actions {
        /** expert 是專家模式，房間裡每個人都照這個打；substitute 是隨時補位，牌局中別人可以加入替換電腦。 */
        void create(String name, RoomSettings settings, boolean expert, boolean substitute);

        void playBots(String name, RoomSettings settings, boolean expert);

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
    private final HTMLInputElement expertBox = checkbox();
    private final HTMLInputElement substituteBox = checkbox();
    private final HTMLSelectElement chipBox = numberSelect(CHIP_OPTIONS, 20_000);
    private final HTMLSelectElement baseBox = numberSelect(BASE_OPTIONS, 100);
    private final HTMLSelectElement taiBox = numberSelect(TAI_OPTIONS, 20);
    private final List<HTMLSelectElement> levelBoxes = new ArrayList<>();
    private final HTMLInputElement waitField = numberInput();
    /** 等待秒數的欄名，寫著現在能填的範圍，勾了專家模式就變小。 */
    private final HTMLElement waitLabel = Dom.text("span", "", " ");
    /** 勾專家模式時填的秒數比上限長，就記在這裡，取消勾選時還原；沒有就是 null。 */
    private String waitBeforeExpert;
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
    private HTMLElement substituteRow;
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
    /** 列表上每間房間的專家模式、隨時補位與牌局中，鍵是房號。 */
    private Map<String, LobbyBrowser.RoomFlags> roomFlags = Map.of();
    private WebProfile profile = new WebProfile("", null, null, false);
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
        joinButton = Dom.button("加入房間", Theme.RED, this::joinByCode);
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
            KeyboardEvent key = (KeyboardEvent) event;
            if ("Enter".equals(key.getKey()) && !Browser.isComposing(key)) {
                key.preventDefault();
                joinByCode();
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

    /**
     * 跳出來的設定。房間名稱與私人房間只有開房間才有，跟電腦打一定是私人的一人房間。
     * 房間名稱拉長到和三家電腦的等級一樣寬；三個勾選框只有框，對齊在同一條線，點標題也能勾，
     * 怎麼補位、專家模式有哪些不一樣都寫在說明，滑鼠停著也看得到。
     */
    private void buildSetupForm() {
        roomNameRow = addRow(setupForm, "房間名稱", roomNameField);
        roomNameRow.setClassName("form-row wide-row");
        roomNameRow.setAttribute("title", "最多 " + RoomSettings.MAX_NAME_LENGTH + " 字，空白就用預設的名稱");
        privateRow = checkRow("私人房間", privateBox, "不列在大廳，只能用房號加入");
        substituteRow = checkRow("隨時補位", substituteBox,
                "開打之後，電腦在打的座位別人也能中途加入替換，中途加入的不列入榮譽榜。詳見「說明」");
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
        HTMLElement waitRow = Dom.el("label", "form-row");
        waitRow.appendChild(waitLabel);
        waitRow.appendChild(waitField);
        setupForm.appendChild(waitRow);
        addRow(setupForm, "電腦速度", speedBox);
        // 跟電腦打與開房間都有，開房間的話房間裡每個人都是
        checkRow("專家模式", expertBox, "每次最多等 " + EXPERT_MAX_WAIT_SECONDS
                + " 秒；開房間的話房間裡每個人都照專家模式打。有哪些不一樣見「說明」");
        expertBox.addEventListener("change", event -> syncWaitLimit());
        syncWaitLimit();
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

    /** 按了跟電腦打或開房間，先檢查名字，再跳出設定，選好再送出。 */
    private void openSetup(boolean solo) {
        if (busy || setupDialog != null || !nameAccepted()) {
            return;
        }
        setupSolo = solo;
        Dom.visible(roomNameRow, !solo);
        Dom.visible(privateRow, !solo);
        Dom.visible(substituteRow, !solo);
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

    /**
     * 跟電腦打的房間一定是私人的，不然打完回到房間時別人會從大廳加進來。
     * 按鈕一直可以按，房間名稱有不雅字眼或底台太大，按下去才跳出提醒。
     */
    private void confirmSetup() {
        if (setupDialog == null || busy || !nameAccepted()) {
            return;
        }
        if (!setupSolo && BadWords.contains(roomNameField.getValue())) {
            Dialog.message(BadWords.ROOM_NAME_REJECTED, () -> roomNameField.focus());
            return;
        }
        if (!stakesValid()) {
            Dialog.message(STAKES_TOO_HIGH, () -> chipBox.focus());
            return;
        }
        if (expertBox.isChecked() && typedWaitSeconds() > EXPERT_MAX_WAIT_SECONDS) {
            waitField.setValue(String.valueOf(EXPERT_MAX_WAIT_SECONDS));
            Dialog.message("專家模式每次最多等 " + EXPERT_MAX_WAIT_SECONDS + " 秒，等待秒數改成 " + EXPERT_MAX_WAIT_SECONDS
                    + " 秒了，確定之後再按一次。", () -> waitField.focus());
            return;
        }
        boolean solo = setupSolo;
        RoomSettings chosen = settings(solo);
        closeSetup();
        if (solo) {
            actions.playBots(name(), chosen, expertBox.isChecked());
        } else {
            actions.create(name(), chosen, expertBox.isChecked(), substituteBox.isChecked());
        }
    }

    /** 勾選框的一列：整列是 label，點標題也能勾。框放在選項欄的左邊，三個勾選框對齊。 */
    private HTMLElement checkRow(String label, HTMLInputElement box, String title) {
        HTMLElement row = Dom.el("label", "form-row check-row");
        row.setAttribute("title", title);
        row.appendChild(Dom.text("span", "", label));
        HTMLElement choice = Dom.el("span", "check-choice");
        choice.appendChild(box);
        row.appendChild(choice);
        setupForm.appendChild(row);
        return row;
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
        updateListStatus();
        syncButtons();
    }

    /** 等人的與可以補位的分開算。牌局中的要等 showRoomFlags 才知道，所以兩邊都會呼叫。 */
    private void updateListStatus() {
        if (rooms.isEmpty()) {
            listStatus.setInnerText("目前沒有等人的公開房間，可以自己開一間。");
            return;
        }
        long playing = rooms.stream().filter(room -> flags(room.code()).playing()).count();
        long waiting = rooms.size() - playing;
        String counts = playing == 0 ? waiting + " 間等人中"
                : waiting == 0 ? playing + " 間牌局可以補位"
                : waiting + " 間等人中、" + playing + " 間牌局可以補位";
        listStatus.setInnerText(counts + "，選一間按「加入」或連點兩下。");
    }

    private LobbyBrowser.RoomFlags flags(String code) {
        LobbyBrowser.RoomFlags flags = roomFlags.get(code);
        return flags != null ? flags : new LobbyBrowser.RoomFlags(false, false, false);
    }

    @Override
    public void showListProblem(String text) {
        showRooms(List.of());
        showActivity(-1, -1);
        listStatus.setInnerText(text);
    }

    /** 和上次一樣就不重畫，理由同 showRooms。 */
    @Override
    public void showRoomFlags(Map<String, LobbyBrowser.RoomFlags> flags) {
        if (!flags.equals(roomFlags)) {
            roomFlags = flags;
            renderRooms();
            updateListStatus();
        }
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

    /**
     * 房間名稱與房主的名字來自網路，GridTable 一律用 innerText。滑鼠停著看得到完整的名稱與電腦速度。
     * 隨時補位與專家模式各一欄，有開就打勾；牌局中、可以補位的房間在名稱後面標「牌局中」。
     */
    private void renderRooms() {
        GridTable table = new GridTable(2);
        HTMLElement[] headers = table.header(COLUMNS);
        markColumns(headers);
        headers[5].setAttribute("title", "開打之後，電腦在打的座位也能中途加入替換");
        headers[6].setAttribute("title", "專家模式");
        roomRows.clear();
        for (RoomListing room : rooms) {
            RoomSettings settings = room.settings();
            LobbyBrowser.RoomFlags flags = flags(room.code());
            HTMLElement[] cells = table.row(false, room.name(), room.host(), room.humans() + "/" + SEATS,
                    settings.chips() + "/" + settings.basePay() + "/" + settings.taiPay(), settings.waitSeconds() + " 秒",
                    flags.substitute() ? CHECK : "", flags.expert() ? CHECK : "");
            markColumns(cells);
            if (flags.playing()) {
                cells[0].appendChild(Dom.text("span", "playing-badge", "牌局中"));
            }
            HTMLElement row = (HTMLElement) cells[0].getParentNode();
            row.setAttribute("title", room.name() + "　房主 " + room.host() + "　電腦速度" + settings.speed().label()
                    + (flags.substitute() ? "　隨時補位" : "") + (flags.expert() ? "　專家模式" : "")
                    + (flags.playing() ? "　牌局中，加入就替換一家電腦" : ""));
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
        if (busy) {
            return;
        }
        if (selectedCode == null) {
            Dialog.message("請先在列表選一間房間。");
            return;
        }
        if (!nameAccepted()) {
            return;
        }
        String code = selectedCode;
        if (flags(code).playing()) {
            Dialog.confirm(SUBSTITUTE_CONFIRM, () -> {
                if (!busy) {
                    actions.join(name(), code);
                }
            });
        } else {
            actions.join(name(), code);
        }
    }

    private void joinByCode() {
        if (busy || !nameAccepted()) {
            return;
        }
        if (code().length() != ROOM_CODE_LENGTH) {
            Dialog.message("請輸入六碼房號。", () -> codeField.focus());
            return;
        }
        actions.join(name(), code());
    }

    /**
     * 名字沒填、太長或有不雅字眼，就跳出提醒，按了確定把游標放回名字欄，回傳 false。
     * 開桌與加入的按鈕不因為名字變灰，按下去才用這個檢查。
     */
    private boolean nameAccepted() {
        String name = name();
        String problem = name.isEmpty() ? "請先輸入名字。"
                : !nameValid() ? "名字最多 " + MAX_NAME_LENGTH + " 字。"
                : BadWords.contains(name) ? BadWords.NAME_REJECTED : null;
        if (problem == null) {
            return true;
        }
        Dialog.message(problem, () -> nameField.focus());
        return false;
    }

    /** 只有正在連線時按鈕不能按；名字、房號與底台按下去才檢查，底台太大先在設定視窗裡寫出來。 */
    private void syncButtons() {
        // 服務器也會檢查，這裡先寫出來，不用連上去才知道
        boolean stakesValid = stakesValid();
        stakesHint.setInnerText(stakesValid ? " " : STAKES_TOO_HIGH);
        Dom.visible(stakesHint, !stakesValid);
        soloButton.setDisabled(busy);
        createButton.setDisabled(busy);
        setupConfirm.setDisabled(busy);
        joinButton.setDisabled(busy);
        listJoinButton.setDisabled(busy);
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

    /** 亂填就回到預設值，超出範圍就夾回去，服務器也會再檢查。專家模式最多 EXPERT_MAX_WAIT_SECONDS 秒。 */
    private int waitSeconds() {
        return Math.max(RoomSettings.MIN_WAIT_SECONDS, Math.min(maxWaitSeconds(), typedWaitSeconds()));
    }

    /** 欄位裡填的秒數，還沒夾進範圍；亂填就是預設值。 */
    private int typedWaitSeconds() {
        try {
            return Integer.parseInt(waitField.getValue().trim());
        } catch (NumberFormatException e) {
            return DEFAULT_WAIT_SECONDS;
        }
    }

    private int maxWaitSeconds() {
        return expertBox.isChecked() ? EXPERT_MAX_WAIT_SECONDS : RoomSettings.MAX_WAIT_SECONDS;
    }

    /**
     * 勾了專家模式就把等待秒數的範圍改小，填的比上限長就改成上限，原本的記著；
     * 取消勾選時，秒數沒動過就還原成原本填的。
     */
    private void syncWaitLimit() {
        int max = maxWaitSeconds();
        waitLabel.setInnerText("等待秒數（" + RoomSettings.MIN_WAIT_SECONDS + " 到 " + max + "）");
        waitField.setAttribute("max", String.valueOf(max));
        if (expertBox.isChecked()) {
            if (typedWaitSeconds() > max) {
                waitBeforeExpert = waitField.getValue();
                waitField.setValue(String.valueOf(max));
            }
        } else if (waitBeforeExpert != null) {
            if (typedWaitSeconds() == EXPERT_MAX_WAIT_SECONDS) {
                waitField.setValue(waitBeforeExpert);
            }
            waitBeforeExpert = null;
        }
    }
}
