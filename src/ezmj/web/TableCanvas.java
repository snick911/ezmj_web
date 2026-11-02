package ezmj.web;

import java.util.ArrayList;
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
import ezmj.model.Meld;
import ezmj.model.Player;
import ezmj.model.Tile;

/**
 * 牌桌畫面，版面與動畫時間照 Swing 版的 TableCanvas。四家共用同一套繪製程式：先把座標旋轉到該家視角，
 * 再以「自己坐在下方」的區域座標繪製，所以棄牌會自然排成風車狀。
 */
final class TableCanvas {

    static final int DISCARD_FLIGHT_MS = 190;
    static final int CLAIM_FLIGHT_MS = 240;
    static final int DRAW_FLIGHT_MS = 170;
    private static final int FLOWER_FLIGHT_MS = 260;
    private static final int FLOWER_BANNER_MS = 650;
    static final int WIN_BANNER_MS = 1100;
    /** 發牌每四張一批輪流發，最後一批是莊家的第 17 張。 */
    static final int DEAL_PACKETS = 17;
    static final int DEAL_FLIGHT_MS = 200;
    static final int DEAL_INTERVAL_MS = 70;
    static final int DEAL_MS = (DEAL_PACKETS - 1) * DEAL_INTERVAL_MS + DEAL_FLIGHT_MS;

    private static final int DISCARD_COLUMNS = 8;
    private static final double DISCARD_W = 25;
    private static final double DISCARD_H = 33;
    private static final double DISCARD_TOP = 104;
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

    /** 一家手牌列的位置，都是該家視角的座標。step 是相鄰兩張的間距，比 tileW 小時牌會互相疊住。 */
    private record RowLayout(double x, double y, double tileW, double tileH, double step, double gap, double total) {
    }

    /** 飛行中的牌要去的地方。 */
    private enum Slot { DISCARD, MELD, DRAWN, FLOWER }

