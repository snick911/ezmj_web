package ezmj.web;

import java.util.List;
import java.util.Map;

import org.teavm.jso.dom.html.HTMLButtonElement;
import org.teavm.jso.dom.html.HTMLElement;

import ezmj.model.GameRecord;
import ezmj.model.HonorRoll;
import ezmj.online.OnlineClient;
import ezmj.online.RoomListing;

/**
 * 連線牌局的榮譽榜，資料由服務器記錄，欄位照 Swing 版 HelpDialog 的榮譽榜頁。
 * Swing 版選單裡的是本機單機牌局的紀錄，和這份是分開的。
 * 打開時另外開一條不登入的連線送 honorRoll，收到就關掉，所以每次打開都是最新的。
 */
final class HonorRollDialog extends Dialog implements OnlineClient.Listener {

    private static final String[] COLUMNS = {"名次", "姓名", "胡牌", "自摸", "贏錢", "放槍", "電腦", "底／台", "日期"};
    /** 胡牌那一欄在 COLUMNS 的位置，後面依序是自摸、贏錢、放槍，和類別一樣。 */
    private static final int FIRST_VALUE_COLUMN = 2;

    private final String myName;
    private final HTMLElement body = Dom.el("div", "honor-body");
    private final HTMLButtonElement[] tabs = new HTMLButtonElement[HonorRoll.Category.values().length];
    private final HTMLButtonElement close;
    private WebClient client;
    private Map<HonorRoll.Category, List<GameRecord>> roll;
    private HonorRoll.Category shown = HonorRoll.Category.WINS;

    /** myName 是大廳上填的名字，榜上同名的那幾列會標出來；沒填就是空字串。 */
    HonorRollDialog(String serverUrl, String myName) {
        super(true);
        this.myName = myName.trim();
        card.setClassName("card honor-card");
        card.appendChild(Dom.text("h2", "headline", "榮譽榜"));
        card.appendChild(Dom.text("div", "muted", "連線牌局打到結束的真人成績，每個類別留前 " + HonorRoll.SIZE + " 名。"));

        HTMLElement tabRow = Dom.el("div", "honor-tabs");
        tabRow.setAttribute("role", "tablist");
        HonorRoll.Category[] categories = HonorRoll.Category.values();
        for (int i = 0; i < categories.length; i++) {
            HonorRoll.Category category = categories[i];
            tabs[i] = Dom.button(category.label(), Theme.PANEL_LIGHT, () -> select(category));
            tabs[i].setAttribute("role", "tab");
            tabRow.appendChild(tabs[i]);
        }
        card.appendChild(tabRow);
        card.appendChild(body);
        card.appendChild(Dom.text("div", "muted", "胡牌數包含自摸。贏錢是結束時的籌碼減去本金。每個類別只列數值大於 0 的成績，"
                + "數值相同時先達成的排前面。中途離開、斷線由電腦接手過的座位與電腦都不列入。"));
        close = Dom.button("關閉", Theme.BUTTON, this::close);
        card.appendChild(close);
        select(shown);
        showStatus("正在讀取榮譽榜，服務器閒置後第一次可能要等一分鐘…");

        client = new WebClient(serverUrl, this);
        client.query(Messages.honorRoll());
    }

    @Override
    void show() {
        super.show();
        close.focus();
    }

    /** 視窗收掉時連線也關掉，還沒回應的就不要了。 */
    @Override
    void close() {
        stopClient();
        super.close();
    }

    private void stopClient() {
        if (client != null) {
            client.close();
            client = null;
        }
    }

    private void select(HonorRoll.Category category) {
        shown = category;
        HonorRoll.Category[] categories = HonorRoll.Category.values();
        for (int i = 0; i < categories.length; i++) {
            boolean selected = categories[i] == category;
            tabs[i].getStyle().setProperty("background-color", selected ? Theme.RED : Theme.PANEL_LIGHT);
            tabs[i].setAttribute("aria-selected", String.valueOf(selected));
        }
        if (roll != null) {
            showTable();
        }
    }

    private void showStatus(String text) {
        Dom.clear(body);
        body.appendChild(Dom.text("p", "muted honor-status", text));
    }

    /** 名字來自網路，GridTable 一律用 innerText。這個類別的那一欄是金色，自己的名字整列標出來。 */
    private void showTable() {
        List<GameRecord> records = roll.get(shown);
        if (records.isEmpty()) {
            showStatus("還沒有紀錄。連線牌局打到結束，成績就會記錄在這裡。");
            return;
        }
        GridTable table = new GridTable(2, 1);
        table.header(COLUMNS);
        int highlighted = FIRST_VALUE_COLUMN + shown.ordinal();
        for (int i = 0; i < records.size(); i++) {
            GameRecord record = records.get(i);
            boolean mine = !myName.isEmpty() && record.name().equals(myName);
            HTMLElement[] cells = table.row(mine, String.valueOf(i + 1), record.name(), String.valueOf(record.wins()),
                    String.valueOf(record.selfDraws()), GameReport.grouped(record.winnings()),
                    String.valueOf(record.dealIns()), record.botLevelsLabel(),
                    GameReport.grouped(record.basePay()) + "／" + GameReport.grouped(record.taiPay()), record.date());
            Dom.color(cells[highlighted], Theme.GOLD);
            if (mine) {
                ((HTMLElement) cells[0].getParentNode()).setClassName("bold mine");
            }
        }
        HTMLElement scroll = Dom.el("div", "honor-scroll");
        scroll.appendChild(table.element());
        Dom.clear(body);
        body.appendChild(scroll);
    }

    /** 不登入的連線只會收到 honorRoll 或錯誤。舊服務器不認得 honorRoll 時回的是錯誤。 */
    @Override
    public void message(Map<String, Object> message) {
        Object type = message.get("type");
        if ("honorRoll".equals(type)) {
            stopClient();
            roll = HonorRollMessage.parse(message);
            showTable();
        } else if ("error".equals(type)) {
            stopClient();
            showStatus("這台服務器還沒有榮譽榜。");
        }
    }

    @Override
    public void closed(String reason) {
        client = null;
        if (roll == null) {
            showStatus("連不上服務器，請稍後再打開一次。");
        }
    }

    @Override
    public void badMessage(String line, RuntimeException error) {
        Browser.warn("看不懂服務器的榮譽榜：" + error.getMessage());
    }

    /** 不看房間列表。 */
    @Override
    public void rooms(List<RoomListing> rooms) {
    }

    /** 不登入，不會收到。 */
    @Override
    public void loggedIn(Map<String, Object> welcome, boolean resumed) {
    }

    /** 沒有 token 不會重連，斷線直接走 closed。 */
    @Override
    public void connectionLost() {
    }

    /** 沒有送 hello，錯誤都走 message。 */
    @Override
    public void loginRejected(String code, String message) {
    }
}
