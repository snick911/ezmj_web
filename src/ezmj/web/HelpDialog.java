package ezmj.web;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.teavm.jso.dom.html.HTMLElement;

import ezmj.model.Meld;
import ezmj.model.Tile;
import ezmj.model.Wall;
import ezmj.rules.ScoreGuide;
import ezmj.rules.ScoreResult;
import ezmj.rules.Scorer;
import ezmj.rules.WinContext;
import ezmj.table.TableFlow;

/** 基本規則、台數計算與關於。不擋住牌桌，可以邊看邊打。內容照 Swing 版的 HelpDialog，只拿掉單機才有的部分。 */
final class HelpDialog extends Dialog {

    static final String COPYRIGHT = "© 2026 snick911";
    static final String LICENSE_URL = "https://www.gnu.org/licenses/agpl-3.0.html";
    private static final String ISSUES_URL = "https://github.com/snick911/ezmj_web/issues";

    private static final int RULE_TILE_W = 30;
    private static final int EXAMPLE_TILE_W = 24;

    private final HTMLElement page = Dom.el("div", "help-page");

    HelpDialog() {
        super(false);
        card.setClassName("card help-card");
        HTMLElement tabs = Dom.el("div", "button-row");
        tabs.appendChild(Dom.button("基本規則", Theme.BUTTON, this::showRules));
        tabs.appendChild(Dom.button("台數計算", Theme.BUTTON, this::showScoring));
        tabs.appendChild(Dom.button("關於", Theme.BUTTON, this::showAbout));
        tabs.appendChild(Dom.button("關閉", Theme.PANEL_LIGHT, this::close));
        card.appendChild(tabs);
        card.appendChild(page);
        showRules();
    }

    private void h2(String text) {
        page.appendChild(Dom.text("h2", "help-h2", text));
    }

    private void p(String text) {
        page.appendChild(Dom.text("p", "", text));
    }

    private void note(String text) {
        page.appendChild(Dom.text("p", "muted", text));
    }

    /** 一段文字後面接一排牌。 */
    private void tiles(String label, String spec) {
        HTMLElement line = Dom.el("p", "tile-line");
        if (!label.isEmpty()) {
            line.appendChild(Dom.text("span", "", label));
        }
        line.appendChild(strip(groups(spec), RULE_TILE_W));
        page.appendChild(line);
    }

    /** 手上的牌，加上紅框的那張，等於亮出來的面子。 */
    private void claim(String hand, String tile) {
        List<Tile> set = new ArrayList<>(Tile.parseAll(hand));
        set.addAll(Tile.parseAll(tile));
        set.sort(null);
        HTMLElement line = Dom.el("p", "tile-line");
        line.appendChild(strip(groups(hand), RULE_TILE_W));
        line.appendChild(Dom.text("span", "", "＋"));
        line.appendChild(strip(List.of(new TileStrip.Group(Tile.parseAll(tile), true)), RULE_TILE_W));
        line.appendChild(Dom.text("span", "", "→"));
        line.appendChild(strip(List.of(new TileStrip.Group(set, false)), RULE_TILE_W));
        page.appendChild(line);
    }

    /** 空白隔開的每一段會畫成一組。 */
    private static List<TileStrip.Group> groups(String spec) {
        return Arrays.stream(spec.split(" "))
                .map(part -> new TileStrip.Group(Tile.parseAll(part), false))
                .collect(Collectors.toList());
    }

    private static HTMLElement strip(List<TileStrip.Group> groups, int tileW) {
        return TileStrip.of(groups, tileW, Math.round(tileW * 1.38), Math.round(tileW * .4f));
    }

