package ezmj.web;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.teavm.jso.browser.Window;
import org.teavm.jso.canvas.CanvasGradient;
import org.teavm.jso.canvas.CanvasRenderingContext2D;
import org.teavm.jso.dom.events.MouseEvent;
import org.teavm.jso.dom.html.HTMLCanvasElement;
import org.teavm.jso.dom.html.HTMLDocument;
import org.teavm.jso.dom.html.HTMLElement;

import ezmj.engine.Round;
import ezmj.engine.RoundEvent;
import ezmj.engine.RoundResult;
import ezmj.engine.RoundView;
import ezmj.engine.WallRing;
import ezmj.model.Dice;
import ezmj.model.Meld;
import ezmj.model.Player;
import ezmj.model.Tile;

/**
 * 牌桌畫面，版面與動畫時間照 Swing 版的 TableCanvas。四家共用同一套繪製程式：先把座標旋轉到該家視角，
 * 再以「自己坐在下方」的區域座標繪製，所以棄牌會自然排成風車狀。
 */
final class TableCanvas {

    /** 打出的牌飛到亮牌的位置，落地的聲音在這時候響。 */
    static final int DISCARD_FLIGHT_MS = 190;
    /**
     * 打出的牌先放大、轉成正著給自己看，停 DISCARD_SHOW_MS，再縮小飛進牌河，花 DISCARD_SETTLE_MS。
     * 全部大約 0.7 秒，中速時下一家打牌前演得完；下一張打得更快就直接落進牌河。
     */
    private static final int DISCARD_SHOW_MS = 300;
    private static final int DISCARD_SETTLE_MS = 200;
    /** 亮牌最大是棄牌的幾倍寬，比自己的手牌大四成左右；地方不夠時縮小，但至少這麼大。 */
    private static final double SHOW_SCALE = 3.0;
    private static final double SHOW_MIN_SCALE = 1.5;
    /** 亮牌沿著打牌那家方向的長度，最多佔牌桌中心到他手牌之間的幾成。 */
    private static final double SHOW_ROOM = .8;
    static final int CLAIM_FLIGHT_MS = 240;
    static final int DRAW_FLIGHT_MS = 170;
    private static final int FLOWER_FLIGHT_MS = 260;
    private static final int FLOWER_BANNER_MS = 650;
    /** 胡別家打出的牌，流局也是這麼久。 */
    static final int WIN_BANNER_MS = 1100;
    static final int SELF_DRAW_BANNER_MS = 1400;
    /** 大胡，見 RoundResult.isBigWin。 */
    static final int BIG_WIN_BANNER_MS = 2400;
    private static final int SPARKS = 40;
    private static final double GOLDEN_ANGLE = 2.39996323;
    private static final String BIG_WIN_TOP = "rgba(201,58,50,.922)";
    private static final String BIG_WIN_BOTTOM = "rgba(110,18,14,.922)";
    /**
     * 流局時把牌牆剩下的牌一張一張翻開給大家看，和 Swing 版一樣：「流局」的大字出來 REVEAL_DELAY_MS 之後開始，
     * 每張隔 REVEAL_INTERVAL_MS、翻 REVEAL_TILE_MS，全部翻完再停 REVEAL_HOLD_MS 才跳出結果。一排 REVEAL_COLUMNS 張。
     */
    static final int REVEAL_DELAY_MS = 900;
    static final int REVEAL_INTERVAL_MS = 110;
    private static final int REVEAL_TILE_MS = 160;
    private static final int REVEAL_HOLD_MS = 1300;
    private static final int REVEAL_COLUMNS = 8;
    /** 報聽時「聽」標記彈出來、外面兩個圈擴散的時間。 */
    static final int LISTEN_BADGE_MS = 900;
    /** 吃碰槓後要打牌時，手牌外框一明一暗的週期。 */
    static final int PROMPT_PULSE_MS = 1000;
    /**
     * 開局先擲骰子：三顆骰子從莊家面前滾到中間，DICE_ROLL_MS 停住，亮出點數與開門的位置，
     * DICE_MS 之後才從牌牆發牌。DICE_BOUNCE_MS 是骰子落地出聲的時間。服務器留 3 秒，骰子加發牌要在這之內。
     */
    static final int DICE_ROLL_MS = 650;
    static final int DICE_MS = 1100;
    static final int[] DICE_BOUNCE_MS = {300, 480, 620};
    /** 發牌開始後骰子淡出、中央的字淡入的時間。 */
    private static final int DICE_FADE_MS = 300;
    /** 開門位置的金線，發牌開始後再留這麼久。 */
    private static final int OPEN_MARK_MS = 600;
    private static final double DICE_SIZE = 26;
    private static final String DIE_FACE = "#F8F3E6";
    private static final String DIE_EDGE = "#8A7F6A";
    private static final String DIE_PIP = "#1E232B";
    /** 骰子各點的位置，以一面的中心為原點，單位是點距。 */
    private static final double[][][] PIPS = {
        {},
        {{0, 0}},
        {{-1, -1}, {1, 1}},
        {{-1, -1}, {0, 0}, {1, 1}},
        {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}},
        {{-1, -1}, {1, -1}, {0, 0}, {-1, 1}, {1, 1}},
        {{-1, -1}, {1, -1}, {-1, 0}, {1, 0}, {-1, 1}, {1, 1}},
    };
    /** 發牌每四張一批輪流發，最後一批是莊家的第 17 張。 */
    static final int DEAL_PACKETS = 17;
    static final int DEAL_FLIGHT_MS = 200;
    static final int DEAL_INTERVAL_MS = 70;
    static final int DEAL_MS = (DEAL_PACKETS - 1) * DEAL_INTERVAL_MS + DEAL_FLIGHT_MS;
    /** 發牌從牌頭拿走的張數：每家 16 張，莊家多一張。 */
    private static final int DEALT_TILES = (DEAL_PACKETS - 1) * 4 + 1;

    /** 牌牆在中央方框四邊名字的外面，深度裡上下兩張錯開，上面那張往中間偏。 */
    private static final double WALL_TOP = 103;
    private static final double WALL_DEPTH = 17;
    private static final int DISCARD_COLUMNS = 8;
    /** 棄牌原本的大小，手機的兩種版面與地方不夠的電腦版照這個畫。 */
    private static final double DISCARD_W = 25;
    private static final double DISCARD_H = 33;
    /** 電腦版地方夠的時候，棄牌最寬放大到這麼寬（單位同 DISCARD_W，和 Swing 版一樣），見 computeDiscardScale。 */
    private static final double MAX_DISCARD_W = 39;
    /** 花牌最多八張；手牌最少十六格。用來算棄牌會不會排到花牌底下，牌局中張數變了棄牌也不會忽大忽小。 */
    private static final int MAX_FLOWERS = 8;
    private static final double MIN_HAND_SLOTS = 16;
    /** 棄牌排在牌牆外面。 */
    private static final double DISCARD_TOP = 123;
    private static final double FLOWER_W = 22;
    private static final double FLOWER_H = 30;
    private static final int RING_MS = 170;
    private static final int CLAIM_BANNER_MS = 800;
    private static final double TILE_RATIO = 1.38;
    /** 寬度不夠時手牌可以互相疊住，牌最多畫成間距的這個倍數，牌面的字還看得清楚。 */
    private static final double MAX_OVERLAP = 1.22;
    /** 手牌列至少要留這麼多格，張數少的時候牌也不會忽大忽小。 */
    private static final double MIN_ROW_SLOTS = 18;
    private static final double MAX_TABLE_SCALE = 1.25;
    private static final int DISCARD_ROWS_TO_FIT = 3;
    /** 一局打到底每家大約打出這麼多張，手機的短邊排不下三排時，要讓每排多放幾張才放得下。 */
    private static final int DISCARDS_TO_FIT = 24;
    /** 直向的手機排成一排時，牌的間距小於這個寬度就太難點了，手牌要分兩排。 */
    private static final double TWO_LINES_BELOW_STEP = 30;
    /** 分兩排時每排多留的空間，組與組之間有空隙，一排的最後一張可能超出一點。 */
    private static final double LINE_MARGIN_SLOTS = .75;
    /** 手機的高倍率螢幕整桌重畫很吃力，超過這個倍率肉眼也看不太出差別。 */
    private static final double MAX_PIXEL_RATIO = 2;
    /**
     * 手機橫向的版面：高度最缺，名字移到各家旁邊，中央只剩圈風、張數與牌牆，棄牌才放得大。
     * 上下兩家的棄牌每排 12 張、排兩排；左右兩家每排 4 張往兩側排。左右兩家一排的長度不超過棄牌離中心的距離，
     * 才不會撞到上下兩家的棄牌，所以上下兩家可以往左右排得很寬。單位是 tableUnit。
     */
    private static final double WIDE_BOX_HALF = 38;
    private static final double WIDE_WALL_TOP = 42;
    private static final double WIDE_WALL_DEPTH = 9;
    private static final double WIDE_DISCARD_TOP = 53;
    private static final int WIDE_COLUMNS_ACROSS = 12;
    private static final int WIDE_COLUMNS_SIDE = 4;
    private static final int WIDE_ROWS_ACROSS = 2;
    private static final int WIDE_ROWS_SIDE = 6;
    /** 橫向時自己的手牌排滿整個寬度，但牌高最多佔牌桌高度的這個比例。 */
    private static final double WIDE_HAND_HEIGHT = .19;
    /** 中央方框四邊的名字，單位是 unit。Swing 版是 14，網頁版的名字放大一點比較好認。 */
    private static final double SEAT_LABEL_SIZE = 17;
    /** 橫向時名字標籤的寬度上限、高度與字的大小，單位是 unit。 */
    private static final double WIDE_LABEL_W = 150;
    private static final double WIDE_LABEL_H = 25;
    private static final double WIDE_LABEL_SIZE = 15;
    /** 聊天泡泡離牌桌中心多遠，是中心到該家邊緣的比例：上下兩家在棄牌上，左右兩家靠近手牌。 */
    private static final double CHAT_ACROSS = .5;
    private static final double CHAT_SIDE = .76;
    /** 橫向時寫給動作列的 CSS 變數，順序和 syncOverlay 算的值一樣。 */
    private static final String[] OVERLAY_VARS = {
        "--hint-left", "--hint-bottom", "--hint-width", "--act-right", "--act-bottom", "--act-width"};

    private record Point(double x, double y) {
    }

    private record HandHit(double x, double y, double w, double h, Tile tile) {
        boolean contains(double px, double py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }

        double centerX() {
            return x + w / 2;
        }
    }

    /**
     * 一家手牌列的位置，都是該家視角的座標。step 是相鄰兩張的間距，比 tileW 小時牌會互相疊住。
     * 手牌分兩排時，位置還是當成一長排來算，超過 wrapAt 的牌才折到第二排，往下 lineStep。只有一排時 wrapAt 是無限大。
     */
    private record RowLayout(double x, double y, double tileW, double tileH, double step, double gap, double total,
                             double wrapAt, double lineStep) {
        private boolean wrapped(double at) {
            return at - x >= wrapAt - 1e-6;
        }

        double placeX(double at) {
            return wrapped(at) ? at - wrapAt : at;
        }

        double placeY(double at) {
            return wrapped(at) ? y + lineStep : y;
        }
    }

    /**
     * 一面牌牆的位置，該家視角。牌牆從 top 往外 depth 那麼深，左右從 -top 到 top + depth，
     * 四面像風車一樣各佔一個角，彼此不重疊。每墩寬 stackW，牌寬 tileW、深 tileH，上面那張往中間錯開 lift。
     */
    private record WallLayout(double top, double depth, double stackW, double tileW, double tileH, double lift) {
        /** 從右端數過來第 column 墩的左邊。 */
        double stackX(int column) {
            return top + depth - (column + 1) * stackW + (stackW - tileW) / 2;
        }
    }

    /** 一家的花牌列，該家視角。牌寬 w，相鄰兩張的間距 step，放不下時 step 比 w 小，牌會疊住。 */
    private record FlowerRow(double x, double y, double w, double h, double step) {
    }

    /**
     * 橫向時一家的名字與花牌排成一條，螢幕座標，正著擺。名字標籤在 x 到 x + w，fontSize 是縮過、放得進去的字型大小。
     * 第 i 張花牌的左邊是 flowerX(i)：從 flowerStart 往 direction 那邊排，間距 step，放不下時比牌寬小，牌會疊住。
     */
    private record NameStrip(double x, double y, double w, double h, String text, double fontSize, boolean listening,
                             double flowerStart, double flowerY, double flowerW, double flowerH, double step,
                             int direction) {
        double flowerX(int index) {
            return direction > 0 ? flowerStart + index * step : flowerStart - flowerW - index * step;
        }
    }

    /** 飛行中的牌要去的地方。 */
    private enum Slot { DISCARD, MELD, DRAWN, FLOWER }

    /**
     * 打出的牌中途停下來亮給大家看的地方：中心 (x, y)、寬 w，以牌桌中心為原點，牌是正著的。
     * turn 是從打牌那家的角度轉到正著要轉幾弧度，飛進牌河時再轉回去。
     */
    private record Showcase(double x, double y, double w, double turn) {
    }

    /**
     * 飛行中的一張牌。位置以牌桌中心為原點，指牌的中心點。
     * 飛行時對應的那張牌不畫出來，落地後才出現。slotIndex 是面子或花牌裡的第幾張，其他地方用不到。
     * show 不是 null 時是打出的牌：先飛到 show 放大亮牌，再飛進牌河，durationMs 是全部的時間。
     */
    private record Flight(Tile tile, boolean faceDown, double fromX, double fromY, double fromAngle, double fromW,
                          double toX, double toY, double toAngle, double toW, double start, int durationMs,
                          Slot slot, int slotSeat, int slotIndex, Showcase show) {
        double progress(double now) {
            return (now - start) / durationMs;
        }

        boolean finished(double now) {
            return now - start >= durationMs + RING_MS;
        }

        /** 落在牌桌上的牌才有落地的光圈，摸牌與補花沒有。 */
        boolean ringsOnLanding() {
            return slot == Slot.DISCARD || slot == Slot.MELD;
        }
    }

    /** 大字的樣子：吃碰槓、補花與流局只有字，三種胡牌在字的後面各有不同的效果。 */
    private enum Style { PLAIN, WIN, SELF_DRAW, BIG_WIN }

    /** 跳出來的大字。位置以牌桌中心為原點。detail 是大胡時寫在字下面的台數，其他時候是 null。 */
    private record Banner(String text, String detail, double x, double y, double size, String color, Style style,
                          double start, int durationMs) {
        double progress(double now) {
            return (now - start) / durationMs;
        }
    }

    private final HTMLElement host = Dom.el("div", "table-canvas");
    private final HTMLCanvasElement canvas = (HTMLCanvasElement) HTMLDocument.current().createElement("canvas");
    private final CanvasRenderingContext2D context = (CanvasRenderingContext2D) canvas.getContext("2d");
    private final Consumer<Tile> onDiscard;
    private final List<HandHit> hits = new ArrayList<>();
    private final List<Flight> flights = new ArrayList<>();
    private final List<Banner> banners = new ArrayList<>();
    private RoundView round;
    private boolean discardEnabled;
    /** 選牌中可以點的牌；null 表示沒有在選牌。 */
    private Set<Tile> pickable;
    /** 已經選好的第一張牌；吃牌還要再選一張時才有。 */
    private Tile picked;
    /** 選牌中能點的牌加上金框。 */
    private boolean markPickable;
    private Consumer<Tile> onPick;
    private int hover = -1;
    /** 這次點擊是手指點的，瀏覽器接著會送滑鼠事件。 */
    private boolean touched;
    /** 手指點下去之前抬起來的是哪一張。 */
    private int raisedBeforeTouch = -1;
    /** 手機版面：牌桌上沒有計分板，籌碼要標在名字旁邊。 */
    private boolean compact;
    /** 棄牌比原本放大幾倍，每次對齊畫布大小時重算（牌的大小設定也會影響），見 computeDiscardScale。 */
    private double discardScale = 1;
    /** 手機橫向的版面，見 WIDE_BOX_HALF。 */
    private boolean wide;
    /** 橫向時浮在牌桌上的提示與按鈕，位置用 CSS 變數告訴它。 */
    private HTMLElement overlay;
    private String overlayStyle = "";
    /** 聊天泡泡那一層，各家泡泡的位置用 CSS 變數告訴它。 */
    private HTMLElement chatLayer;
    private String chatStyle = "";
    private double frameNow;
    /** 擲骰子開始的時間，發牌在 DICE_MS 之後。0 表示沒有在演。 */
    private double dealStart;
    /** 這局開門的骰子，牌牆照它畫。 */
    private Dice dice;
    /** 上一個演過的事件。摸牌緊接在補花或槓之後就是從牌尾補的。 */
    private RoundEvent lastEvent;
    /** 各座位報聽動畫開始的時間，0 表示沒有在演。 */
    private final double[] listenStart = new double[4];
    /** 專家模式：棄牌每家只畫最後一張，中央不寫剩幾張。 */
    private boolean expert;
    /** 各家上次打出牌時牌河的張數。之後少了就是那張被拿走了，專家模式就不再畫它。 */
    private final int[] shownDiscards = new int[4];
    /** 最後打出牌的人，這局還沒人打過是 -1。他牌河的最後一張有小標記，被吃碰槓胡走就不畫。 */
    private int lastDiscarder = -1;
    /** 吃碰槓後要打牌，手牌外框亮起。 */
    private boolean discardPrompt;
    private double promptStart;
    /** 流局時牌牆剩下的牌，從牌頭到牌尾；沒有流局或舊服務器沒有送時是空的。revealStart 是開始翻的時間。 */
    private List<Tile> revealTiles = List.of();
    private double revealStart;
    private boolean repaintRequested;
    private boolean hitsStale;
    private int width;
    private int height;

    TableCanvas(Consumer<Tile> onDiscard) {
        this.onDiscard = onDiscard;
        host.appendChild(canvas);
        // 手機轉向或網址列伸縮時，視窗事件可能比版面先到，所以直接看容器的大小
        Browser.onResize(host, this::repaint);
        canvas.addEventListener("mousemove", event -> updateHover((MouseEvent) event));
        canvas.addEventListener("mouseleave", event -> {
            hover = -1;
            requestFrame();
        });
        canvas.addEventListener("touchstart", event -> {
            touched = true;
            raisedBeforeTouch = hover;
        });
        canvas.addEventListener("mousedown", event -> {
            boolean touch = touched;
            touched = false;
            updateHover((MouseEvent) event);
            if (hover < 0 || hover >= hits.size()) {
                return;
            }
            // 手機上牌很小，點一下只抬起來，再點一次同一張才打出，免得打錯。選牌時能點的牌不多，一次就好
            if (touch && pickable == null && hover != raisedBeforeTouch) {
                return;
            }
            (pickable != null ? onPick : this.onDiscard).accept(hits.get(hover).tile());
        });
    }

    HTMLElement element() {
        return host;
    }

    /** 動作列。手機橫向時它蓋在牌桌上，提示與按鈕要放在牌桌留給它們的地方。 */
    void setOverlay(HTMLElement overlay) {
        this.overlay = overlay;
    }

    /** 聊天泡泡那一層，蓋在牌桌上，畫的時候把各家的位置寫給它。 */
    void setChatLayer(HTMLElement layer) {
        this.chatLayer = layer;
    }

    void setRound(RoundView round, Dice dice) {
        this.round = round;
        this.dice = dice;
        flights.clear();
        banners.clear();
        Arrays.fill(listenStart, 0);
        Arrays.fill(shownDiscards, 0);
        lastDiscarder = -1;
        revealTiles = List.of();
        discardPrompt = false;
        dealStart = 0;
        lastEvent = null;
        repaint();
    }

    /**
     * 流局：牌牆剩下的牌（服務器 roundEnd 的 wall）在牌桌中間翻開。animated 時等「流局」的大字出來一下再一張一張翻，
     * 不然直接全部翻開。留到下一局開始才收掉。
     */
    void revealWall(List<Tile> tiles, boolean animated) {
        revealTiles = List.copyOf(tiles);
        frameNow = Browser.now();
        revealStart = animated ? frameNow + REVEAL_DELAY_MS : frameNow - 3_600_000;
        repaint();
    }

    /** 流局翻牌從開始到可以跳出結果要多久；count 是牌牆剩下幾張，沒有牌可翻是 0。 */
    static int drawnRevealMs(int count) {
        return count == 0 ? 0 : REVEAL_DELAY_MS + count * REVEAL_INTERVAL_MS + REVEAL_TILE_MS + REVEAL_HOLD_MS;
    }

    /** 流局的牌還沒翻完，還沒開始翻也算。 */
    private boolean revealing() {
        return !revealTiles.isEmpty() && frameNow - revealStart < revealTiles.size() * REVEAL_INTERVAL_MS + REVEAL_TILE_MS;
    }

    /** 專家模式：棄牌每家只畫最後一張，畫在牌河的第一格，看不出打了幾張；中央不寫剩幾張。 */
    void setExpert(boolean on) {
        expert = on;
        repaint();
    }

    /**
     * 有沒有動畫都會收到。打出牌時記下牌河的張數與是誰打的，之後少了就是被拿走了，專家模式就不再畫它，
     * 最後打出那張的小三角也不畫。重連或接回時還沒記到，各家打出下一張才看得到。
     */
    void noteEvent(RoundEvent event) {
        if (event.type() == RoundEvent.Type.DISCARD && round != null && event.seat() >= 0) {
            shownDiscards[event.seat()] = round.player(event.seat()).discards().size();
            lastDiscarder = event.seat();
        }
    }

    /** 吃碰槓之後要打一張牌：自己的手牌外框一明一暗，直到打出牌。 */
    void setDiscardPrompt(boolean on) {
        if (on == discardPrompt) {
            return;
        }
        discardPrompt = on;
        promptStart = Browser.now();
        frameNow = promptStart;
        repaint();
    }

    /** 先擲骰子，再從牌牆一批一批發牌。牌局其實已經發好了，這裡只是讓牌一批一批出現。 */
    void animateDeal() {
        dealStart = Browser.now();
        frameNow = dealStart;
        repaint();
    }

    /** 從擲骰子開始過了多久。 */
    private double diceElapsedMs() {
        return frameNow - dealStart;
    }

    /** 發牌開始了多久，還在擲骰子時是負的。 */
    private double dealElapsedMs() {
        return diceElapsedMs() - DICE_MS;
    }

    /** 擲骰子與發牌都算。 */
    private boolean dealing() {
        return dealStart != 0 && dealElapsedMs() < DEAL_MS;
    }

    /** 手牌第 index 張是第幾批發的。每家每批四張，莊家先拿，最後一批是莊家的第 17 張。 */
    private int dealPacket(int seat, int index) {
        return index >= 16 ? DEAL_PACKETS - 1 : index / 4 * 4 + Math.floorMod(seat - round.dealer(), 4);
    }

    /** 手牌第 index 張是牌頭的第幾張。 */
    private int dealtFrom(int seat, int index) {
        return index >= 16 ? DEALT_TILES - 1 : dealPacket(seat, index) * 4 + index % 4;
    }

    /** 手牌第 index 張的發牌進度：小於 0 還沒發，0 到 1 飛行中，1 以上已落地。 */
    private double dealProgress(int seat, int index) {
        if (!dealing()) {
            return 1;
        }
        return (dealElapsedMs() - dealPacket(seat, index) * DEAL_INTERVAL_MS) / DEAL_FLIGHT_MS;
    }

    /** 還沒發出去的張數，讓中央的剩餘張數與牌牆跟著往下數。 */
    private int undealtTiles() {
        if (!dealing()) {
            return 0;
        }
        if (dealElapsedMs() < 0) {
            return DEALT_TILES;
        }
        int started = (int) (dealElapsedMs() / DEAL_INTERVAL_MS) + 1;
        return started >= DEAL_PACKETS ? 0 : (DEAL_PACKETS - 1 - started) * 4 + 1;
    }

    /** 這局的牌牆。沒有骰子時是 null。 */
    private WallRing ring() {
        return dice == null ? null : new WallRing(round.dealer(), dice);
    }

    /** 牌牆上看得到的：發牌時牌頭一批一批少下去，發完才補花，所以牌尾等發完才少。 */
    private WallRing.Taken wallTaken() {
        return dealing() ? new WallRing.Taken(DEALT_TILES - undealtTiles(), 0) : WallRing.taken(round);
    }

    private WallLayout wallLayout() {
        double unit = tableUnit();
        double top = wallTop() * unit;
        double depth = (wide ? WIDE_WALL_DEPTH : WALL_DEPTH) * unit;
        double stackW = (top * 2 + depth) / WallRing.STACKS_PER_SIDE;
        double lift = depth * .24;
        return new WallLayout(top, depth, stackW, stackW * .9, depth - lift, lift);
    }

    /** 牌牆離中心多遠，單位是 tableUnit。 */
    private double wallTop() {
        return wide ? WIDE_WALL_TOP : WALL_TOP;
    }

    /** 第 stack 墩的中心點，以牌桌中心為原點。 */
    private Point stackCenter(int stack) {
        WallLayout wall = wallLayout();
        return toTable(WallRing.side(stack), wall.stackX(WallRing.column(stack)) + wall.tileW() / 2,
                wall.top() + wall.depth() / 2);
    }

    /** 播放事件的動畫。只影響畫面，牌局狀態已經更新好了。 */
    void animate(RoundEvent event) {
        syncSize();
        if (round == null || isEmpty()) {
            return;
        }
        // 摸牌與補花緊接在打牌之後，不能把還在飛的牌清掉
        if (event.type() != RoundEvent.Type.DRAW && event.type() != RoundEvent.Type.FLOWER) {
            flights.clear();
        }
        switch (event.type()) {
            case DRAW:
                addDrawFlight(event);
                break;
            case FLOWER:
                addFlowerFlight(event);
                break;
            case DISCARD:
                addDiscardFlight(event);
                break;
            case CHOW:
                addClaim(event, "吃");
                break;
            case PONG:
                addClaim(event, "碰");
                break;
            case KONG:
                addClaim(event, "槓");
                break;
            case CONCEALED_KONG:
                addSeatBanner(event.seat(), "暗槓");
                break;
            case ADD_KONG:
                addSeatBanner(event.seat(), "加槓");
                break;
            case WIN:
            case SELF_WIN:
                addWinBanner(event.type() == RoundEvent.Type.SELF_WIN);
                break;
            case LISTEN:
                addSeatBanner(event.seat(), "聽牌");
                listenStart[event.seat()] = Browser.now();
                break;
            default:
                addCenterBanner("流局", null, Theme.MUTED, Style.PLAIN, WIN_BANNER_MS);
                break;
        }
        lastEvent = event;
        frameNow = Browser.now();
        repaint();
    }

    /** 這次摸牌是從牌尾補的：同一家剛補花或開槓。 */
    private boolean fromTail(RoundEvent draw) {
        if (lastEvent == null || lastEvent.seat() != draw.seat()) {
            return false;
        }
        switch (lastEvent.type()) {
            case FLOWER:
            case KONG:
            case CONCEALED_KONG:
            case ADD_KONG:
                return true;
            default:
                return false;
        }
    }

    /** 連線時結果在胡牌的事件之前就有了，所以這時候已經知道台數。靠花牌胡的直接顯示八仙過海或七搶一。 */
    private void addWinBanner(boolean selfDraw) {
        RoundResult result = round.result();
        Style style = result == null ? (selfDraw ? Style.SELF_DRAW : Style.WIN) : endStyle(result);
        String text = result != null && result.isFlowerWin() ? result.score().items().get(0).name() : selfDraw ? "自摸" : "胡";
        String detail = style == Style.BIG_WIN ? result.score().totalTai() + " 台" : null;
        addCenterBanner(text, detail, Theme.GOLD, style, durationMs(style));
    }

    /** 一局結束的大字演多久，結果視窗等它演完才跳出來。 */
    static int endBannerMs(RoundResult result) {
        return durationMs(endStyle(result));
    }

    private static Style endStyle(RoundResult result) {
        if (result == null || result.isDrawnGame()) {
            return Style.PLAIN;
        }
        if (result.isBigWin()) {
            return Style.BIG_WIN;
        }
        return result.isSelfDraw() ? Style.SELF_DRAW : Style.WIN;
    }

    private static int durationMs(Style style) {
        switch (style) {
            case SELF_DRAW: return SELF_DRAW_BANNER_MS;
            case BIG_WIN:   return BIG_WIN_BANNER_MS;
            default:        return WIN_BANNER_MS;
        }
    }

    /** 局面變了，要重畫。下一幀之前點牌的話，會先重畫才判斷點到哪一張。 */
    void repaint() {
        hitsStale = true;
        requestFrame();
    }

    /** 同一個畫面更新週期只畫一次。分頁在背景時瀏覽器不會更新畫面，回到前景才畫。 */
    private void requestFrame() {
        if (!repaintRequested) {
            repaintRequested = true;
            Window.requestAnimationFrame(time -> {
                repaintRequested = false;
                paint();
            });
        }
    }

    private void paint() {
        syncSize();
        double now = Browser.now();
        frameNow = now;
        hitsStale = false;
        flights.removeIf(flight -> flight.finished(now));
        banners.removeIf(banner -> banner.progress(now) >= 1);
        boolean listenAnimating = false;
        for (int seat = 0; seat < listenStart.length; seat++) {
            if (listenStart[seat] != 0 && listenProgress(seat) >= 1) {
                listenStart[seat] = 0;
            }
            listenAnimating |= listenStart[seat] != 0;
        }
        paintComponent();
        if (!flights.isEmpty() || !banners.isEmpty() || dealing() || listenAnimating || discardPrompt || revealing()) {
            requestFrame();
        }
    }

    /** 報聽動畫的進度，0 到 1；沒有在演時是 1。 */
    private double listenProgress(int seat) {
        return listenStart[seat] == 0 ? 1 : (frameNow - listenStart[seat]) / LISTEN_BADGE_MS;
    }

    /** 畫布跟著容器走，實際像素要乘上螢幕的倍率，高解析度螢幕才不會糊。 */
    private void syncSize() {
        int nextW = host.getClientWidth();
        int nextH = host.getClientHeight();
        wide = Browser.isLandscapeCompact();
        compact = Browser.isCompact();
        double ratio = pixelRatio();
        if (nextW != width || nextH != height || canvas.getWidth() != (int) (nextW * ratio)) {
            width = nextW;
            height = nextH;
            canvas.setWidth((int) (nextW * ratio));
            canvas.setHeight((int) (nextH * ratio));
        }
        discardScale = computeDiscardScale();
    }

    private static double pixelRatio() {
        return Math.min(MAX_PIXEL_RATIO, Math.max(1, Window.current().getDevicePixelRatio()));
    }

    /** 大視窗和 Swing 版一樣以 720 為基準；手機照比例縮會小到看不清楚，所以小螢幕的基準也跟著變小。 */
    private double unit() {
        double shortSide = Math.min(width, height);
        return Math.max(shortSide / 720.0, Math.min(shortSide / 480.0, .8));
    }

    /**
     * 中央方框與棄牌區跟著牌放大，但至少要留得下三排棄牌，不能撞到自己的手牌。
     * 橫向時用滿空間：上下兩家的兩排棄牌放得進牌桌中心到手牌之間，左右兩家的六排與花牌放得進左右兩邊。
     */
    private double tableUnit() {
        double unit = unit();
        if (wide) {
            double across = sideRoom() / (WIDE_DISCARD_TOP + WIDE_ROWS_ACROSS * DISCARD_H + 3);
            double side = (width / 2.0 - botRowDepth()) / (WIDE_DISCARD_TOP + WIDE_ROWS_SIDE * DISCARD_H + FLOWER_H + 10);
            return Math.max(unit * .5, Math.min(across, side));
        }
        double room = (height / 2.0 - humanRowDepth()) / unit;
        double fit = room / (DISCARD_TOP + DISCARD_ROWS_TO_FIT * DISCARD_H);
        return unit * Math.max(1, Math.min(Math.min(Theme.tileScale(), MAX_TABLE_SCALE), fit));
    }

    private double preferredTileW(int seat) {
        double base = (seat == 0 ? 54 : 32) * unit();
        if (seat == 0 && wide) {
            // 橫向時右邊沒有動作列，手牌排滿整個寬度，但不能高到擠掉棄牌
            base = Math.max(base, Math.min(width * .98 / MIN_ROW_SLOTS, height * WIDE_HAND_HEIGHT / TILE_RATIO));
        }
        return base * Theme.tileScale();
    }

    /** 牌桌中心。橫向時上面對家的牌比較薄、下面自己的手牌比較厚，中心往上挪，上下兩家的棄牌才一樣大。 */
    private double centerX() {
        return width / 2.0;
    }

    private double centerY() {
        return wide ? botRowDepth() + sideRoom() : height / 2.0;
    }

    /** 橫向時牌桌中心到上下兩家手牌的距離。 */
    private double sideRoom() {
        return Math.max(0, (height - humanRowDepth() - botRowDepth()) / 2);
    }

    /** 直向的手機寬度不夠，但高度用不完，所以手牌分兩排，牌才夠大。 */
    private boolean twoLineHand() {
        return height > width * 1.2 && width * .98 / MIN_ROW_SLOTS < TWO_LINES_BELOW_STEP;
    }

    /** 分兩排時，第一排放幾格。 */
    private static double firstLineSlots(double slots) {
        return Math.ceil(Math.max(slots, MIN_ROW_SLOTS) / 2);
    }

    private double rowStep(int seat, double slots) {
        if (seat == 0 && twoLineHand()) {
            return Math.min(preferredTileW(0), width * .98 / (firstLineSlots(slots) + LINE_MARGIN_SLOTS));
        }
        return Math.min(preferredTileW(seat), rowLength(seat) * .98 / Math.max(slots, MIN_ROW_SLOTS));
    }

    /** 自己的手牌佔滿整個寬度，其他三家排在剩下的空間裡，牌放大時才不會互相蓋住。 */
    private double rowLength(int seat) {
        if (seat == 0) {
            return width;
        }
        if (seat == 2) {
            return width - 2 * botRowDepth();
        }
        return height - humanRowDepth() - botRowDepth();
    }

    private double humanRowDepth() {
        double tileH = rowTileW(0, MIN_ROW_SLOTS) * TILE_RATIO;
        return (twoLineHand() ? tileH * 2 + handLineGap() : tileH) + 8 * unit();
    }

    private double handLineGap() {
        return 4 * unit();
    }

    private double botRowDepth() {
        return preferredTileW(1) * TILE_RATIO + 8 * unit();
    }

    /** 左右兩家的牌往上挪，避開比較厚的自家手牌。座標方向相反，所以兩家的正負號也相反。 */
    private double rowShift(int seat) {
        // 橫向時牌桌中心已經挪到兩者中間
        if (seat % 2 == 0 || wide) {
            return 0;
        }
        double shift = (humanRowDepth() - botRowDepth()) / 2;
        return seat == 1 ? shift : -shift;
    }

    private double rowTileW(int seat, double slots) {
        if (seat == 0 && twoLineHand()) {
            return rowStep(0, slots);
        }
        return Math.min(preferredTileW(seat), rowStep(seat, slots) * MAX_OVERLAP);
    }

    /** 牌桌中心到這家那一邊的距離。 */
    private double halfDepth(int seat) {
        if (seat % 2 == 1) {
            return width / 2.0;
        }
        if (!wide) {
            return height / 2.0;
        }
        return seat == 0 ? height - centerY() : centerY();
    }

    private static double seatAngle(int seat) {
        return -Math.PI / 2 * seat;
    }

    /** 把該家視角的座標轉成以牌桌中心為原點的座標。 */
    private static Point toTable(int seat, double x, double y) {
        double angle = seatAngle(seat);
        return new Point(x * Math.cos(angle) - y * Math.sin(angle), x * Math.sin(angle) + y * Math.cos(angle));
    }

    /**
     * 這家的棄牌每排幾張。一般都是 8 張；手機橫向時上下兩家、直向時左右兩家的深度只排得下一兩排，
     * 就讓每排多放幾張，起點不變，只往該家的右手邊延伸，第一張才不會離中央很遠。
     * 另一個方向的兩家維持 8 張，彼此才不會撞在一起。
     */
    private int discardColumns(int seat) {
        double unit = tableUnit();
        double rowDepth = seat == 0 ? humanRowDepth() : botRowDepth();
        double room = halfDepth(seat) - rowDepth - DISCARD_TOP * unit;
        int rows = Math.max(1, (int) (room / discardH()));
        if (rows >= DISCARD_ROWS_TO_FIT) {
            return DISCARD_COLUMNS;
        }
        double tileW = discardW();
        double halfLength = (seat % 2 == 0 ? width : height) / 2.0;
        double nearestRow = Math.max(humanRowDepth(), botRowDepth()) + (FLOWER_H + 6) * unit;
        int fits = (int) ((halfLength - nearestRow + tileW * DISCARD_COLUMNS / 2) / tileW);
        return Math.max(DISCARD_COLUMNS, Math.min((DISCARDS_TO_FIT + rows - 1) / rows, fits));
    }

    /** 這家的棄牌每排幾張。橫向時上下兩家 12 張、左右兩家 4 張。 */
    private int discardColumnsFor(int seat) {
        if (wide) {
            return seat % 2 == 0 ? WIDE_COLUMNS_ACROSS : WIDE_COLUMNS_SIDE;
        }
        return discardColumns(seat);
    }

    /** 第一張棄牌的左邊，該家視角。橫向時每排置中；平常起點固定，張數多時往右延伸。 */
    private double discardLeft(int seat, double tileW) {
        if (wide) {
            return -tileW * discardColumnsFor(seat) / 2;
        }
        return pileLeft(tileW, discardTop(tableUnit()));
    }

    /**
     * 一排 8 張的棄牌左端，該家視角。原本置中；放大後半排比 top（棄牌離中心的距離）寬，就往這家的左手邊挪，
     * 右端剛好到 top。四家像風車一樣各佔一個角，相鄰兩家的棄牌在角落才不會疊在一起。
     */
    private static double pileLeft(double tileW, double top) {
        return Math.min(-tileW * DISCARD_COLUMNS / 2, top - tileW * DISCARD_COLUMNS);
    }

    private double discardTop(double unit) {
        return (wide ? WIDE_DISCARD_TOP : DISCARD_TOP) * unit;
    }

    /** 棄牌一張的寬，電腦版地方夠時放大。 */
    private double discardW() {
        return DISCARD_W * tableUnit() * discardScale;
    }

    private double discardH() {
        return DISCARD_H * tableUnit() * discardScale;
    }

    /**
     * 電腦版的棄牌放大幾倍：四家都要從 DISCARD_TOP 排得下三排、不超過 discardReach，最寬到 MAX_DISCARD_W，
     * 放不下就照原本的大小。中央方框、牌牆與手牌不變。手機的兩種版面先不放大。
     */
    private double computeDiscardScale() {
        if (compact || wide || isEmpty()) {
            return 1;
        }
        double k = tableUnit();
        double top = DISCARD_TOP * k;
        double rows = DISCARD_ROWS_TO_FIT * DISCARD_H * k;
        double scale = MAX_DISCARD_W / DISCARD_W;
        for (int seat = 0; seat < 4; seat++) {
            double reach = discardReach(seat, pileLeft(DISCARD_W * k * scale, top));
            scale = Math.min(scale, (reach - top) / rows);
        }
        return Math.max(1, scale);
    }

    /**
     * 從牌桌中心往這家的方向，棄牌最遠排到哪裡：手牌前面留一點空，自己的手牌還要留抬起來與外框發光的地方。
     * 花牌從手牌左端往右排，最多八張，排得到棄牌左端（pileLeft，該家視角）的話，再讓出花牌那一排。
     * 手牌照最少的格數算，花牌的起點最靠右。
     */
    private double discardReach(int seat, double pileLeft) {
        double u = unit();
        double k = tableUnit();
        double reach = halfDepth(seat) - (seat == 0 ? humanRowDepth() + 8 * u : botRowDepth() + 4 * u);
        double rowX = -rowStep(seat, MIN_HAND_SLOTS) * MIN_HAND_SLOTS / 2 + rowShift(seat);
        if (flowersX(seat, rowX) + MAX_FLOWERS * FLOWER_W * k + 4 * k > pileLeft) {
            reach -= (FLOWER_H + 6) * k;
        }
        return reach;
    }

    /** 這家第 index 張棄牌的中心點，該家視角。 */
    private Point discardCenter(int seat, int index) {
        double unit = tableUnit();
        double w = discardW();
        double h = discardH();
        int columns = discardColumnsFor(seat);
        return new Point(discardLeft(seat, w) + (index % columns) * w + w / 2,
                discardTop(unit) + (index / columns) * h + h / 2);
    }

    /** 打出的牌從手牌飛到棄牌區。真人從點的那張牌起飛，電腦從手牌中間起飛。 */
    private void addDiscardFlight(RoundEvent event) {
        int seat = event.seat();
        RowLayout row = layoutRow(seat, null);
        double fromX = 0;
        double fromY = row.y() + row.tileH() / 2;
        double fromW = row.tileW();
        if (seat == 0) {
            for (HandHit hit : hits) {
                if (hit.tile().equals(event.tile())) {
                    fromX = hit.centerX() - centerX();
                    fromY = hit.y() + hit.h() / 2 - centerY();
                }
            }
        }
        // 專家模式只畫最後一張，放在第一格
        int index = expert ? 0 : round.player(seat).discards().size() - 1;
        Point to = discardCenter(seat, index);
        Point from = toTable(seat, fromX, fromY);
        Point target = toTable(seat, to.x(), to.y());
        double w = discardW() - 1;
        flights.add(new Flight(event.tile(), false, from.x(), from.y(), seatAngle(seat), fromW,
                target.x(), target.y(), seatAngle(seat), w, Browser.now(),
                DISCARD_FLIGHT_MS + DISCARD_SHOW_MS + DISCARD_SETTLE_MS, Slot.DISCARD, seat, -1, showcase(seat)));
    }

    /**
     * 打出的牌亮在哪裡：打牌那家面前、牌桌中心到他手牌的中間，正著擺，每一家打的都轉成給自己看的方向。
     * 牌大約是棄牌的 3 倍寬；沿著那家方向的長度（上下兩家是牌高、左右兩家是牌寬）不超過中間空地的八成。
     * 放得下的話不蓋到中央方框與牌牆，直向手機的左右兩家空地窄，才不會整張壓在方框上。
     */
    private Showcase showcase(int seat) {
        double unit = tableUnit();
        double room = halfDepth(seat) - (seat == 0 ? humanRowDepth() : botRowDepth());
        double ratio = DISCARD_H / DISCARD_W;
        double alongAxis = seat % 2 == 0 ? ratio : 1;
        double w = Math.min(DISCARD_W * SHOW_SCALE * unit, room * SHOW_ROOM / alongAxis);
        w = Math.max(w, DISCARD_W * SHOW_MIN_SCALE * unit);
        double extent = w * alongAxis;
        double inner = wallTop() * unit + extent / 2 + 4 * unit;
        double outer = room - extent / 2 - 4 * unit;
        double distance = inner <= outer ? Math.min(Math.max(room * .5, inner), outer) : room * .5;
        Point at = toTable(seat, 0, distance);
        double turn = Math.IEEEremainder(-seatAngle(seat), Math.PI * 2);
        return new Showcase(at.x(), at.y(), w, turn);
    }

    /** 被吃碰槓的牌從棄牌區飛進新面子，同時跳出大字。 */
    private void addClaim(RoundEvent event, String text) {
        int seat = event.seat();
        Player player = round.player(seat);
        Meld meld = player.melds().get(player.melds().size() - 1);
        int tileIndex = meld.tiles().indexOf(event.tile());
        RowLayout row = layoutRow(seat, separatedTile(seat, false));
        // 面子從左邊排起，新的排在最後面
        double meldX = row.x();
        for (Meld earlier : player.melds().subList(0, player.melds().size() - 1)) {
            meldX += earlier.tiles().size() * row.step() + row.gap();
        }

        Point slot = discardCenter(event.from(), expert ? 0 : round.player(event.from()).discards().size());
        Point from = toTable(event.from(), slot.x(), slot.y());
        double tileX = meldX + tileIndex * row.step();
        Point to = toTable(seat, row.placeX(tileX) + row.tileW() / 2, row.placeY(tileX) + row.tileH() / 2);
        double turn = Math.IEEEremainder(seatAngle(seat) - seatAngle(event.from()), Math.PI * 2);
        flights.add(new Flight(event.tile(), false, from.x(), from.y(), seatAngle(event.from()),
                discardW() - 1, to.x(), to.y(), seatAngle(event.from()) + turn, row.tileW(),
                Browser.now(), CLAIM_FLIGHT_MS, Slot.MELD, seat, tileIndex, null));
        addSeatBanner(seat, text);
    }

    /**
     * 摸到的牌從牌牆飛到手牌右邊：一般從牌頭，補花與開槓之後從牌尾。別家的牌背朝上。
     * 局面已經更新，剛拿走的就是牌頭或牌尾的最後一張。
     */
    private void addDrawFlight(RoundEvent event) {
        int seat = event.seat();
        Player player = round.player(seat);
        RowLayout row = layoutRow(seat, event.tile());
        double x = row.x();
        for (Meld meld : player.melds()) {
            x += meld.tiles().size() * row.step() + row.gap();
        }
        x += (player.concealed().size() - 1) * row.step() + row.step() * .4;
        Point to = toTable(seat, row.placeX(x) + row.tileW() / 2, row.placeY(x) + row.tileH() / 2);
        Point from = new Point(0, 0);
        double fromAngle = seatAngle(seat);
        double fromW = row.tileW() * .6;
        WallRing ring = ring();
        WallRing.Taken taken = WallRing.taken(round);
        boolean tail = fromTail(event);
        int index = (tail ? taken.tail() : taken.head()) - 1;
        if (ring != null && index >= 0) {
            int stack = tail ? ring.tailStack(index) : ring.headStack(index);
            from = stackCenter(stack);
            fromAngle = seatAngle(WallRing.side(stack));
            fromW = wallLayout().tileW();
        }
        double toAngle = fromAngle + Math.IEEEremainder(seatAngle(seat) - fromAngle, Math.PI * 2);
        flights.add(new Flight(event.tile(), seat != 0, from.x(), from.y(), fromAngle, fromW,
                to.x(), to.y(), toAngle, row.tileW(), Browser.now(), DRAW_FLIGHT_MS,
                Slot.DRAWN, seat, -1, null));
    }

    /** 花牌從手牌亮出來，飛到該家的花牌列，旁邊跳出小一點的字。補的牌接著從牌尾飛過來。 */
    private void addFlowerFlight(RoundEvent event) {
        int seat = event.seat();
        int index = round.player(seat).flowers().size() - 1;
        RowLayout row = layoutRow(seat, separatedTile(seat, false));
        double middle = row.x() + Math.min(row.total(), row.wrapAt()) / 2;
        Point from = toTable(seat, middle, row.y() + row.tileH() / 2);
        Point to;
        double toAngle = seatAngle(seat);
        double toW;
        if (wide) {
            // 橫向時花牌在名字旁邊，正著擺
            NameStrip strip = nameStrip(seat);
            to = new Point(strip.flowerX(index) + strip.flowerW() / 2 - centerX(),
                    strip.flowerY() + strip.flowerH() / 2 - centerY());
            toAngle += Math.IEEEremainder(-toAngle, Math.PI * 2);
            toW = strip.flowerW();
        } else {
            FlowerRow flowers = flowerRow(seat, row);
            to = toTable(seat, flowers.x() + index * flowers.step() + flowers.w() / 2, flowers.y() + flowers.h() / 2);
            toW = flowers.w();
        }
        flights.add(new Flight(event.tile(), false, from.x(), from.y(), seatAngle(seat), row.tileW(),
                to.x(), to.y(), toAngle, toW - 1, Browser.now(), FLOWER_FLIGHT_MS,
                Slot.FLOWER, seat, index, null));
        addSeatBanner(seat, "補花", 34 * unit(), FLOWER_BANNER_MS);
    }

    private void addSeatBanner(int seat, String text) {
        addSeatBanner(seat, text, 58 * unit(), CLAIM_BANNER_MS);
    }

    /** 不是牌局事件的大字，例如有人補位上場。畫面座位，0 是自己。 */
    void showSeatBanner(int seat, String text) {
        addSeatBanner(seat, text);
        repaint();
    }

    private void addSeatBanner(int seat, String text, double size, int durationMs) {
        Point at = toTable(seat, 0, halfDepth(seat) * .62);
        banners.add(new Banner(text, null, at.x(), at.y(), size, Theme.GOLD, Style.PLAIN, Browser.now(), durationMs));
    }

    private void addCenterBanner(String text, String detail, String color, Style style, int durationMs) {
        banners.clear();
        double size = (style == Style.BIG_WIN ? 140 : 120) * unit();
        banners.add(new Banner(text, detail, 0, 0, size, color, style, Browser.now(), durationMs));
    }

    /** 這個位置的牌是不是還在飛。 */
    private boolean flying(Slot slot, int seat, int index) {
        for (Flight flight : flights) {
            if (flight.slot() == slot && flight.slotSeat() == seat && flight.slotIndex() == index
                    && flight.progress(frameNow) < 1) {
                return true;
            }
        }
        return false;
    }

    /** 回到一般狀態，同時結束選牌。 */
    void setDiscardEnabled(boolean enabled) {
        discardEnabled = enabled;
        pickable = null;
        picked = null;
        markPickable = false;
        hover = -1;
        repaint();
    }

    /**
     * 開始選牌：只有 tiles 裡的牌能點，其他手牌蓋上遮罩。first 是已經選好、要留著不蓋的牌。
     * marked 時能點的牌另外加上金框。
     */
    void startPick(Set<Tile> tiles, Tile first, Consumer<Tile> onPick, boolean marked) {
        discardEnabled = false;
        pickable = tiles;
        picked = first;
        markPickable = marked;
        this.onPick = onPick;
        hover = -1;
        repaint();
    }

    private boolean clickable(Tile tile) {
        return pickable != null ? pickable.contains(tile) : discardEnabled;
    }

    private void updateHover(MouseEvent event) {
        // 點擊範圍是畫的時候記下的，局面剛變還沒重畫的話會點到舊的牌，服務器就不接受了
        if (hitsStale) {
            paint();
        }
        double x = event.getClientX() - canvas.getBoundingClientRect().getLeft();
        double y = event.getClientY() - canvas.getBoundingClientRect().getTop();
        int next = -1;
        for (int i = 0; i < hits.size(); i++) {
            if (hits.get(i).contains(x, y)) {
                next = clickable(hits.get(i).tile()) ? i : -1;
                break;
            }
        }
        if (next != hover) {
            hover = next;
            requestFrame();
        }
    }

    /** 牌桌已經從畫面拿掉、但還有一幀沒畫時，或視窗壓得太扁時，大小是 0，座標都算不出來。 */
    private boolean isEmpty() {
        return width == 0 || height == 0;
    }

    private void paintComponent() {
        if (isEmpty()) {
            return;
        }
        double ratio = pixelRatio();
        Gfx g = new Gfx(context, ratio);
        paintFelt(g);
        if (round != null) {
            double cx = centerX();
            double cy = centerY();
            hits.clear();
            double[] base = g.transform();
            for (int seat = 0; seat < 4; seat++) {
                g.setTransform(base);
                g.translate(cx, cy);
                g.rotate(-Math.PI / 2 * seat);
                paintSeat(g, seat);
            }
            g.setTransform(base);
            if (wide) {
                paintNameStrips(g);
                syncOverlay();
            }
            syncChatAnchors();
            g.translate(cx, cy);
            paintCenterText(g, tableUnit());
            paintDice(g, tableUnit());
            paintReveal(g, tableUnit());
            for (Flight flight : flights) {
                paintFlight(g, flight);
            }
            for (Banner banner : banners) {
                paintBanner(g, banner);
            }
        }
    }

    /**
     * 流局：牌桌中間一塊深色金框的板子，牌牆剩下的牌先蓋著排好，再照牌頭到牌尾的順序一張一張翻開，
     * 剛翻開的那張有金框。照 Swing 版的 paintReveal 畫。原點在牌桌中心；板子太寬時縮到放得進牌桌。
     */
    private void paintReveal(Gfx g, double tableUnit) {
        if (revealTiles.isEmpty()) {
            return;
        }
        double ms = frameNow - revealStart;
        if (ms < 0) {
            return;
        }
        int count = revealTiles.size();
        int columns = Math.min(REVEAL_COLUMNS, count);
        int rows = (count + columns - 1) / columns;
        double unit = Math.min(tableUnit, width * .94 / (columns * 37 + 24));
        double w = 34 * unit;
        double h = w * TILE_RATIO;
        double gap = 3 * unit;
        double pad = 12 * unit;
        double titleSize = 17 * unit;
        double titleH = titleSize * 1.33 + 5 * unit;
        double gridW = columns * w + (columns - 1) * gap;
        double gridH = rows * h + (rows - 1) * gap;
        double boxW = gridW + pad * 2;
        double boxH = gridH + titleH + pad * 2;
        double savedAlpha = g.alpha();
        g.alpha(savedAlpha * Math.min(1, ms / 200));
        g.color("rgba(10,26,20,.933)");
        g.fillRoundRect(-boxW / 2, -boxH / 2, boxW, boxH, 14 * unit);
        g.color(Theme.GOLD);
        g.stroke(Math.max(1.5, 2 * unit));
        g.drawRoundRect(-boxW / 2, -boxH / 2, boxW, boxH, 14 * unit);
        g.font(Theme.fixedFont(true, titleSize));
        g.drawCentered("流局・牌牆剩下的 " + count + " 張", 0, -boxH / 2 + pad + titleSize * .665);
        int latest = (int) Math.min(count - 1, Math.floor(ms / REVEAL_INTERVAL_MS));
        boolean running = ms < count * REVEAL_INTERVAL_MS + REVEAL_TILE_MS + REVEAL_HOLD_MS / 2.0;
        double[] base = g.transform();
        for (int i = 0; i < count; i++) {
            double cx = -gridW / 2 + (i % columns) * (w + gap) + w / 2;
            double top = -boxH / 2 + pad + titleH + (i / columns) * (h + gap);
            double t = Math.min(1, (ms - i * REVEAL_INTERVAL_MS) / REVEAL_TILE_MS);
            // 前半蓋著往中間縮成一條線，後半翻成正面再展開
            boolean face = t >= .5;
            double squeeze = t <= 0 ? 1 : Math.max(.04, Math.abs(1 - 2 * t));
            g.translate(cx, top);
            g.scale(squeeze, 1);
            if (face) {
                TilePainter.paintFace(g, revealTiles.get(i), -w / 2, 0, w, h);
                if (running && i == latest) {
                    TilePainter.paintHighlight(g, -w / 2, 0, w, h, Theme.GOLD);
                }
            } else {
                TilePainter.paintBack(g, -w / 2, 0, w, h);
            }
            g.setTransform(base);
        }
        g.alpha(savedAlpha);
    }

    private void paintFelt(Gfx g) {
        double radius = Math.max(1, Math.max(width, height) * .7);
        CanvasGradient gradient = context.createRadialGradient(width / 2.0, height / 2.0, 0, width / 2.0, height / 2.0, radius);
        gradient.addColorStop(0, Theme.FELT);
        gradient.addColorStop(1, Theme.FELT_DARK);
        context.setFillStyle(gradient);
        context.fillRect(0, 0, width, height);
    }

    private void paintFlight(Gfx g, Flight flight) {
        double t = flight.progress(frameNow);
        if (t >= 1) {
            if (flight.ringsOnLanding()) {
                paintLandingRing(g, flight, t);
            }
            return;
        }
        if (flight.show() != null) {
            paintShowcaseFlight(g, flight);
            return;
        }
        double eased = easeOut(Math.max(0, t));
        double x = flight.fromX() + (flight.toX() - flight.fromX()) * eased;
        double y = flight.fromY() + (flight.toY() - flight.fromY()) * eased;
        double w = (flight.fromW() + (flight.toW() - flight.fromW()) * eased) * (1 + .2 * Math.sin(Math.PI * t));
        // 自己摸到的牌一路站著飛進手牌，比例也和手牌一樣，落地時樣子不會跳
        boolean standing = flight.slot() == Slot.DRAWN && flight.slotSeat() == 0 && !flight.faceDown();
        double h = w * (standing ? TILE_RATIO : DISCARD_H / DISCARD_W);
        double[] saved = g.transform();
        g.translate(x, y);
        g.rotate(flight.fromAngle() + (flight.toAngle() - flight.fromAngle()) * eased);
        if (flight.faceDown()) {
            TilePainter.paintBack(g, -w / 2, -h / 2, w, h);
        } else if (standing) {
            TilePainter.paintStanding(g, flight.tile(), -w / 2, -h / 2, w, h);
        } else {
            TilePainter.paintFace(g, flight.tile(), -w / 2, -h / 2, w, h);
        }
        g.setTransform(saved);
    }

    /**
     * 打出的牌：從手牌飛到亮牌的位置，一邊轉正一邊放大到比亮牌再大一點；停下來時彈回原本大小，
     * 外面一圈金光淡入淡出；最後縮小、轉回打牌那家的方向，飛進牌河。
     */
    private void paintShowcaseFlight(Gfx g, Flight flight) {
        Showcase show = flight.show();
        double elapsed = Math.max(0, frameNow - flight.start());
        double upright = flight.fromAngle() + show.turn();
        double x;
        double y;
        double angle;
        double w;
        double glow;
        if (elapsed < DISCARD_FLIGHT_MS) {
            double p = easeOut(elapsed / DISCARD_FLIGHT_MS);
            x = flight.fromX() + (show.x() - flight.fromX()) * p;
            y = flight.fromY() + (show.y() - flight.fromY()) * p;
            angle = flight.fromAngle() + show.turn() * p;
            w = flight.fromW() + (show.w() * 1.12 - flight.fromW()) * p;
            glow = 0;
        } else if (elapsed < DISCARD_FLIGHT_MS + DISCARD_SHOW_MS) {
            double q = (elapsed - DISCARD_FLIGHT_MS) / DISCARD_SHOW_MS;
            x = show.x();
            y = show.y();
            angle = upright;
            w = show.w() * (1.12 - .12 * easeOut(Math.min(1, q * 2.5)));
            glow = Math.sin(Math.PI * q);
        } else {
            double r = Math.min(1, (elapsed - DISCARD_FLIGHT_MS - DISCARD_SHOW_MS) / DISCARD_SETTLE_MS);
            double p = easeInOut(r);
            x = show.x() + (flight.toX() - show.x()) * p;
            y = show.y() + (flight.toY() - show.y()) * p;
            angle = upright + (flight.toAngle() - upright) * p;
            w = show.w() + (flight.toW() - show.w()) * p;
            glow = 0;
        }
        double h = w * DISCARD_H / DISCARD_W;
        double[] saved = g.transform();
        double savedAlpha = g.alpha();
        g.translate(x, y);
        g.rotate(angle);
        // 陰影讓牌浮在牌桌上
        double shadow = w * .06;
        g.color("rgba(0,0,0,.35)");
        g.fillRoundRect(-w / 2 + shadow, -h / 2 + shadow, w, h, w * .3);
        if (glow > 0) {
            double pad = w * .1;
            for (int layer = 2; layer >= 0; layer--) {
                g.color(withAlpha(Theme.GOLD, glow * (layer == 0 ? .9 : .3 / layer)));
                g.stroke(Math.max(1.5, w * (.05 + layer * .06)));
                g.drawRoundRect(-w / 2 - pad / 2, -h / 2 - pad / 2, w + pad, h + pad, w * .3);
            }
        }
        TilePainter.paintFace(g, flight.tile(), -w / 2, -h / 2, w, h);
        g.setTransform(saved);
        g.alpha(savedAlpha);
    }

    private static double easeInOut(double t) {
        return t < .5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
    }

    private void paintLandingRing(Gfx g, Flight flight, double t) {
        double ring = Math.min(1, (t - 1) * flight.durationMs() / RING_MS);
        double radius = flight.toW() * (.7 + .8 * ring);
        double saved = g.alpha();
        g.alpha(.7 * (1 - ring));
        g.color(Theme.GOLD);
        g.stroke(Math.max(1, flight.toW() * .08));
        g.drawEllipse(flight.toX() - radius, flight.toY() - radius, radius * 2, radius * 2);
        g.alpha(saved);
    }

    /**
     * 大字先放大再彈回原本大小，最後淡出，照 Swing 版的 paintBanner。
     * 胡：金色的圈往外擴散；自摸：金光加上慢慢轉的金紅光芒；大胡：紅金閃光、更多更長的光芒、往外噴再落下的彩片，
     * 紅底的框裡多一行台數，字放大後會跳動。
     */
    private void paintBanner(Gfx g, Banner banner) {
        double t = Math.min(1, Math.max(0, banner.progress(frameNow)));
        boolean big = banner.style() == Style.BIG_WIN;
        double size = banner.size();
        double pop;
        if (big) {
            pop = t < .12 ? 2 - easeOut(t / .12) : 1 + .04 * Math.sin((t - .12) * Math.PI * 8) * (1 - t);
        } else {
            pop = t < .14 ? 1.7 - .7 * easeOut(t / .14) : 1;
        }
        double fadeFrom = big ? .8 : .7;
        double alpha = t > fadeFrom ? (1 - t) / (1 - fadeFrom) : 1;
        double[] savedTransform = g.transform();
        double savedAlpha = g.alpha();
        g.translate(banner.x(), banner.y());
        g.alpha(alpha);
        switch (banner.style()) {
            case WIN:
                paintRings(g, size, t);
                break;
            case SELF_DRAW:
                paintGlow(g, size, t);
                paintRays(g, size, t, 16, Math.PI / 3, Theme.RED);
                break;
            case BIG_WIN:
                paintFlash(g, size, t);
                paintRays(g, size, t, 24, Math.PI / 2, "#FFFFFF");
                paintSparks(g, size, t);
                break;
            default:
                break;
        }
        g.scale(pop, pop);
        paintBox(g, banner, big);
        g.setTransform(savedTransform);
        g.alpha(savedAlpha);
    }

    /** 字和外框。大胡是紅色漸層底，字下面再寫台數。字高照 Swing 的 FontMetrics 抓字型大小的 1.33 倍。 */
    private static void paintBox(Gfx g, Banner banner, boolean big) {
        double size = banner.size();
        double lineH = size * 1.33;
        double detailSize = size * .36;
        double detailH = 0;
        g.font(Theme.fixedFont(true, size));
        double textW = g.textWidth(banner.text());
        if (banner.detail() != null) {
            g.font(Theme.fixedFont(true, detailSize));
            textW = Math.max(textW, g.textWidth(banner.detail()));
            detailH = detailSize * 1.33;
        }
        double padX = size * .35;
        double padY = size * .14;
        double boxH = lineH + detailH + padY * 2;
        double top = -boxH / 2;
        if (big) {
            g.gradient(0, top, BIG_WIN_TOP, 0, -top, BIG_WIN_BOTTOM);
        } else {
            g.color("rgba(11,30,24,.843)");
        }
        g.fillRoundRect(-textW / 2 - padX, top, textW + padX * 2, boxH, size * .3);
        g.color(banner.color());
        g.stroke(Math.max(big ? 2 : 1.5, size * (big ? .06 : .04)));
        g.drawRoundRect(-textW / 2 - padX, top, textW + padX * 2, boxH, size * .3);
        g.font(Theme.fixedFont(true, size));
        g.drawCentered(banner.text(), 0, top + padY + lineH / 2);
        if (banner.detail() != null) {
            g.font(Theme.fixedFont(true, detailSize));
            g.color("#FFFFFF");
            g.drawCentered(banner.detail(), 0, top + padY + lineH + detailH / 2);
        }
    }

    /** 胡：三個金色的圈接連往外擴散，越外面越細越淡。 */
    private static void paintRings(Gfx g, double size, double t) {
        for (int ring = 0; ring < 3; ring++) {
            double r = (t - ring * .12) / .55;
            if (r <= 0 || r >= 1) {
                continue;
            }
            double radius = size * (.55 + 2.0 * easeOut(r));
            g.color(withAlpha(Theme.GOLD, .85 * (1 - r)));
            g.stroke(Math.max(1.5, size * .07 * (1 - r)));
            g.drawEllipse(-radius, -radius, radius * 2, radius * 2);
        }
    }

    /** 自摸：中央一團往外變大的金光。 */
    private static void paintGlow(Gfx g, double size, double t) {
        double radius = size * (1 + 1.6 * easeOut(Math.min(1, t * 1.6)));
        g.fillRadial(radius, new double[] {0, 1}, new String[] {withAlpha(Theme.GOLD, .55), withAlpha(Theme.GOLD, 0)});
    }

    /** 大胡：一開始整片紅金閃一下。 */
    private static void paintFlash(Gfx g, double size, double t) {
        if (t >= .3) {
            return;
        }
        double strength = .6 * (1 - t / .3);
        g.fillRadial(size * 4, new double[] {0, .5, 1},
                new String[] {withAlpha(Theme.GOLD, strength), withAlpha(Theme.RED, strength / 2), withAlpha(Theme.RED, 0)});
    }

    /**
     * 從中央往外放射、慢慢轉的光芒。單數的光芒短一點，顏色用 second。turn 是整段時間轉幾弧度。
     * 自摸 16 道、大胡 24 道，大胡的更長。
     */
    private static void paintRays(Gfx g, double size, double t, int count, double turn, String second) {
        boolean big = count > 16;
        double spread = easeOut(Math.min(1, t * (big ? 1.4 : 1.6)));
        double inner = size * (big ? .7 + 1.0 * spread : .6 + 1.2 * spread);
        double outer = size * (big ? 1.1 + 3.0 * spread : .9 + 2.4 * spread);
        g.roundStroke(Math.max(2, size * (big ? .06 : .05)));
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2 * i / count + t * turn;
            double reach = i % 2 == 0 ? outer : inner + (outer - inner) * (big ? .6 : .55);
            g.color(i % 2 == 0 ? Theme.GOLD : second);
            g.drawLine(Math.cos(angle) * inner, Math.sin(angle) * inner, Math.cos(angle) * reach, Math.sin(angle) * reach);
        }
    }

    /** 大胡：彩片照黃金角往四面噴出去，遠近不一，再慢慢往下掉、邊轉邊淡。 */
    private static void paintSparks(Gfx g, double size, double t) {
        String[] colors = {Theme.GOLD, Theme.RED, "#FFFFFF"};
        double travel = easeOut(Math.min(1, t * 1.3));
        double drop = size * 1.2 * t * t;
        double side = size * .11 * (1 - .4 * t);
        double[] saved = g.transform();
        for (int i = 0; i < SPARKS; i++) {
            double angle = i * GOLDEN_ANGLE;
            double reach = .55 + .45 * ((i * .618034) % 1);
            double distance = size * 3.2 * reach * travel;
            g.translate(Math.cos(angle) * distance, Math.sin(angle) * distance + drop);
            g.rotate(angle + t * 8);
            g.color(withAlpha(colors[i % colors.length], 1 - .8 * t));
            g.fillRect(-side / 2, -side / 2, side, side);
            g.setTransform(saved);
        }
    }

    /** "#RRGGBB" 換成帶透明度的 rgba()。取到千分位，太小的數字 Java 會寫成 1.0E-4，CSS 看不懂。 */
    private static String withAlpha(String hex, double alpha) {
        int rgb = Integer.parseInt(hex.substring(1), 16);
        double clamped = Math.round(Math.max(0, Math.min(1, alpha)) * 1000) / 1000.0;
        return "rgba(" + (rgb >> 16 & 255) + "," + (rgb >> 8 & 255) + "," + (rgb & 255) + "," + clamped + ")";
    }

    private static double easeOut(double t) {
        double rest = 1 - t;
        return 1 - rest * rest * rest;
    }

    private void paintSeat(Gfx g, int seat) {
        Player player = round.player(seat);
        double tableUnit = tableUnit();
        // 橫向時名字不在中央方框四邊，另外標在各家旁邊
        if (!wide) {
            paintSeatLabel(g, seat, player, tableUnit);
        }
        paintWall(g, seat);
        paintDiscards(g, seat, player, tableUnit);
        paintRow(g, player, seat);
    }

    /**
     * 這家面前的牌牆，該家視角，照 Swing 版。拿走的墩空出來，只剩下面那張的墩比較暗。
     * 擲完骰子到發牌開始後一下子，開門的地方有一條金線。
     */
    private void paintWall(Gfx g, int seat) {
        WallRing ring = ring();
        if (ring == null) {
            return;
        }
        WallLayout wall = wallLayout();
        WallRing.Taken taken = wallTaken();
        for (int column = 0; column < WallRing.STACKS_PER_SIDE; column++) {
            int left = ring.remaining(WallRing.stack(seat, column), taken.head(), taken.tail());
            if (left == 0) {
                continue;
            }
            double x = wall.stackX(column);
            double y = wall.top() + wall.lift();
            TilePainter.paintBack(g, x, y, wall.tileW(), wall.tileH());
            g.color("rgba(0,0,0,.314)");
            g.fillRoundRect(x, y, wall.tileW(), wall.tileH(), wall.tileW() * .3);
            if (left == 2) {
                TilePainter.paintBack(g, x, wall.top(), wall.tileW(), wall.tileH());
            }
        }
        double mark = openMarkAlpha();
        if (mark > 0 && WallRing.side(ring.start()) == seat) {
            double unit = tableUnit();
            double x = wall.stackX(WallRing.column(ring.start())) + wall.tileW() + (wall.stackW() - wall.tileW()) / 2;
            double saved = g.alpha();
            g.alpha(saved * mark);
            g.color(Theme.GOLD);
            g.roundStroke(Math.max(2, 3 * unit));
            g.drawLine(x, wall.top() - 5 * unit, x, wall.top() + wall.depth() + 5 * unit);
            g.alpha(saved);
        }
    }

    /** 開門金線的透明度：骰子停下來才出現，發牌開始後慢慢淡掉。 */
    private double openMarkAlpha() {
        if (dealStart == 0) {
            return 0;
        }
        double t = diceElapsedMs();
        if (t < DICE_ROLL_MS) {
            return 0;
        }
        double fadeFrom = DICE_MS + OPEN_MARK_MS / 2.0;
        return t < fadeFrom ? 1 : Math.max(0, 1 - (t - fadeFrom) / (OPEN_MARK_MS / 2.0));
    }

    /** 骰子還看得到的時候是 1，發牌開始後淡出，之後是 0。 */
    private double diceAlpha() {
        if (dealStart == 0 || dice == null) {
            return 0;
        }
        double t = diceElapsedMs();
        return t <= DICE_MS ? 1 : Math.max(0, 1 - (t - DICE_MS) / DICE_FADE_MS);
    }

    /**
     * 三顆骰子從莊家面前邊轉邊跳到中央，滾動時點數一直換，停下來就是這局的點數，下面跳出總點數。
     * 1 點與 4 點是紅色的，和真的骰子一樣。照 Swing 版。
     */
    private void paintDice(Gfx g, double unit) {
        double alpha = diceAlpha();
        if (alpha <= 0) {
            return;
        }
        double t = diceElapsedMs();
        double size = DICE_SIZE * unit;
        double roll = Math.min(1, t / DICE_ROLL_MS);
        double eased = easeOut(roll);
        int[] faces = {dice.first(), dice.second(), dice.third()};
        double savedAlpha = g.alpha();
        g.alpha(savedAlpha * alpha);
        for (int i = 0; i < faces.length; i++) {
            double toX = (i - 1) * size * 1.3;
            double toY = -8 * unit;
            Point from = toTable(round.dealer(), toX * .8, (wallTop() - 20) * unit);
            double x = from.x() + (toX - from.x()) * eased;
            double y = from.y() + (toY - from.y()) * eased;
            double hop = roll < 1 ? Math.abs(Math.sin(Math.PI * (2 + i) * eased)) * (1 - eased) : 0;
            double scale = 1 + .5 * hop;
            double angle = (1 - eased) * Math.PI * (3 + i);
            int face = roll < 1 ? 1 + Math.floorMod((int) (t / 70) * (i + 2) + faces[i] + i * 3, 6) : faces[i];
            double[] saved = g.transform();
            g.translate(x, y);
            g.rotate(angle);
            g.scale(scale, scale);
            paintDie(g, face, size);
            g.setTransform(saved);
        }
        if (t >= DICE_ROLL_MS) {
            double p = Math.min(1, (t - DICE_ROLL_MS) / 180.0);
            double pop = 1.5 - .5 * easeOut(p);
            g.color(Theme.GOLD);
            g.font(Theme.fixedFont(true, 20 * unit * pop));
            g.drawCentered(dice.total() + " 點", 0, 32 * unit);
        }
        g.alpha(savedAlpha);
    }

    /** 一顆骰子，中心在原點：米白圓角方塊加陰影，點是深色，1 與 4 點是紅色。 */
    private static void paintDie(Gfx g, int face, double size) {
        double corner = size * .28;
        double shadow = size * .07;
        g.color("rgba(0,0,0,.353)");
        g.fillRoundRect(-size / 2 + shadow, -size / 2 + shadow, size, size, corner);
        g.color(DIE_FACE);
        g.fillRoundRect(-size / 2, -size / 2, size, size, corner);
        g.color(DIE_EDGE);
        g.stroke(Math.max(1, size * .05));
        g.drawRoundRect(-size / 2, -size / 2, size, size, corner);
        g.color(face == 1 || face == 4 ? Theme.RED : DIE_PIP);
        double r = size * (face == 1 ? .14 : .085);
        double step = size * .26;
        for (double[] pip : PIPS[face]) {
            g.fillEllipse(pip[0] * step - r, pip[1] * step - r, r * 2, r * 2);
        }
    }

    /**
     * 中央方框四邊標示各家門風與姓名，輪到的一家亮起，照 Swing 版。方框跟著座位轉，字不轉：對家的是正的橫寫，
     * 左右兩家是正的直寫、由上往下，四家都不用歪頭看。手機直向時名字後面多寫籌碼。
     */
    private void paintSeatLabel(Gfx g, int seat, Player player, double unit) {
        double half = 100 * unit;
        double barH = 30 * unit;
        boolean active = round.phase() != Round.Phase.FINISHED && round.current() == seat
                && round.phase() == Round.Phase.TURN;
        g.color(active ? Theme.GOLD : "rgba(0,0,0,.353)");
        g.fillRect(-half, half - barH, half * 2, barH);

        String wind = Tile.windName(round.seatWind(seat));
        boolean dealer = round.dealer() == seat;
        String chips = compact ? String.valueOf(player.chips()) : null;
        // 報聽的標記放在該家右手邊那一端、轉角的方格內側，名字往另一邊讓開
        boolean listening = round.isListening(seat);
        double badgeRoom = listening ? 32 * unit : 0;
        double room = half * 2 - 8 * unit - badgeRoom * 2;
        double[] turned = g.transform();
        g.rotate(-seatAngle(seat));
        Point center = toTable(seat, -badgeRoom / 2, half - barH / 2);
        double size = SEAT_LABEL_SIZE * unit;
        g.font(Theme.font(true, size));
        g.color(active ? "#000000" : Theme.TEXT);
        if (seat % 2 == 0) {
            String text = (dealer ? "莊 " : "") + wind + "  " + player.name() + (chips != null ? "  " + chips : "");
            // 字體調大時，長名字要縮回方框內
            double textWidth = g.textWidth(text);
            if (textWidth > room) {
                g.font(Theme.font(true, size * room / textWidth));
            }
            g.drawCentered(text, center.x(), center.y());
        } else {
            List<String> groups = new ArrayList<>();
            if (dealer) {
                groups.add("莊");
            }
            groups.add(wind);
            groups.add(player.name());
            if (chips != null) {
                groups.add(chips);
            }
            // 手機直向多寫籌碼，上下兩家的字長到轉角，直寫的就只用兩個轉角中間那一段，才不會疊在一起
            double columnRoom = chips != null ? room - barH * 2 : room;
            paintColumn(g, groups, center, columnRoom, size);
        }
        if (listening) {
            Point badge = toTable(seat, half - 46 * unit, half - barH / 2);
            paintListenBadge(g, badge.x(), badge.y(), 12.5 * unit, listenProgress(seat));
        }
        g.setTransform(turned);
    }

    /**
     * 直寫：每個字正著由上往下排，中心在 center，groups 之間多空半個字。總長超過 room 就把字縮小。
     * 名字裡的英文字母與數字（還有手機上的籌碼）也一個一個直排，這些窄的字排得緊一點，佔一個中文字的 NARROW_STEP。
     * size 是還沒縮放的字型大小（Theme.font 會再乘字體大小的設定）。
     */
    private static void paintColumn(Gfx g, List<String> groups, Point center, double room, double size) {
        double full = g.textWidth("國");
        // 先照原本的大小量每個字佔多高，放不下再一起縮
        double units = .45 * (groups.size() - 1);
        for (String group : groups) {
            for (int i = 0; i < group.length(); i = group.offsetByCodePoints(i, 1)) {
                units += columnStep(g, group.substring(i, group.offsetByCodePoints(i, 1)), full);
            }
        }
        double fit = Math.min(1, room / (units * full * 1.02));
        if (fit < 1) {
            g.font(Theme.font(true, size * fit));
        }
        double em = full * 1.02 * fit;
        double y = center.y() - units * em / 2;
        for (String group : groups) {
            for (int i = 0; i < group.length(); i = group.offsetByCodePoints(i, 1)) {
                String ch = group.substring(i, group.offsetByCodePoints(i, 1));
                double step = columnStep(g, ch, full * fit) * em;
                // 每個字放在一格 step 高的方格裡，上下置中
                g.drawCentered(ch, center.x(), y + step / 2);
                y += step;
            }
            y += em * .45;
        }
    }

    /** 直寫時窄的字（英文字母、數字）佔一個中文字的這麼多高。 */
    private static final double NARROW_STEP = .78;

    /** 這個字直寫時佔幾個中文字高：寬度不到中文字七成的算窄字。full 是目前字型一個中文字的寬。 */
    private static double columnStep(Gfx g, String ch, double full) {
        return g.textWidth(ch) < full * .7 ? NARROW_STEP : 1;
    }

    /**
     * 報聽的人方框邊上的「聽」：紅底圓形、金色圓框、白色粗體字，照 Swing 版。t 是報聽動畫的進度：
     * 一開始從 2.4 倍大彈回原本大小，同時有兩個金圈往外擴散、變淡，t 到 1 之後就是靜止的標記。
     */
    private static void paintListenBadge(Gfx g, double cx, double cy, double radius, double t) {
        if (t < 1) {
            for (int ring = 0; ring < 2; ring++) {
                double r = (t - ring * .2) / .8;
                if (r <= 0 || r >= 1) {
                    continue;
                }
                double reach = radius * (1 + 3 * easeOut(r));
                g.color(withAlpha(Theme.GOLD, .8 * (1 - r)));
                g.stroke(Math.max(1.5, radius * .25 * (1 - r)));
                g.drawEllipse(cx - reach, cy - reach, reach * 2, reach * 2);
            }
        }
        double scale = t < .35 ? 2.4 - 1.4 * easeOut(t / .35) : 1;
        double r = radius * scale;
        g.color(Theme.RED);
        g.fillEllipse(cx - r, cy - r, r * 2, r * 2);
        g.color(Theme.GOLD);
        g.stroke(Math.max(1.5, r * .16));
        g.drawEllipse(cx - r, cy - r, r * 2, r * 2);
        g.color("#FFFFFF");
        g.font(Theme.fixedFont(true, r * 1.2));
        g.drawCentered("聽", cx, cy);
    }

    private void paintDiscards(Gfx g, int seat, Player player, double unit) {
        double tileW = discardW();
        double tileH = discardH();
        int columns = discardColumnsFor(seat);
        double startX = discardLeft(seat, tileW);
        double startY = discardTop(unit);
        List<Tile> discards = player.discards();
        boolean lastHidden = flying(Slot.DISCARD, seat, -1);
        // 整桌最後打出、還沒被拿走的那張，落進牌河後到下一張打出之前都有小標記；亮牌還在演時不畫
        boolean latest = seat == lastDiscarder && !discards.isEmpty() && discards.size() == shownDiscards[seat]
                && !lastHidden;
        if (expert) {
            // 只畫最後打出、還沒被拿走的那張，放在第一格
            int count = discards.size();
            if (count == 0 || count != shownDiscards[seat] || lastHidden) {
                return;
            }
            TilePainter.paintFace(g, discards.get(count - 1), startX, startY, tileW - 1, tileH - 1);
            if (round.phase() == Round.Phase.CLAIMS && !round.isRobbing() && round.discarder() == seat) {
                TilePainter.paintHighlight(g, startX, startY, tileW - 1, tileH - 1, Theme.RED);
            }
            if (latest) {
                paintLatestMark(g, startX, startY, tileW - 1);
            }
            return;
        }
        for (int i = 0; i < discards.size(); i++) {
            if (lastHidden && i == discards.size() - 1) {
                break;
            }
            double x = startX + (i % columns) * tileW;
            double y = startY + (i / columns) * tileH;
            TilePainter.paintFace(g, discards.get(i), x, y, tileW - 1, tileH - 1);
            boolean justDiscarded = round.phase() == Round.Phase.CLAIMS && !round.isRobbing()
                    && round.discarder() == seat && i == discards.size() - 1;
            if (justDiscarded) {
                TilePainter.paintHighlight(g, x, y, tileW - 1, tileH - 1, Theme.RED);
            }
            if (latest && i == discards.size() - 1) {
                paintLatestMark(g, x, y, tileW - 1);
            }
        }
    }

    /**
     * 最後打出那張牌的標記，照 Swing 版：牌的上緣（靠牌桌中間那一邊）正中間一個朝牌指的紅色小三角，金邊、底下一點影子，
     * 大半壓在牌上，只露出一點，才不會蓋到前一排的牌。跟著該家的方向轉，所以一律從牌桌中間指向那張牌。
     */
    private static void paintLatestMark(Gfx g, double x, double y, double tileW) {
        double size = tileW * .4;
        double cx = x + tileW / 2;
        double[] saved = g.transform();
        g.translate(size * .06, size * .1);
        g.color("rgba(0,0,0,.353)");
        markPath(g, cx, y, size);
        g.context().fill();
        g.setTransform(saved);
        g.color(Theme.DEALER_RED);
        markPath(g, cx, y, size);
        g.context().fill();
        g.color(Theme.GOLD);
        g.roundStroke(Math.max(1, size * .1));
        g.context().stroke();
    }

    /** 小三角的路徑：底邊在牌的上緣外面一點，尖端壓在牌上。 */
    private static void markPath(Gfx g, double cx, double y, double size) {
        CanvasRenderingContext2D path = g.context();
        path.beginPath();
        path.moveTo(cx - size / 2, y - size * .38);
        path.lineTo(cx + size / 2, y - size * .38);
        path.lineTo(cx, y + size * .5);
        path.closePath();
    }

    /** 手牌列的位置。separated 是與手牌分開顯示的那張牌，沒有就傳 null。 */
    private RowLayout layoutRow(int seat, Tile separated) {
        Player player = round.player(seat);
        int meldTiles = player.melds().stream().mapToInt(m -> m.tiles().size()).sum();
        double gapSlots = (separated != null ? .4 : 0) + player.melds().size() * .25;
        double slots = player.concealed().size() + meldTiles + gapSlots;
        double step = rowStep(seat, slots);
        double tileW = rowTileW(seat, slots);
        double tileH = tileW * TILE_RATIO;
        double total = slots * step;
        if (seat == 0 && twoLineHand()) {
            double firstLine = firstLineSlots(slots);
            return new RowLayout(-(firstLine + LINE_MARGIN_SLOTS) * step / 2,
                    halfDepth(0) - tileH * 2 - handLineGap() - 8 * unit(), tileW, tileH, step, step * .25, total,
                    firstLine * step, tileH + handLineGap());
        }
        return new RowLayout(-total / 2 + rowShift(seat), halfDepth(seat) - tileH - 8 * unit(), tileW, tileH, step,
                step * .25, total, Double.POSITIVE_INFINITY, 0);
    }

    private void paintRow(Gfx g, Player player, int seat) {
        boolean finished = round.phase() == Round.Phase.FINISHED;
        boolean faceUp = seat == 0 || finished;

        List<Tile> hand = new ArrayList<>(player.concealed());
        Tile separated = separatedTile(seat, finished);
        if (separated != null) {
            hand.remove(separated);
        }

        RowLayout row = layoutRow(seat, separated);
        double tileW = row.tileW();
        double tileH = row.tileH();
        double step = row.step();
        double gap = row.gap();
        double x = row.x();

        for (int m = 0; m < player.melds().size(); m++) {
            Meld meld = player.melds().get(m);
            boolean newest = m == player.melds().size() - 1;
            for (int i = 0; i < meld.tiles().size(); i++) {
                // 暗槓只亮中間兩張給自己看，其他家全蓋
                boolean hidden = meld.isConcealed() && !finished && (seat != 0 || i == 0 || i == 3);
                if (newest && flying(Slot.MELD, seat, i)) {
                    x += step;
                    continue;
                }
                if (hidden) {
                    TilePainter.paintBack(g, row.placeX(x), row.placeY(x), tileW, tileH);
                } else {
                    TilePainter.paintFace(g, meld.tiles().get(i), row.placeX(x), row.placeY(x), tileW, tileH);
                }
                x += step;
            }
            x += gap;
        }
        int index = 0;
        for (Tile tile : hand) {
            // 被下一張疊住的部分點不到，點擊範圍只算露出來的寬度
            double hitW = index == hand.size() - 1 ? tileW : step;
            paintDealtTile(g, seat, tile, index++, row.placeX(x), row.placeY(x), tileW, tileH, hitW, faceUp);
            x += step;
        }
        if (separated != null) {
            x += step * .4;
            if (!flying(Slot.DRAWN, seat, -1)) {
                paintDealtTile(g, seat, separated, index, row.placeX(x), row.placeY(x), tileW, tileH, tileW, faceUp);
            } else if (seat == 0) {
                // 還在飛也要點得到，真人可以馬上打掉
                addHit(row.placeX(x), row.placeY(x), tileW, tileH, separated);
            }
        }
        if (seat == 0 && discardPrompt && !finished) {
            paintPromptGlow(g, row);
        }
        // 橫向時花牌和名字畫在一起，見 paintNameStrips
        if (!dealing() && !wide) {
            paintFlowers(g, player, flowerRow(seat, row));
        }
    }

    /**
     * 吃碰槓後要打牌：自己整列牌外面一圈金色圓角框，一明一暗，週期 PROMPT_PULSE_MS，照 Swing 版。
     * 外面再疊兩圈比較寬、比較淡的框當作光暈。手牌分兩排時框住兩排。
     */
    private void paintPromptGlow(Gfx g, RowLayout row) {
        double phase = (frameNow - promptStart) / PROMPT_PULSE_MS;
        double strength = .55 + .45 * Math.sin(Math.PI * 2 * phase);
        double pad = 6 * unit();
        boolean wrapped = row.total() > row.wrapAt();
        double w = wrapped ? row.wrapAt() : row.total();
        double h = row.tileH() + (wrapped ? row.lineStep() : 0);
        for (int layer = 2; layer >= 0; layer--) {
            g.color(withAlpha(Theme.GOLD, strength * (layer == 0 ? .95 : .25 / layer)));
            g.stroke((3 + layer * 5) * unit());
            g.drawRoundRect(row.x() - pad, row.y() - pad, w + pad * 2, h + pad * 2, 14 * unit());
        }
    }

    /** 花牌列的起點，該家視角。面子多時手牌變寬，花牌要避開隔壁家的手牌。 */
    private double flowersX(int seat, RowLayout row) {
        return flowersX(seat, row.x());
    }

    /** 手牌左端在 rowX 時，花牌列的起點。 */
    private double flowersX(int seat, double rowX) {
        double halfLength = (seat % 2 == 0 ? width : height) / 2.0;
        double inset = Math.max(66 * unit(), botRowDepth() + 6 * unit());
        return Math.max(rowX, -halfLength + inset);
    }

    /** 花牌列，該家視角，排在手牌前面靠左手邊。橫向時花牌改到名字旁邊，見 nameStrip。 */
    private FlowerRow flowerRow(int seat, RowLayout row) {
        double unit = tableUnit();
        double w = FLOWER_W * unit;
        double h = FLOWER_H * unit;
        return new FlowerRow(flowersX(seat, row), row.y() - h - 6 * unit, w, h, w);
    }

    private void addHit(double x, double y, double w, double h, Tile tile) {
        hits.add(new HandHit(x + centerX(), y + centerY(), Math.ceil(w), Math.ceil(h), tile));
    }

    /**
     * 發牌時，牌背朝上從牌牆上拿的那一墩轉著飛過來，落地後才是一般的手牌。原點是牌桌中央，座標是這家的視角。
     * 起飛時和牌牆上的牌一樣大、一樣的方向。
     */
    private void paintDealtTile(Gfx g, int seat, Tile tile, int index, double x, double y, double w, double h,
                                double hitW, boolean faceUp) {
        double progress = dealProgress(seat, index);
        if (progress >= 1) {
            paintHandTile(g, seat, tile, x, y, w, h, hitW, faceUp);
        } else if (progress >= 0) {
            double eased = easeOut(progress);
            double fromX = 0;
            double fromY = 0;
            double turn = .9;
            double from = .6;
            WallRing ring = ring();
            if (ring != null) {
                int stack = ring.headStack(dealtFrom(seat, index));
                Point center = stackCenter(stack);
                // 牌桌座標轉回這家的視角
                Point local = toTable(Math.floorMod(-seat, 4), center.x(), center.y());
                fromX = local.x();
                fromY = local.y();
                turn = Math.IEEEremainder(seatAngle(WallRing.side(stack)) - seatAngle(seat), Math.PI * 2);
                from = Math.min(1, wallLayout().tileW() / w);
            }
            double scale = from + (1 - from) * eased;
            double[] saved = g.transform();
            g.translate(fromX + (x + w / 2 - fromX) * eased, fromY + (y + h / 2 - fromY) * eased);
            g.rotate((1 - eased) * turn);
            TilePainter.paintBack(g, -w * scale / 2, -h * scale / 2, w * scale, h * scale);
            g.setTransform(saved);
        }
    }

    /** 剛摸到的牌（或胡的那張）與手牌分開顯示。 */
    private Tile separatedTile(int seat, boolean finished) {
        if (finished) {
            RoundResult result = round.result();
            return !result.isDrawnGame() && !result.isFlowerWin() && result.winner() == seat ? result.winTile() : null;
        }
        return round.phase() == Round.Phase.TURN && round.current() == seat ? round.drawn() : null;
    }

    private void paintHandTile(Gfx g, int seat, Tile tile, double x, double y, double w, double h,
                               double hitW, boolean faceUp) {
        double drawY = y;
        boolean masked = false;
        boolean marked = false;
        if (seat == 0) {
            int index = hits.size();
            // 相同的牌只抬起第一張，看起來才是選了一張
            boolean chosen = tile.equals(picked) && hits.stream().noneMatch(hit -> hit.tile().equals(picked));
            addHit(x, y, hitW, h, tile);
            masked = pickable != null && !pickable.contains(tile) && !chosen;
            marked = markPickable && pickable != null && pickable.contains(tile);
            if (chosen || index == hover) {
                drawY -= h * .16;
            }
        }
        if (drawY < y) {
            // 抬起來的牌在原本的位置留一道影子，看得出是離開桌面了
            g.color("rgba(0,0,0,.216)");
            g.fillRoundRect(x + w * .08, y + h * .8, w * .92, h * .24, w * .3);
        }
        // 自己的手牌是站著的，牌頂畫在原本一張牌的範圍裡，排版不變
        boolean standing = seat == 0 && faceUp;
        if (standing) {
            TilePainter.paintStanding(g, tile, x, drawY, w, h);
        } else if (faceUp) {
            TilePainter.paintFace(g, tile, x, drawY, w, h);
        } else {
            TilePainter.paintBack(g, x, drawY, w, h);
        }
        if (masked) {
            if (standing) {
                TilePainter.paintStandingMask(g, x, drawY, w, h);
            } else {
                TilePainter.paintMask(g, x, drawY, w, h);
            }
        }
        if (marked) {
            if (standing) {
                TilePainter.paintStandingHighlight(g, x, drawY, w, h, Theme.GOLD);
            } else {
                TilePainter.paintHighlight(g, x, drawY, w, h, Theme.GOLD);
            }
        }
    }

    private void paintFlowers(Gfx g, Player player, FlowerRow row) {
        List<Tile> flowers = player.flowers();
        for (int i = 0; i < flowers.size(); i++) {
            if (!flying(Slot.FLOWER, player.seat(), i)) {
                TilePainter.paintFace(g, flowers.get(i), row.x() + i * row.step(), row.y(), row.w() - 1, row.h());
            }
        }
    }

    /** 中央方框的圈風與剩餘張數。擲骰子時骰子在方框上，字先藏起來，骰子淡出時再淡入。 */
    private void paintCenterText(Gfx g, double unit) {
        double half = (wide ? WIDE_BOX_HALF : 70) * unit;
        g.color("rgba(0,0,0,.431)");
        g.fillRoundRect(-half, -half, half * 2, half * 2, 12 * unit);
        double textAlpha = 1 - diceAlpha();
        if (textAlpha <= 0) {
            return;
        }
        double saved = g.alpha();
        g.alpha(saved * textAlpha);
        paintCenterLines(g, unit, half);
        g.alpha(saved);
    }

    /**
     * 圈風局風與剩餘張數；連莊時三行一起往上挪，最下面用紅色粗體寫「連 N 拉 N」，和計分表的「莊」同一個紅。
     * 手機橫向的方框比較小，三行排得比較緊。專家模式不寫剩幾張，照 Swing 版的位置。
     */
    private void paintCenterLines(Gfx g, double unit, double half) {
        int streak = round.streak();
        // 專家模式不寫剩幾張，其他行往中間靠
        double labelY = expert ? (streak == 0 ? 8 : -10) : streak == 0 ? -14 : wide ? -17 : -26;
        double countY = streak == 0 ? 22 : wide ? 5 : 8;
        double streakY = expert ? 24 : wide ? 26 : 40;
        g.color(Theme.GOLD);
        double labelSize = 22 * unit;
        g.font(Theme.font(true, labelSize));
        String label = roundLabel(round);
        // 字體調大時四個字會超出方框，縮回框內
        double labelWidth = g.textWidth(label);
        double maxWidth = half * 2 - 12 * unit;
        if (labelWidth > maxWidth) {
            g.font(Theme.font(true, labelSize * maxWidth / labelWidth));
        }
        drawCenteredLine(g, label, labelY * unit);
        double countSize = 16 * unit;
        if (!expert) {
            g.color(Theme.TEXT);
            String count = "剩餘 " + (round.liveTiles() + undealtTiles()) + " 張";
            drawFittedLine(g, count, false, countSize, maxWidth, countY * unit);
        }
        if (streak > 0) {
            g.color(Theme.DEALER_RED);
            drawFittedLine(g, "連" + streak + "拉" + streak, true, countSize, maxWidth, streakY * unit);
        }
    }

    /** 置中寫一行字，比 maxWidth 寬就縮小到放得下；橫向的方框比較小，字體調大時會超出去。 */
    private static void drawFittedLine(Gfx g, String text, boolean bold, double size, double maxWidth, double y) {
        g.font(Theme.font(bold, size));
        double width = g.textWidth(text);
        if (width > maxWidth) {
            g.font(Theme.font(bold, size * maxWidth / width));
        }
        drawCenteredLine(g, text, y);
    }

    /**
     * 橫向時各家的名字與花牌排成一條，螢幕座標，都避開棄牌：自己的在手牌左上方；對家的在對家手牌下面靠左，
     * 左家的再下面一條；右家的在右上角、選單下面，靠右對齊，花牌排在名字左邊。名字放不下就縮小字，花牌放不下就疊起來。
     */
    private NameStrip nameStrip(int seat) {
        double u = unit();
        double k = tableUnit();
        double side = botRowDepth();
        double gap = 6 * u;
        double cx = centerX();
        double across = -discardLeft(0, DISCARD_W * k);
        double labelH = WIDE_LABEL_H * u;
        double flowerW = FLOWER_W * k;
        double flowerH = FLOWER_H * k;
        double lineH = Math.max(labelH, flowerH);
        double left;
        double right;
        double lineTop;
        if (seat == 1) {
            left = cx + across + gap;
            right = width - side - gap;
        } else {
            left = side + gap;
            right = cx - across - gap;
        }
        if (seat == 0) {
            lineH = flowerH;
            lineTop = height - humanRowDepth() - 6 * k - flowerH;
        } else if (seat == 3) {
            lineTop = side + 3 * u + lineH + 3 * u;
        } else {
            lineTop = side + 3 * u;
        }
        Player player = round.player(seat);
        String text = (round.dealer() == seat ? "莊 " : "") + Tile.windName(round.seatWind(seat)) + "  "
                + player.name() + "  " + player.chips();
        boolean listening = round.isListening(seat);
        double pad = 5 * u;
        double badge = listening ? 24 * u : 0;
        // 名字最多用掉六成，留地方給花牌
        double maxW = Math.max(0, Math.min(WIDE_LABEL_W * u, (right - left) * .62));
        double size = WIDE_LABEL_SIZE * u;
        context.setFont(Theme.font(true, size));
        double textW = context.measureText(text).getWidth();
        double fits = maxW - pad * 2 - badge;
        if (textW > fits) {
            size *= Math.max(0, fits) / textW;
            textW = Math.max(0, fits);
        }
        double w = Math.min(maxW, textW + pad * 2 + badge);
        double x = seat == 1 ? right - w : left;
        int direction = seat == 1 ? -1 : 1;
        double flowerStart = seat == 1 ? x - gap : x + w + gap;
        double room = seat == 1 ? flowerStart - left : right - flowerStart;
        int count = player.flowers().size();
        double step = flowerW;
        if (count > 1 && count * flowerW > room) {
            step = Math.max(flowerW * .35, (room - flowerW) / (count - 1));
        }
        return new NameStrip(x, lineTop + (lineH - labelH) / 2, w, labelH, text, size, listening,
                flowerStart, lineTop + (lineH - flowerH) / 2, flowerW, flowerH, step, direction);
    }

    /** 名字標籤，輪到的一家亮起，報聽的在右端標「聽」；旁邊是花牌，發牌時還沒補花，先不畫。 */
    private void paintNameStrips(Gfx g) {
        double u = unit();
        for (int seat = 0; seat < 4; seat++) {
            NameStrip strip = nameStrip(seat);
            if (!dealing()) {
                List<Tile> flowers = round.player(seat).flowers();
                for (int i = 0; i < flowers.size(); i++) {
                    if (!flying(Slot.FLOWER, seat, i)) {
                        TilePainter.paintFace(g, flowers.get(i), strip.flowerX(i), strip.flowerY(),
                                strip.flowerW() - 1, strip.flowerH());
                    }
                }
            }
            if (strip.w() <= 0 || strip.fontSize() < 4) {
                continue;
            }
            boolean active = round.phase() == Round.Phase.TURN && round.current() == seat;
            g.color(active ? Theme.GOLD : "rgba(0,0,0,.45)");
            g.fillRoundRect(strip.x(), strip.y(), strip.w(), strip.h(), 6 * u);
            g.font(Theme.font(true, strip.fontSize()));
            g.color(active ? "#000000" : Theme.TEXT);
            double badge = strip.listening() ? 24 * u : 0;
            g.drawCentered(strip.text(), strip.x() + (strip.w() - badge) / 2, strip.y() + strip.h() / 2);
            if (strip.listening()) {
                paintListenBadge(g, strip.x() + strip.w() - 13 * u, strip.y() + strip.h() / 2, 9.5 * u,
                        listenProgress(seat));
            }
        }
    }

    /**
     * 橫向時把提示與按鈕的位置寫進動作列的 CSS 變數，螢幕座標，style.css 照著放。
     * 提示在自己手牌左上方、花牌與名字上面；按鈕在右上方，避開右家的手牌與抬起來的手牌。兩邊都不壓到棄牌。
     */
    private void syncOverlay() {
        if (overlay == null) {
            return;
        }
        double u = unit();
        double k = tableUnit();
        double side = botRowDepth();
        double gap = 6 * u;
        double cx = centerX();
        double across = -discardLeft(0, DISCARD_W * k);
        double flowers = (6 + FLOWER_H) * k;
        double lift = rowTileW(0, MIN_ROW_SLOTS) * TILE_RATIO * .16;
        double hintLeft = side + gap;
        double actRight = side + gap;
        double[] values = {hintLeft, humanRowDepth() + flowers + 4 * u, cx - across - gap - hintLeft,
            actRight, humanRowDepth() + lift + 2 * u, width - actRight - (cx + across + gap)};
        StringBuilder key = new StringBuilder();
        for (double value : values) {
            key.append(Math.max(0, Math.round(value))).append(',');
        }
        if (key.toString().equals(overlayStyle)) {
            return;
        }
        overlayStyle = key.toString();
        // 網頁的 CSP 不准用 style 屬性，要經過 CSSOM 設定
        for (int i = 0; i < OVERLAY_VARS.length; i++) {
            overlay.getStyle().setProperty(OVERLAY_VARS[i], Math.max(0, Math.round(values[i])) + "px");
        }
    }

    /**
     * 聊天泡泡的位置，螢幕座標，寫進 CSS 變數 --chat-x0 到 --chat-y3。上下兩家在中心與手牌之間、左右置中；
     * 左右兩家靠近自己的手牌，泡泡往中間長，見 style.css 的 .chat-seat。
     */
    private void syncChatAnchors() {
        if (chatLayer == null) {
            return;
        }
        double[] values = new double[8];
        for (int seat = 0; seat < 4; seat++) {
            Point at = toTable(seat, 0, halfDepth(seat) * (seat % 2 == 0 ? CHAT_ACROSS : CHAT_SIDE));
            values[seat * 2] = centerX() + at.x();
            values[seat * 2 + 1] = centerY() + at.y();
        }
        StringBuilder key = new StringBuilder();
        for (double value : values) {
            key.append(Math.round(value)).append(',');
        }
        if (key.toString().equals(chatStyle)) {
            return;
        }
        chatStyle = key.toString();
        for (int seat = 0; seat < 4; seat++) {
            chatLayer.getStyle().setProperty("--chat-x" + seat, Math.round(values[seat * 2]) + "px");
            chatLayer.getStyle().setProperty("--chat-y" + seat, Math.round(values[seat * 2 + 1]) + "px");
        }
    }

    /** 圈風加局風，例如「東風東局」「南風西局」。舊服務器沒有送局風時是原本的「東風圈」。牌桌中間與右側都用這個。 */
    static String roundLabel(RoundView round) {
        int hand = round.handWind();
        return Tile.windName(round.roundWind()) + "風" + (hand >= 1 && hand <= 4 ? Tile.windName(hand) + "局" : "圈");
    }

    private static void drawCenteredLine(Gfx g, String text, double y) {
        g.drawString(text, -g.textWidth(text) / 2, y);
    }
}
