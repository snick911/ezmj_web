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
import ezmj.online.RoomSettings;

/** 網頁的入口：輸入名字，開房間、用房號加入或跟電腦打；牌局中斷過的話也能接回去。服務器是固定的。 */
final class OnlinePanel {

    private static final int MAX_NAME_LENGTH = 16;
    private static final int ROOM_CODE_LENGTH = 6;
    private static final int DEFAULT_WAIT_SECONDS = 30;
    private static final int[] CHIP_OPTIONS = {10_000, 20_000, 50_000, 100_000, 500_000};
    private static final int[] BASE_OPTIONS = {50, 100, 300, 500, 1_000};
    private static final int[] TAI_OPTIONS = {10, 20, 50, 100, 200};

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

    private final HTMLElement root = Dom.el("div", "form-screen");
    private final HTMLInputElement nameField = input(MAX_NAME_LENGTH);
    private final HTMLInputElement codeField = input(ROOM_CODE_LENGTH);
    private final HTMLSelectElement chipBox = numberSelect(CHIP_OPTIONS, 20_000);
    private final HTMLSelectElement baseBox = numberSelect(BASE_OPTIONS, 100);
    private final HTMLSelectElement taiBox = numberSelect(TAI_OPTIONS, 20);
    private final List<HTMLSelectElement> levelBoxes = new ArrayList<>();
    private final HTMLInputElement waitField = numberInput();
    private final HTMLSelectElement speedBox = speedSelect();
    private final HTMLButtonElement soloButton;
    private final HTMLButtonElement createButton;
    private final HTMLButtonElement joinButton;
    private final HTMLButtonElement resumeButton;
    private final HTMLButtonElement cancelButton;
    private final HTMLElement status = Dom.text("div", "status", " ");
    private WebProfile profile = new WebProfile("", null, null);
    private boolean busy;

    OnlinePanel(Actions actions, String version) {
        for (int i = 0; i < 3; i++) {
            levelBoxes.add(levelSelect());
        }
        soloButton = Dom.button("跟電腦打", Theme.RED, () -> actions.playBots(name(), settings()));
        createButton = Dom.button("開房間", Theme.RED, () -> actions.create(name(), settings()));
        joinButton = Dom.button("加入房間", Theme.RED, () -> actions.join(name(), code()));
        resumeButton = Dom.button("接回牌局", Theme.BUTTON, () -> actions.resume(profile));
        cancelButton = Dom.button("取消連線", Theme.PANEL_LIGHT, actions::cancel);

        HTMLElement card = Dom.el("div", "card form-card");
        card.appendChild(Dom.text("h1", "title", "EZMJ 台灣十六張麻將"));
        HTMLElement columns = Dom.el("div", "form-columns");
        columns.appendChild(buildLeft(actions));
        columns.appendChild(buildRight());
        card.appendChild(columns);
        card.appendChild(Dom.text("div", "version", "網頁版 " + version));
        root.appendChild(card);

        nameField.addEventListener("input", event -> syncButtons());
        codeField.addEventListener("input", event -> syncButtons());
        codeField.addEventListener("keydown", event -> {
            if ("Enter".equals(((org.teavm.jso.dom.events.KeyboardEvent) event).getKey()) && !joinButton.isDisabled()) {
                joinButton.click();
            }
        });
        syncButtons();
    }

    HTMLElement element() {
        return root;
    }

    private HTMLElement buildLeft(Actions actions) {
        HTMLElement left = Dom.el("div", "form-column");
        addRow(left, "名字（最多 " + MAX_NAME_LENGTH + " 字）", nameField);
        left.appendChild(soloButton);
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
        addRow(right, "籌碼本金", chipBox);
        addRow(right, "底", baseBox);
        addRow(right, "每台", taiBox);
        for (int i = 0; i < levelBoxes.size(); i++) {
            addRow(right, "補位電腦 " + (i + 1) + " 的等級", levelBoxes.get(i));
        }
        addRow(right, "等待秒數（" + RoomSettings.MIN_WAIT_SECONDS + " 到 " + RoomSettings.MAX_WAIT_SECONDS + "）", waitField);
        addRow(right, "電腦速度", speedBox);
        right.appendChild(createButton);
        return right;
    }

    private static void addRow(HTMLElement parent, String label, HTMLElement field) {
        HTMLElement row = Dom.el("label", "form-row");
        row.appendChild(Dom.text("span", "", label));
        row.appendChild(field);
        parent.appendChild(row);
    }

    private static HTMLInputElement input(int maxLength) {
        HTMLInputElement input = (HTMLInputElement) Dom.el("input", "field");
        input.setType("text");
        input.setMaxLength(maxLength * 2);
        input.setAttribute("autocomplete", "off");
        return input;
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

    /** 回到這個畫面時用存的資料帶入，上次打到一半的話可以接回。 */
    void setProfile(WebProfile saved) {
        profile = saved;
        if (nameField.getValue().trim().isEmpty()) {
            nameField.setValue(saved.name());
        }
        resumeButton.setInnerText(saved.canResume() ? "接回牌局（房號 " + saved.room() + "）" : "接回牌局");
        setBusy(null);
    }

    /** text 不是 null 時表示正在連線，顯示這段文字，只能按取消。 */
    void setBusy(String text) {
        busy = text != null;
        status.setInnerText(busy ? text : " ");
        syncButtons();
    }

    boolean isBusy() {
        return busy;
    }

    private void syncButtons() {
        boolean ready = !busy && nameValid();
        soloButton.setDisabled(!ready);
        createButton.setDisabled(!ready);
        joinButton.setDisabled(!ready || code().length() != ROOM_CODE_LENGTH);
        resumeButton.setDisabled(busy);
        Dom.visible(resumeButton, profile.canResume());
        Dom.visible(cancelButton, busy);
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

    private RoomSettings settings() {
        List<BotLevel> levels = new ArrayList<>();
        for (HTMLSelectElement box : levelBoxes) {
            levels.add(BotLevel.valueOf(box.getValue()));
        }
        return new RoomSettings(Integer.parseInt(chipBox.getValue()), Integer.parseInt(baseBox.getValue()),
                Integer.parseInt(taiBox.getValue()), levels, waitSeconds(), GameSpeed.valueOf(speedBox.getValue()));
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