    /**
     * 飛行中的一張牌。位置以牌桌中心為原點，指牌的中心點。
     * 飛行時對應的那張牌不畫出來，落地後才出現。slotIndex 是面子或花牌裡的第幾張，其他地方用不到。
     */
    private record Flight(Tile tile, boolean faceDown, double fromX, double fromY, double fromAngle, double fromW,
                          double toX, double toY, double toAngle, double toW, double start, int durationMs,
                          Slot slot, int slotSeat, int slotIndex) {
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

    /** 跳出來的大字。位置以牌桌中心為原點。 */
    private record Banner(String text, double x, double y, double size, String color, boolean burst,
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
    private Consumer<Tile> onPick;
    private int hover = -1;
    private double frameNow;
    private double dealStart;
    private boolean repaintRequested;
    private boolean hitsStale;
    private int width;
    private int height;

    TableCanvas(Consumer<Tile> onDiscard) {
        this.onDiscard = onDiscard;
        host.appendChild(canvas);
        canvas.addEventListener("mousemove", event -> updateHover((MouseEvent) event));
        canvas.addEventListener("mouseleave", event -> {
            hover = -1;
            requestFrame();
        });
        canvas.addEventListener("mousedown", event -> {
            updateHover((MouseEvent) event);
            if (hover >= 0 && hover < hits.size()) {
                (pickable != null ? onPick : this.onDiscard).accept(hits.get(hover).tile());
            }
        });
    }

    HTMLElement element() {
        return host;
    }

    void setRound(RoundView round) {
        this.round = round;
        flights.clear();
        banners.clear();
        dealStart = 0;
        repaint();
    }

    /** 開始發牌動畫。牌局其實已經發好了，這裡只是讓牌一批一批出現。 */
    void animateDeal() {
        dealStart = Browser.now();
        frameNow = dealStart;
        repaint();
    }

    private double dealElapsedMs() {
        return frameNow - dealStart;
    }

    private boolean dealing() {
        return dealStart != 0 && dealElapsedMs() < DEAL_MS;
    }

    /** 手牌第 index 張的發牌進度：小於 0 還沒發，0 到 1 飛行中，1 以上已落地。 */
    private double dealProgress(int seat, int index) {
        if (!dealing()) {
            return 1;
        }
        int packet = index >= 16 ? DEAL_PACKETS - 1 : index / 4 * 4 + Math.floorMod(seat - round.dealer(), 4);
        return (dealElapsedMs() - packet * DEAL_INTERVAL_MS) / DEAL_FLIGHT_MS;
    }

    /** 還沒發出去的張數，讓中央的剩餘張數跟著往下數。 */
    private int undealtTiles() {
        if (!dealing()) {
            return 0;
        }
        int started = (int) (dealElapsedMs() / DEAL_INTERVAL_MS) + 1;
        return started >= DEAL_PACKETS ? 0 : (DEAL_PACKETS - 1 - started) * 4 + 1;
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
                addCenterBanner(winText("胡"), Theme.GOLD, true);
                break;
            case SELF_WIN:
                addCenterBanner(winText("自摸"), Theme.GOLD, true);
                break;
            default:
                addCenterBanner("流局", Theme.MUTED, false);
                break;
        }
        frameNow = Browser.now();
        repaint();
    }

    /** 靠花牌胡的直接顯示八仙過海或七搶一。 */
    private String winText(String normal) {
        RoundResult result = round.result();
        return result.isFlowerWin() ? result.score().items().get(0).name() : normal;
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
        paintComponent();
        if (!flights.isEmpty() || !banners.isEmpty() || dealing()) {
            requestFrame();
        }
    }

    /** 畫布跟著容器走，實際像素要乘上螢幕的倍率，高解析度螢幕才不會糊。 */
    private void syncSize() {
        int nextW = host.getClientWidth();
        int nextH = host.getClientHeight();
        double ratio = pixelRatio();
        if (nextW != width || nextH != height || canvas.getWidth() != (int) (nextW * ratio)) {
            width = nextW;
            height = nextH;
            canvas.setWidth((int) (nextW * ratio));
            canvas.setHeight((int) (nextH * ratio));
        }
    }

    private static double pixelRatio() {
        return Math.max(1, Window.current().getDevicePixelRatio());
    }

    private double unit() {
        return Math.min(width, height) / 720.0;
    }

    /** 中央方框與棄牌區跟著牌放大，但至少要留得下三排棄牌，不能撞到自己的手牌。 */
    private double tableUnit() {
        double unit = unit();
        double handH = rowTileW(0, MIN_ROW_SLOTS) * TILE_RATIO;
        double room = (height / 2.0 - handH - 8 * unit) / unit;
        double fit = room / (DISCARD_TOP + DISCARD_ROWS_TO_FIT * DISCARD_H);
        return unit * Math.max(1, Math.min(Math.min(Theme.tileScale(), MAX_TABLE_SCALE), fit));
    }

    private double preferredTileW(int seat) {
        return (seat == 0 ? 54 : 32) * unit() * Theme.tileScale();
    }

    private double rowStep(int seat, double slots) {
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
        return rowTileW(0, MIN_ROW_SLOTS) * TILE_RATIO + 8 * unit();
    }

    private double botRowDepth() {
        return preferredTileW(1) * TILE_RATIO + 8 * unit();
    }

    /** 左右兩家的牌往上挪，避開比較厚的自家手牌。座標方向相反，所以兩家的正負號也相反。 */
    private double rowShift(int seat) {
        if (seat % 2 == 0) {
            return 0;
        }
        double shift = (humanRowDepth() - botRowDepth()) / 2;
        return seat == 1 ? shift : -shift;
    }

    private double rowTileW(int seat, double slots) {
        return Math.min(preferredTileW(seat), rowStep(seat, slots) * MAX_OVERLAP);
    }

    private double halfDepth(int seat) {
        return (seat % 2 == 0 ? height : width) / 2.0;
    }

    private static double seatAngle(int seat) {
        return -Math.PI / 2 * seat;
    }

    /** 把該家視角的座標轉成以牌桌中心為原點的座標。 */
    private static Point toTable(int seat, double x, double y) {
        double angle = seatAngle(seat);
        return new Point(x * Math.cos(angle) - y * Math.sin(angle), x * Math.sin(angle) + y * Math.cos(angle));
    }

    /** 第 index 張棄牌的中心點，該家視角。 */
    private Point discardCenter(int index) {
        double unit = tableUnit();
        double w = DISCARD_W * unit;
        double h = DISCARD_H * unit;
        return new Point(-w * DISCARD_COLUMNS / 2 + (index % DISCARD_COLUMNS) * w + w / 2,
                DISCARD_TOP * unit + (index / DISCARD_COLUMNS) * h + h / 2);
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
                    fromX = hit.centerX() - width / 2.0;
                }
            }
        }
        int index = round.player(seat).discards().size() - 1;
        Point to = discardCenter(index);
        Point from = toTable(seat, fromX, fromY);
        Point target = toTable(seat, to.x(), to.y());
        double w = (DISCARD_W - 1) * tableUnit();
        flights.add(new Flight(event.tile(), false, from.x(), from.y(), seatAngle(seat), fromW,
                target.x(), target.y(), seatAngle(seat), w, Browser.now(), DISCARD_FLIGHT_MS,
                Slot.DISCARD, seat, -1));
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

        Point slot = discardCenter(round.player(event.from()).discards().size());
        Point from = toTable(event.from(), slot.x(), slot.y());
        Point to = toTable(seat, meldX + tileIndex * row.step() + row.tileW() / 2, row.y() + row.tileH() / 2);
        double turn = Math.IEEEremainder(seatAngle(seat) - seatAngle(event.from()), Math.PI * 2);
        flights.add(new Flight(event.tile(), false, from.x(), from.y(), seatAngle(event.from()),
                (DISCARD_W - 1) * tableUnit(), to.x(), to.y(), seatAngle(event.from()) + turn, row.tileW(),
                Browser.now(), CLAIM_FLIGHT_MS, Slot.MELD, seat, tileIndex));
        addSeatBanner(seat, text);
    }

    /** 摸到的牌從牌桌中央飛到手牌右邊。別家的牌背朝上。 */
    private void addDrawFlight(RoundEvent event) {
        int seat = event.seat();
        Player player = round.player(seat);
        RowLayout row = layoutRow(seat, event.tile());
        double x = row.x();
        for (Meld meld : player.melds()) {
            x += meld.tiles().size() * row.step() + row.gap();
        }
        x += (player.concealed().size() - 1) * row.step() + row.step() * .4;
        Point to = toTable(seat, x + row.tileW() / 2, row.y() + row.tileH() / 2);
        flights.add(new Flight(event.tile(), seat != 0, 0, 0, seatAngle(seat), row.tileW() * .6,
                to.x(), to.y(), seatAngle(seat), row.tileW(), Browser.now(), DRAW_FLIGHT_MS,
                Slot.DRAWN, seat, -1));
    }

    /** 花牌從牌桌中央飛到該家的花牌列，旁邊跳出小一點的字。 */
    private void addFlowerFlight(RoundEvent event) {
        int seat = event.seat();
        int index = round.player(seat).flowers().size() - 1;
        RowLayout row = layoutRow(seat, separatedTile(seat, false));
        double unit = tableUnit();
        double w = FLOWER_W * unit;
        double h = FLOWER_H * unit;
        Point to = toTable(seat, flowersX(seat, row) + index * w + w / 2, row.y() - 6 * unit - h / 2);
        flights.add(new Flight(event.tile(), false, 0, 0, seatAngle(seat), w * .7,
                to.x(), to.y(), seatAngle(seat), w - 1, Browser.now(), FLOWER_FLIGHT_MS,
                Slot.FLOWER, seat, index));
        addSeatBanner(seat, "補花", 34 * unit(), FLOWER_BANNER_MS);
    }

    private void addSeatBanner(int seat, String text) {
        addSeatBanner(seat, text, 58 * unit(), CLAIM_BANNER_MS);
    }

    private void addSeatBanner(int seat, String text, double size, int durationMs) {
        Point at = toTable(seat, 0, halfDepth(seat) * .62);
        banners.add(new Banner(text, at.x(), at.y(), size, Theme.GOLD, false, Browser.now(), durationMs));
    }

    private void addCenterBanner(String text, String color, boolean burst) {
        banners.clear();
        banners.add(new Banner(text, 0, 0, 120 * unit(), color, burst, Browser.now(), WIN_BANNER_MS));
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
        hover = -1;
        repaint();
    }

    /** 開始選牌：只有 tiles 裡的牌能點，其他手牌蓋上遮罩。first 是已經選好、要留著不蓋的牌。 */
    void startPick(Set<Tile> tiles, Tile first, Consumer<Tile> onPick) {
        discardEnabled = false;
        pickable = tiles;
        picked = first;
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
            double cx = width / 2.0;
            double cy = height / 2.0;
            hits.clear();
            double[] base = g.transform();
            for (int seat = 0; seat < 4; seat++) {
                g.setTransform(base);
                g.translate(cx, cy);
                g.rotate(-Math.PI / 2 * seat);
                paintSeat(g, seat);
            }
            g.setTransform(base);
            g.translate(cx, cy);
            paintCenterText(g, tableUnit());
            for (Flight flight : flights) {
                paintFlight(g, flight);
            }
            for (Banner banner : banners) {
                paintBanner(g, banner);
            }
        }
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
        double eased = easeOut(Math.max(0, t));
        double x = flight.fromX() + (flight.toX() - flight.fromX()) * eased;
        double y = flight.fromY() + (flight.toY() - flight.fromY()) * eased;
        double w = (flight.fromW() + (flight.toW() - flight.fromW()) * eased) * (1 + .2 * Math.sin(Math.PI * t));
        double h = w * DISCARD_H / DISCARD_W;
        double[] saved = g.transform();
        g.translate(x, y);
        g.rotate(flight.fromAngle() + (flight.toAngle() - flight.fromAngle()) * eased);
        if (flight.faceDown()) {
            TilePainter.paintBack(g, -w / 2, -h / 2, w, h);
        } else {
            TilePainter.paintFace(g, flight.tile(), -w / 2, -h / 2, w, h);
        }
        g.setTransform(saved);
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

    /** 大字先放大再彈回原本大小，最後淡出。 */
    private void paintBanner(Gfx g, Banner banner) {
        double t = Math.min(1, Math.max(0, banner.progress(frameNow)));
        double pop = t < .14 ? 1.7 - .7 * easeOut(t / .14) : 1;
        double alpha = t > .7 ? (1 - t) / .3 : 1;
        double[] savedTransform = g.transform();
        double savedAlpha = g.alpha();
        g.translate(banner.x(), banner.y());
        g.alpha(alpha);
        if (banner.burst()) {
            paintBurst(g, banner.size(), t);
        }
        g.scale(pop, pop);
        g.font(Theme.fixedFont(true, banner.size()));
        double textW = g.textWidth(banner.text());
        double padX = banner.size() * .35;
        double padY = banner.size() * .14;
        double boxH = banner.size() * 1.33 + padY * 2;
        g.color("rgba(11,30,24,.843)");
        g.fillRoundRect(-textW / 2 - padX, -boxH / 2, textW + padX * 2, boxH, banner.size() * .3);
        g.color(banner.color());
        g.stroke(Math.max(1.5, banner.size() * .04));
        g.drawRoundRect(-textW / 2 - padX, -boxH / 2, textW + padX * 2, boxH, banner.size() * .3);
        g.drawCentered(banner.text(), 0, 0);
        g.setTransform(savedTransform);
        g.alpha(savedAlpha);
    }

    /** 胡牌時從中央往外放射的光芒。 */
    private void paintBurst(Gfx g, double size, double t) {
        double spread = easeOut(Math.min(1, t * 1.6));
        double inner = size * (.6 + 1.2 * spread);
        double outer = size * (.9 + 2.4 * spread);
        g.roundStroke(Math.max(2, size * .05));
        for (int i = 0; i < 16; i++) {
            double angle = Math.PI * 2 * i / 16;
            double reach = i % 2 == 0 ? outer : inner + (outer - inner) * .55;
            g.color(i % 2 == 0 ? Theme.GOLD : Theme.RED);
            g.drawLine(Math.cos(angle) * inner, Math.sin(angle) * inner, Math.cos(angle) * reach, Math.sin(angle) * reach);
        }
    }

    private static double easeOut(double t) {
        double rest = 1 - t;
        return 1 - rest * rest * rest;
    }

    private void paintSeat(Gfx g, int seat) {
        Player player = round.player(seat);
        double tableUnit = tableUnit();
        paintSeatLabel(g, seat, player, tableUnit);
        paintDiscards(g, seat, player, tableUnit);
        paintRow(g, player, seat);
    }

    /** 中央方框四邊標示各家門風與姓名，輪到的一家亮起。 */
    private void paintSeatLabel(Gfx g, int seat, Player player, double unit) {
        double half = 100 * unit;
        boolean active = round.phase() != Round.Phase.FINISHED && round.current() == seat
                && round.phase() == Round.Phase.TURN;
        g.color(active ? Theme.GOLD : "rgba(0,0,0,.353)");
        g.fillRect(-half, half - 30 * unit, half * 2, 30 * unit);

        String wind = Tile.windName(round.seatWind(seat));
        String dealerMark = round.dealer() == seat ? "莊 " : "";
        String text = dealerMark + wind + "  " + player.name();
        g.font(Theme.font(true, 14 * unit));
        g.color(active ? "#000000" : Theme.TEXT);
        // 字體調大時，長名字要縮回方框內
        double textWidth = g.textWidth(text);
        double maxWidth = half * 2 - 8 * unit;
        if (textWidth > maxWidth) {
            g.font(Theme.font(true, 14 * unit * maxWidth / textWidth));
        }
        g.drawCentered(text, 0, half - 15 * unit);
    }

    private void paintDiscards(Gfx g, int seat, Player player, double unit) {
        double tileW = DISCARD_W * unit;
        double tileH = DISCARD_H * unit;
        double startX = -tileW * DISCARD_COLUMNS / 2;
        double startY = DISCARD_TOP * unit;
        List<Tile> discards = player.discards();
        boolean lastHidden = flying(Slot.DISCARD, seat, -1);
        for (int i = 0; i < discards.size(); i++) {
            if (lastHidden && i == discards.size() - 1) {
                break;
            }
            double x = startX + (i % DISCARD_COLUMNS) * tileW;
            double y = startY + (i / DISCARD_COLUMNS) * tileH;
            TilePainter.paintFace(g, discards.get(i), x, y, tileW - 1, tileH - 1);
            boolean justDiscarded = round.phase() == Round.Phase.CLAIMS && !round.isRobbing()
                    && round.discarder() == seat && i == discards.size() - 1;
            if (justDiscarded) {
                TilePainter.paintHighlight(g, x, y, tileW - 1, tileH - 1, Theme.RED);
            }
        }
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
        return new RowLayout(-total / 2 + rowShift(seat), halfDepth(seat) - tileH - 8 * unit(), tileW, tileH, step,
                step * .25, total);
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
        double y = row.y();

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
                    TilePainter.paintBack(g, x, y, tileW, tileH);
                } else {
                    TilePainter.paintFace(g, meld.tiles().get(i), x, y, tileW, tileH);
                }
                x += step;
            }
            x += gap;
        }
        int index = 0;
        for (Tile tile : hand) {
            // 被下一張疊住的部分點不到，點擊範圍只算露出來的寬度
            double hitW = index == hand.size() - 1 ? tileW : step;
            paintDealtTile(g, seat, tile, index++, x, y, tileW, tileH, hitW, faceUp);
            x += step;
        }
        if (separated != null) {
            x += step * .4;
            if (!flying(Slot.DRAWN, seat, -1)) {
                paintDealtTile(g, seat, separated, index, x, y, tileW, tileH, tileW, faceUp);
            } else if (seat == 0) {
                // 還在飛也要點得到，真人可以馬上打掉
                addHit(x, y, tileW, tileH, separated);
            }
        }
        if (!dealing()) {
            paintFlowers(g, player, flowersX(seat, row), y, tableUnit());
        }
    }

    /** 花牌列的起點，該家視角。面子多時手牌變寬，花牌要避開隔壁家的手牌。 */
    private double flowersX(int seat, RowLayout row) {
        double halfLength = (seat % 2 == 0 ? width : height) / 2.0;
        double inset = Math.max(66 * unit(), botRowDepth() + 6 * unit());
        return Math.max(row.x(), -halfLength + inset);
    }

    private void addHit(double x, double y, double w, double h, Tile tile) {
        hits.add(new HandHit(x + width / 2.0, y + height / 2.0, Math.ceil(w), Math.ceil(h), tile));
    }

    /** 發牌時，牌背朝上從牌桌中央轉著飛過來，落地後才是一般的手牌。原點就是牌桌中央。 */
    private void paintDealtTile(Gfx g, int seat, Tile tile, int index, double x, double y, double w, double h,
                                double hitW, boolean faceUp) {
        double progress = dealProgress(seat, index);
        if (progress >= 1) {
            paintHandTile(g, seat, tile, x, y, w, h, hitW, faceUp);
        } else if (progress >= 0) {
            double eased = easeOut(progress);
            double scale = .6 + .4 * eased;
            double[] saved = g.transform();
            g.translate((x + w / 2) * eased, (y + h / 2) * eased);
            g.rotate((1 - eased) * .9);
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
        if (seat == 0) {
            int index = hits.size();
            // 相同的牌只抬起第一張，看起來才是選了一張
            boolean chosen = tile.equals(picked) && hits.stream().noneMatch(hit -> hit.tile().equals(picked));
            addHit(x, y, hitW, h, tile);
            masked = pickable != null && !pickable.contains(tile) && !chosen;
            if (chosen || index == hover) {
                drawY -= h * .16;
            }
        }
        if (faceUp) {
            TilePainter.paintFace(g, tile, x, drawY, w, h);
        } else {
            TilePainter.paintBack(g, x, drawY, w, h);
        }
        if (masked) {
            TilePainter.paintMask(g, x, drawY, w, h);
        }
    }

    private void paintFlowers(Gfx g, Player player, double x, double rowY, double unit) {
        double w = FLOWER_W * unit;
        double h = FLOWER_H * unit;
        double y = rowY - h - 6 * unit;
        List<Tile> flowers = player.flowers();
        for (int i = 0; i < flowers.size(); i++) {
            if (!flying(Slot.FLOWER, player.seat(), i)) {
                TilePainter.paintFace(g, flowers.get(i), x, y, w - 1, h);
            }
            x += w;
        }
    }

    private void paintCenterText(Gfx g, double unit) {
        double half = 70 * unit;
        g.color("rgba(0,0,0,.431)");
        g.fillRoundRect(-half, -half, half * 2, half * 2, 12 * unit);
        g.color(Theme.GOLD);
        g.font(Theme.font(true, 22 * unit));
        drawCenteredLine(g, Tile.windName(round.roundWind()) + "風圈", -14 * unit);
        g.color(Theme.TEXT);
        g.font(Theme.font(false, 16 * unit));
        drawCenteredLine(g, "剩餘 " + (round.liveTiles() + undealtTiles()) + " 張", 22 * unit);
    }

    private static void drawCenteredLine(Gfx g, String text, double y) {
        g.drawString(text, -g.textWidth(text) / 2, y);
    }
}