    private void showRules() {
        Dom.clear(page);
        h2("牌");
        p("共 144 張。下面每種 4 張：");
        tiles("萬", "1w2w3w4w5w6w7w8w9w");
        tiles("筒", "1p2p3p4p5p6p7p8p9p");
        tiles("條", "1s2s3s4s5s6s7s8s9s");
        tiles("字", "1z2z3z4z 5z6z7z");
        p("另外有八張花牌，各 1 張：");
        tiles("花", "1f2f3f4f 5f6f7f8f");
        h2("開局");
        p("四人對局，第一局隨機決定莊家。每人發 16 張，莊家再摸第 17 張後先打。");
        p("拿到花牌會自動亮出，並從牌尾補一張。");
        p("八張花牌都亮出來時，一家集滿八張（八仙過海）或一家七張、別家一張（七搶一）就立即胡，不看手牌，開局補花時也一樣。"
                + "八仙過海算自摸，三家付錢；七搶一拿走別家那張花，算那家放槍，只有他付錢。兩種都是 8 台，再依規則加莊家台。");
        h2("胡牌");
        p("胡牌要湊成五組面子加一對眼睛，共 17 張。面子是順子、刻子或槓。沒有其他特殊胡牌牌型，也沒有最低台數，0 台一樣能胡。");
        tiles("", ScoreGuide.WINNING_HAND);
        note("上面這手牌依序是兩組順子、三組刻子，最後一對是眼睛。");
        p("輪到自己摸到胡牌是自摸，由三家付錢；胡別家打出的牌，只有放槍的人付錢。");
        h2("吃、碰、槓");
        note("紅框是別家打出或自己要加上去的那張牌。");
        p("吃：只能吃上家打出的數牌，湊成順子。");
        claim("1w2w", "3w");
        p("碰：手上有兩張相同的牌，可碰任何一家打出的牌。");
        claim("5p5p", "5p");
        p("明槓：手上有三張相同的牌，可槓任何一家打出的牌。");
        claim("9s9s9s", "9s");
        p("暗槓：輪到自己時，手上有四張相同的牌。");
        tiles("", "1z1z1z1z");
        p("加槓：輪到自己時，用手上的牌加到已經碰出的刻子上。別家若能胡這張牌，可以搶槓。");
        claim("7w7w7w", "7w");
        p("吃、碰之後要直接打一張牌。槓牌之後從牌尾補一張。");
        p("只有一種選擇時，按下吃、碰、槓就直接宣告；胡與自摸也是按了就胡。"
                + "吃有多種組合、或自己回合有多張牌能槓時才進入選牌：能用的手牌維持原色，不能選的會蓋上遮罩，點手牌就決定。"
                + "吃牌時點共用的牌後再選一張。按「取消」可以回去。");
        p("同一張牌有多人宣告時，胡優先於槓、碰，槓、碰優先於吃。多人同時要胡，只有放槍者的下家起算最近的一家能胡。");
        p("已經有人決定的宣告比還在考慮的人能做的都大時，就不再等他。例如你只能吃、電腦要碰，"
                + "電腦等 " + TableFlow.BOT_CLAIM_MS / 1000 + " 秒就先碰走，不用等你按。你能胡、或能做的和電腦一樣大時，照舊等你。");
        h2("流局");
        p("牌牆固定留 " + Wall.RESERVED + " 張不摸，其餘摸完還沒有人胡就流局，這局沒有輸贏。");
        h2("莊家與圈風");
        p("莊家胡牌或流局就連莊，其他情況由下家接莊。莊家的門風是東，下家起依序是南、西、北。");
        p("從東風圈開始，每輪完一次莊就換下一圈。");
        h2("牌局結束");
        p("東南西北四圈打完，或有人籌碼輸光，牌局就結束，依籌碼排名，並列出四家的胡牌、自摸、放槍與輸贏。");
        h2("每局結果");
        p("結果視窗會倒數，時間到自動進入下一局：流局 5 秒，其他 15 秒。也可以直接按按鈕。");
        h2("報聽");
        p("照常打牌，打出之後如果已經聽牌，下方會出現「聽牌」按鈕。這個機會一直到你下一次摸牌、吃碰槓或這局結束，"
                + "放棄別家打出的牌不影響。按下就報聽：之後自動摸牌打牌，胡的牌來了就自動胡，也不再吃、碰、槓，這一局不能取消。");
        p("報聽是公開的，全桌都看得到，你的名字旁會有圓圈的「聽」。胡的時候多算「聽牌」1 台，自摸或胡別家的牌都算，"
                + "也算在牌型台數裡。電腦也會報聽：初學者不報，其他等級看手牌與局勢決定。");
        h2("斷線");
        p("連線斷了會自己重連，最多試 3 分鐘，這段時間座位由電腦代打。關掉網頁的話，重新打開按「接回牌局」。按「離開牌局」就回不來了。");
        page.setScrollTop(0);
    }

    private void showScoring() {
        Dom.clear(page);
        h2("每位付款者付多少");
        page.appendChild(Dom.text("p", "formula", "底 ＋ 台數 × 每台"));
        p("底與每台在開房間時選擇。台數是牌型台數的總和，再加上莊家台：");
        HTMLElement dealer = Dom.el("ul", "");
        dealer.appendChild(Dom.text("li", "", "胡牌者或付款者是莊家時，這筆付款加莊家 1 台。"));
        dealer.appendChild(Dom.text("li", "", "莊家連莊時再加連 N 拉 N，也就是連莊次數 × 2 台。"));
        dealer.appendChild(Dom.text("li", "", "與莊家無關的付款不加。"));
        page.appendChild(dealer);
        h2("算算看");
        p("底 100、每台 20，胡的牌型共 3 台：");
        HTMLElement examples = Dom.el("ul", "");
        examples.appendChild(Dom.text("li", "", "閒家胡閒家放槍：100 ＋ 3 × 20 ＝ 160，放槍的人付 160。"));
        examples.appendChild(Dom.text("li", "", "閒家胡莊家放槍：100 ＋ (3 ＋ 1) × 20 ＝ 180，莊家付 180。"));
        examples.appendChild(Dom.text("li", "", "連 2 的莊家自摸：100 ＋ (3 ＋ 1 ＋ 4) × 20 ＝ 260，三家各付 260。"));
        page.appendChild(examples);
        h2("牌型台數");
        p("符合的牌型可以累加。同一手牌有多種拆法時，取台數最高的拆法。");
        p("下表的刻子都包含槓。暗槓算暗刻，也不影響門清。胡別家打出的牌時，如果這張牌只能湊成刻子，這組不算暗刻。");
        note("每個牌型下面都有一手範例：紅框是胡的那張牌，最左邊是亮出的面子，最右邊是花牌。"
                + "「這手共幾台」是實際計算的結果，可能包含其他一起成立的牌型。");
        for (ScoreGuide.Group group : ScoreGuide.GROUPS) {
            page.appendChild(Dom.text("h3", "help-h3", group.title()));
            for (ScoreGuide.Entry entry : group.entries()) {
                addEntry(entry);
            }
        }
        page.setScrollTop(0);
    }

