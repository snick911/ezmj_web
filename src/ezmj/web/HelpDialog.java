package ezmj.web;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.teavm.jso.dom.html.HTMLElement;

import ezmj.model.Meld;
import ezmj.model.Tile;
import ezmj.model.Wall;
import ezmj.online.RoomSettings;
import ezmj.rules.ScoreGuide;
import ezmj.rules.ScoreResult;
import ezmj.rules.Scorer;
import ezmj.rules.WinContext;
import ezmj.table.TableFlow;

/**
 * 簡介、基本規則、建立牌局、台數計算與關於。不擋住牌桌，可以邊看邊打。內容照 Swing 版的 HelpDialog，只拿掉單機才有的部分；
 * 建立牌局是網頁版才有的，講大廳的按鈕與設定。打開時先看簡介。
 */
final class HelpDialog extends Dialog {

    static final String COPYRIGHT = "© 2026 snick911";
    static final String LICENSE_URL = "https://www.gnu.org/licenses/agpl-3.0.html";
    private static final String ISSUES_URL = "https://github.com/snick911/ezmj_web/issues";
    /**
     * 專家模式有哪些不一樣，只寫在說明的「簡介」與「建立牌局」，設定視窗的勾選框只寫「開啟」。
     * 和 Swing 版的 StartPanel.EXPERT_SUMMARY 一樣。
     */
    static final String EXPERT_SUMMARY = "沒有聽牌提示，紀錄只留一筆，棄牌每家只看得到最後一張，也不顯示剩幾張";

    private static final int RULE_TILE_W = 30;
    private static final int EXAMPLE_TILE_W = 24;

    private final HTMLElement page = Dom.el("div", "help-page");