    /**
     * AGPL 第 5 條的「適當的法律聲明」：著作權、不提供擔保、可以照這個授權散布，以及去哪裡看授權條款。
     * 原版的畫面有顯示，改過的版本就要照樣顯示，不要拿掉。原始碼的網址是作者決定不放的，
     * 改過的版本架成網站時，照 AGPL 第 13 條要讓玩家拿得到它自己的原始碼。
     */
    private void showAbout() {
        Dom.clear(page);
        h2("EZMJ 台灣十六張麻將 網頁版");
        p("Copyright " + COPYRIGHT);
        p("這個程式是自由軟體：你可以依照自由軟體基金會發布的 GNU Affero 通用公眾授權條款第 3 版（AGPL-3.0）使用、修改與散布。");
        p("這個程式不提供任何擔保，連適售性或特定用途的適用性都沒有。詳細內容請看授權條款，以英文原文為準。");
        link("授權條款", LICENSE_URL);
        note("修改過的版本架成網站給別人玩，也要讓玩家拿得到完整的原始碼，這段聲明也要留著。");
        h2("商業授權");
        p("不想公開原始碼的商業用途，可以向作者取得商業授權，請到 GitHub Issues 開一則標題寫「商業授權」的 issue。");
        link("GitHub Issues", ISSUES_URL);
        h2("其他部分的授權");
        p("牌、規則與局面翻譯來自 EZMJ，同樣是 AGPL-3.0。");
        p("牌面的備用字型是霞鶩文楷 TC 的子集，授權是 SIL Open Font License 1.1。");
        p("TeaVM 編進來的 Java 執行環境，授權是 Apache License 2.0。");
        page.setScrollTop(0);
    }

    /** 網址直接顯示出來，複製得到。 */
    private void link(String label, String url) {
        HTMLElement line = Dom.el("p", "");
        line.appendChild(Dom.text("span", "", label + "："));
        line.appendChild(Dom.link(url, url));
        page.appendChild(line);
    }

    private void addEntry(ScoreGuide.Entry entry) {
        HTMLElement item = Dom.el("div", "score-entry");
        HTMLElement head = Dom.el("div", "score-head");
        head.appendChild(Dom.text("span", "bold", entry.name()));
        head.appendChild(Dom.text("span", "gold", entry.tai() + " 台"));
        head.appendChild(Dom.text("span", "", entry.condition()));
        item.appendChild(head);
        if (entry.example() == null) {
            item.appendChild(strip(List.of(new TileStrip.Group(Tile.parseAll("1f2f3f4f5f6f7f8f"), false)), EXAMPLE_TILE_W));
        } else {
            item.appendChild(strip(handGroups(entry.example()), EXAMPLE_TILE_W));
            item.appendChild(Dom.text("div", "muted", caption(entry.example())));
        }
        page.appendChild(item);
    }

    private static List<TileStrip.Group> handGroups(WinContext hand) {
        List<Tile> concealed = new ArrayList<>(hand.concealed());
        concealed.remove(hand.winTile());
        concealed.sort(null);
        List<TileStrip.Group> groups = new ArrayList<>();
        for (Meld meld : hand.melds()) {
            groups.add(new TileStrip.Group(meld.tiles(), false));
        }
        if (!concealed.isEmpty()) {
            groups.add(new TileStrip.Group(concealed, false));
        }
        groups.add(new TileStrip.Group(List.of(hand.winTile()), true));
        if (!hand.flowers().isEmpty()) {
            groups.add(new TileStrip.Group(hand.flowers(), false));
        }
        return groups;
    }

    private static String caption(WinContext hand) {
        ScoreResult score = Scorer.score(hand);
        String items = score.items().stream()
                .map(item -> item.name() + " " + item.tai() + " 台")
                .collect(Collectors.joining("、"));
        return Tile.windName(hand.roundWind()) + "風圈、門風" + Tile.windName(hand.seatWind()) + "，"
                + (hand.selfDraw() ? "自摸 " : "胡別家打出的 ") + hand.winTile().label()
                + "。這手共 " + score.totalTai() + " 台：" + items;
    }
}