    HelpDialog() {
        super(false);
        card.setClassName("card help-card");
        HTMLElement tabs = Dom.el("div", "button-row");
        tabs.appendChild(Dom.button("簡介", Theme.BUTTON, this::showIntro));
        tabs.appendChild(Dom.button("基本規則", Theme.BUTTON, this::showRules));
        tabs.appendChild(Dom.button("建立牌局", Theme.BUTTON, this::showSetup));
        tabs.appendChild(Dom.button("台數計算", Theme.BUTTON, this::showScoring));
        tabs.appendChild(Dom.button("關於", Theme.BUTTON, this::showAbout));
        tabs.appendChild(Dom.button("關閉", Theme.PANEL_LIGHT, this::close));
        card.appendChild(tabs);
        card.appendChild(page);
        showIntro();
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

    /** 打開說明先看到這頁：這個網站是什麼、專家模式、連線打牌的禮儀與單機版。Swing 版說明的「簡介」寫的是同一件事，改了兩邊一起改。 */
    private void showIntro() {
        Dom.clear(page);
        h2("隨時隨地，上桌打一場");
        p("EZMJ 想提供一個可以連線打麻將的地方，不受實體場地限制：不用湊一張牌桌、不用約好地點，也不用自己洗牌疊牌。"
                + "只要有網路，手機、平板或電腦打開瀏覽器就能上桌，住在不同城市的親友也能隨時湊成一桌。");
        p("無須註冊帳號，也不用下載安裝，取個名字就能直接開、直接玩：");
        list("想和親友打：按「開房間」，把六碼房號傳給大家，用房號加入就能同桌；人數不夠，空位由電腦補滿。",
                "想認識新牌友：大廳列出正在等人的公開房間，挑一間加入就好。",
                "不想等開局：開了「隨時補位」的牌局就算已經開打，也能從大廳加入，替換一家電腦接著打。",
                "想自己練練手：按「跟電腦打」，馬上和三家電腦開打。");
        h2("每一場都是新的開始");
        p("這裡不記帳號、不累積輸贏。上一場手氣再好或再差，下一場大家都從房間定好的籌碼重新來過，沒有包袱，坐下來就能專心打牌。");
        p("雖然不留帳號，每場結束還是能留下這一場的戰績：牌局結束的視窗可以「分享結果圖」，直接傳到 LINE 這類 App，"
                + "也可以「下載輸贏 PDF」，把每一局的輸贏都記下來，和親友分享這一場的精彩。打完全場、成績亮眼的，名字還會登上大廳的「榮譽榜」。");
        h2("免費，沒有廣告");
        p("EZMJ 完全免費，畫面上沒有廣告，也沒有任何要付費的地方，單純就是打麻將。");
        h2("想挑戰自己，就開專家模式");
        p("跟電腦打與開房間的設定都有「專家模式」，勾了「開啟」就換成考驗記性與反應的打法：" + EXPERT_SUMMARY
                + "，每次最多只等 " + OnlinePanel.EXPERT_MAX_WAIT_SECONDS + " 秒。規則與台數都和平常一樣，"
                + "等哪幾張、誰打過什麼、牌牆還剩多少，全靠自己記。開房間時勾了，房間裡每個人都照專家模式打。"
                + "詳細見「建立牌局」的「專家模式」。");
        h2("隨時補位，開打了也能上桌");
        p("開房間時勾了「隨時補位」，開打之後電腦在打的座位，別人也能從大廳加入替換。牌局不會為了等人停下來："
                + "加入時先保留座位，等那家電腦打完這一手就換你上場，通常只要一兩手；這一局先結束的話，下一局開始時上場。"
                + "上場時全桌都會跳出「玩家 ○○ 替換電腦（××）加入」。中途加入的不列入榮譽榜，想上榜就在開局前加入。"
                + "詳細見「建立牌局」的「隨時補位」。");
        h2("牌桌禮儀");
        p("網路上開局，對面坐的一樣是真人牌友，禮儀和在實體牌桌上一樣重要：");
        list("最重要的一條：上了桌，就盡可能打完東南西北四圈。中途離開，你的座位只能交給電腦打到最後，同桌的人也跟著掃興；"
                        + "榮譽榜也只記打到結束的人。",
                "斷線了盡快回來：連線斷了會自己重連；網頁關掉的話，重新打開按「接回牌局」就回到原本的座位，這段時間由電腦暫時代打。",
                "出牌不拖：輪到自己時盡快決定，別讓大家乾等。",
                "說話客氣：名字、房間名稱與聊天都不能有不雅的字眼。贏了不驕、輸了不惱，打得開心最重要。");
        h2("想練功，就用單機版");
        p("另外還有裝在電腦上的單機版，除了同樣的連線對戰，還提供「修業模式」：從無名小卒練起，帶著帳上的籌碼一場一場累積，"
                + "一關一關挑戰高手，還能考取初段到九段的段位證照，紮紮實實地鍛鍊自己的麻將技巧。");
        p("單機版不連網也能隨時開一桌練習牌局，和初學者到職業四種等級的電腦對打。它的連線對戰和網頁版連到同一台服務器，可以和用網頁的親友同桌。");
        page.setScrollTop(0);
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
        h2("聽牌");
        p("跟電腦打時，輪到你打牌，如果打出某張牌就會聽牌，下方會多一個半透明的「聽牌」按鈕。按下後，打出去會聽牌的牌加上金框，"
                + "其他牌蓋起來不能選，提示會列出打哪張聽哪些牌；點一張金框的牌打出就聽牌，按「取消」可以回去照常打牌。"
                + "沒按聽牌就打出去的，這一輪就不能聽牌了。");
        p("多人的房間與專家模式沒有打牌前的提示，照常打牌，打出之後如果已經聽牌，下方會出現「聽牌」按鈕，"
                + "一直到你下一次摸牌、吃碰槓或這局結束，放棄別家打出的牌不影響。專家模式聽牌之後也不列出等哪些牌，要自己記。");
        p("聽牌之後自動摸牌打牌，胡的牌來了就自動胡，也不再吃、碰、槓，這一局不能取消。");
        p("聽牌是公開的，全桌都看得到，你的名字旁會有圓圈的「聽」。胡的時候多算「聽牌」1 台，自摸或胡別家的牌都算，"
                + "也算在牌型台數裡。電腦也會聽牌：初學者不會，其他等級看手牌與局勢決定。");
        h2("斷線");
        p("連線斷了會自己重連，最多試 3 分鐘，這段時間座位由電腦代打。關掉網頁的話，重新打開按「接回牌局」。按「離開牌局」就回不來了。");
        page.setScrollTop(0);
    }

    /** 大廳的按鈕、建立牌局的每個設定與專家模式。設定的範圍照 RoomSettings 與大廳的選單。 */
    private void showSetup() {
        Dom.clear(page);
        h2("開始之前");
        p("先在大廳最上面填名字，最多 16 字，不能有不雅的字眼。名字會顯示在牌桌上，牌局打完也用這個名字記在榮譽榜。"
                + "沒填名字或名字不合就按了跟電腦打、開房間或加入，會跳出提醒。");
        h2("跟電腦打");
        p("按「跟電腦打」，選好設定按「開始牌局」，就開一間只有你一個人的房間，由服務器補三家電腦，馬上開打。"
                + "這間房間是私人的，不列在大廳，別人加不進來。打完一場回到房間，可以再開一場。");
        h2("開房間");
        p("按「開房間」，選好設定按「開房間」。開好之後把房號告訴朋友，或等大廳上的人加入；房主按「開始牌局」才開打，"
                + "不到四人時空的座位由電腦補上。");
        list("房間名稱：最多 " + RoomSettings.MAX_NAME_LENGTH + " 字，不能有不雅的字眼，空白就用「你的名字的房間」。",
                "私人房間：勾了就不列在大廳，只能用房號加入。",
                "隨時補位：勾了「開啟」，開打之後電腦在打的座位別人也能加入替換，見下面的「隨時補位」。");
        h2("加入房間");
        p("大廳的「公開的房間」列出還在等人的房間，以及開了隨時補位、正在打的牌局（名稱後面標「牌局中」），"
                + "等人的排在前面。選一間按「加入」，或連點兩下。私人房間不在列表上，要在「用房號加入」填六碼房號。");
        p("列表的「隨時補位」與「專家」兩欄，房間有開就打勾，滑鼠停在房間上也看得到。"
                + "加入「牌局中」的房間就是補位，按了會先問一次。");
        h2("設定");
        p("跟電腦打與開房間跳出來的設定一樣，選過的值會留到重新整理網頁為止：");
        list("籌碼本金：每個人開局時的籌碼。東南西北四圈打完，或有人籌碼輸光，牌局就結束。",
                "底、每台：每位付款者付「底 ＋ 台數 × 每台」，見「台數計算」。兩個都不能大於籌碼本金，太大時設定裡會寫出來，按了送出也會提醒。",
                "補位電腦：依序是補位電腦 1、2、3，也就是房主的右家、對家、左家，各選一個等級：初學者、一般、業餘、職業。"
                        + "四個等級拿到的牌與規則都一樣，差別只在看多少資訊、想多少。開房間時有真人坐的座位用不到。",
                "等待秒數：輪到你打牌，或問你要不要吃、碰、槓、胡時，最多等幾秒，" + RoomSettings.MIN_WAIT_SECONDS + " 到 "
                        + RoomSettings.MAX_WAIT_SECONDS + "，提示後面會倒數。時間到就替你打出最右邊的牌，還沒決定的宣告算過。"
                        + "專家模式最多 " + OnlinePanel.EXPERT_MAX_WAIT_SECONDS + " 秒，勾了專家模式，填得比較長的會自動改成 "
                        + OnlinePanel.EXPERT_MAX_WAIT_SECONDS + " 秒。",
                "電腦速度：慢、中、快，是電腦出牌前與等宣告時停多久，只影響節奏，不影響牌局結果。",
                "專家模式：見下面。開房間時勾了，房間裡每個人都照專家模式打。");
        h2("專家模式");
        p("跟電腦打與開房間的設定都可以勾選專家模式：" + EXPERT_SUMMARY + "，每次最多只等 " + OnlinePanel.EXPERT_MAX_WAIT_SECONDS
                + " 秒。規則與台數都一樣，只是要記得更多、想得更快。設定視窗的勾選框只寫「開啟」，有哪些不一樣都寫在這裡。"
                + "開房間時勾了，房間裡每個人都照專家模式打，大廳的列表與房間畫面會標出「專家」。");
        list("每次最多等 " + OnlinePanel.EXPERT_MAX_WAIT_SECONDS + " 秒：等待秒數只能選 " + RoomSettings.MIN_WAIT_SECONDS + " 到 "
                        + OnlinePanel.EXPERT_MAX_WAIT_SECONDS + "，勾了專家模式，填得比較長的會自動改小，取消勾選再改回來。",
                "打牌前沒有「聽牌」按鈕。照常打牌，打出之後已經聽牌，下方才出現「聽牌」，一直到你下一次摸牌、吃碰槓或這局結束。",
                "聽牌之後提示只寫「【聽牌中】」，不列出等哪些牌。",
                "右側的紀錄只顯示最新一則，不能展開。",
                "牌河每家只留最後打出的那一張，畫在第一格，被吃碰槓胡走就不見了。",
                "牌桌中間不寫剩幾張。");
        p("服務器不會告訴專家模式的房間打哪張會聽牌。關掉網頁再按「接回牌局」，照樣是專家模式。"
                + "電腦版（Swing 版）的連線對戰還不能加入專家模式的房間。");
        h2("隨時補位");
        p("開房間的設定勾了「隨時補位」的「開啟」，開打之後，電腦在打的座位別人也能中途加入替換，"
                + "人沒到齊也能先開打，晚到的親友或大廳上的牌友隨時補上。跟電腦打的房間是私人的，沒有這個設定。"
                + "房間畫面與大廳的列表都會標出「隨時補位」。");
        list("哪些座位能換：開局時由電腦補上的座位，以及玩家中途按「離開牌局」、由電腦打到最後的座位。"
                        + "斷線的玩家還會回來，他的座位替他留著，不能換。",
                "怎麼加入：大廳列表上標「牌局中」的房間，選了按「加入」，確定之後就加入；也可以用房號加入，私人房間只能用房號。"
                        + "有好幾家電腦時，從房主的下家（座位號碼小的）換起。電腦都有人換了或正在等，就加不進去。",
                "什麼時候上場：加入後先替你保留座位，牌局照走，不會停下來等你。畫面寫著在等哪一家，"
                        + "等那家電腦打完這一手（打出一張牌）就換你上場，通常只要等一兩手。"
                        + "這一局在那之前就結束的話，下一局開始時上場，整局都是你打；整場在你上場前就打完的話，"
                        + "你會直接坐進房間，等房主開下一場。等的時候可以按「取消」回到大廳，保留的座位就讓給別人。",
                "上場之後：接著那一家的手牌、籌碼、牌河與聽牌繼續打，和斷線接回來一樣。"
                        + "全桌的牌桌上方都會跳出金框的公告「玩家 ○○ 替換電腦（××）加入」，那一家旁邊跳出「補位」，"
                        + "紀錄與聊天區也各留一行；換的是離開的玩家留下的座位，就寫「替換電腦（代打「××」的座位）」。",
                "榮譽榜：中途補位的人不列入，就算之後一直打到結束也一樣；開局時就坐著、打到結束的人照樣列入。想上榜，就在開局前加入。",
                "補位上場的人斷線，和其他人一樣由電腦代打、可以接回；按「離開牌局」的話，座位又交回電腦，別人可以再補。",
                "電腦版（Swing 版）的連線對戰還不能中途補位，大廳也看不到牌局中的房間；開局前就坐進來的照樣能打。");
        h2("接回牌局");
        p("牌局中關掉網頁或斷線太久，回到大廳時會有「接回牌局（房號 …）」，按了就回到原本的座位，這段時間由電腦代打。"
                + "按了「離開牌局」、牌局已經結束，或全桌都斷線太久房間關了，就接不回來。");
        page.setScrollTop(0);
    }

    /** 一串項目。 */
    private void list(String... items) {
        HTMLElement list = Dom.el("ul", "");
        for (String item : items) {
            list.appendChild(Dom.text("li", "", item));
        }
        page.appendChild(list);
    }

    private void showScoring() {
        Dom.clear(page);
        h2("每位付款者付多少");
        page.appendChild(Dom.text("p", "formula", "底 ＋ 台數 × 每台"));
        p("底與每台在建立牌局時選擇，見「建立牌局」。台數是牌型台數的總和，再加上莊家台：");
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
