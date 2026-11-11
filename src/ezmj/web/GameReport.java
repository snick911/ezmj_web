package ezmj.web;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.teavm.jso.canvas.CanvasGradient;
import org.teavm.jso.canvas.CanvasRenderingContext2D;
import org.teavm.jso.dom.html.HTMLCanvasElement;
import org.teavm.jso.dom.html.HTMLDocument;

import ezmj.engine.GameView;
import ezmj.engine.RoundRecord;
import ezmj.engine.RoundResult;
import ezmj.model.GameRecord;
import ezmj.model.Player;

/**
 * 牌局結束後的結果圖與輸贏歷程 PDF，都畫在 Canvas 上。
 * 結果圖是直的 4:5，照牌桌的配色，給手機分享；PDF 是白底的 A4 橫放，每頁一張圖，一頁放不下就往下一頁接。
 * 房號不寫上去：房間打完還在，私人房的房號傳出去別人就能加入。
 */
final class GameReport {

    static final int IMAGE_W = 1080;
    static final int IMAGE_H = 1350;
    /** A4 橫放，150 dpi。 */
    private static final int PAGE_W = 1754;
    private static final int PAGE_H = 1240;
    private static final int PDF_W = 842;
    private static final int PDF_H = 595;
    private static final double JPEG_QUALITY = .9;
    private static final int SEATS = 4;

    private static final String[] MEDALS = {"#E8C05A", "#CFD8DC", "#D08C4E", "#7C948A"};
    /** 印出來的顏色：贏是紅字、輸是綠字，和看盤的習慣一樣。 */
    private static final String INK = "#1E2A25";
    private static final String GRAY = "#66766F";
    private static final String RULE = "#C9D3CE";
    private static final String STRIPE = "#F3F6F4";
    private static final String HEAD = "#E3EBE7";
    private static final String MINE = "#FBF1D6";
    private static final String WIN = "#B3261E";
    private static final String LOSS = "#1B6E44";

    private static final double MARGIN = 72;
    private static final double BOTTOM = PAGE_H - 80;
    private static final double ROW_H = 64;
    private static final double HEAD_H = 46;
    private static final double RANK_ROW_H = 46;
    /** 每局輸贏的欄寬，加起來是頁寬扣掉左右邊界。依序是局、圈風與莊家、結果、台、四家、我的累計。 */
    private static final double[] ROUND_COLUMNS = {70, 250, 560, 70, 130, 130, 130, 130, 140};
    private static final double[] RANK_COLUMNS = {90, 420, 150, 150, 150, 220, 220};
    private static final double RANK_TOP = 226;
    private static final double FIRST_TABLE_TOP = 514;
    private static final double NEXT_TABLE_TOP = 174;

    private static final String MEDAL_INK = "#1B1B1B";
    private static final String GOLD_FAINT = "rgba(232,192,90,.35)";
    /** 結果圖上成績表的左緣，右邊也留一樣寬。 */
    private static final double SHARE_TABLE_X = 66;
    private static final double SHARE_TABLE_TOP = 372;
    private static final double SHARE_HEAD_H = 76;
    private static final double SHARE_ROW_H = 172;
    /** 自己那一列的金框比表格左右各寬這麼多。 */
    private static final double MINE_OUTSET = 8;
    /** 名次與玩家、胡牌、自摸、放槍、輸贏、籌碼，加起來是圖寬扣掉左右的 SHARE_TABLE_X。 */
    private static final double[] SHARE_COLUMNS = {360, 80, 80, 80, 180, 168};
    private static final String[] SHARE_ALIGNS = {"left", "center", "center", "center", "right", "right"};
    /** 數字放不下時縮到這麼小，比名字縮得多，數字不能截掉。 */
    private static final double SHARE_MIN_NUMBER = 20;

    private final GameView game;
    private final String roomName;
    /** 牌局結束的當地時間，例如 202611062130。 */
    private final String digits = Browser.nowDigits();

    GameReport(GameView game, String roomName) {
        this.game = game;
        this.roomName = roomName;
    }

    /** 例如 2026/11/06 21:30。 */
    String when() {
        return digits.substring(0, 4) + "/" + digits.substring(4, 6) + "/" + digits.substring(6, 8) + " "
                + digits.substring(8, 10) + ":" + digits.substring(10, 12);
    }

    private String stamp() {
        return digits.substring(0, 8) + "-" + digits.substring(8, 12);
    }

    String imageFileName() {
        return "ezmj-結果-" + stamp() + ".png";
    }

    String pdfFileName() {
        return "ezmj-輸贏歷程-" + stamp() + ".pdf";
    }

    /** 分享時附上的一句話。 */
    String shareText() {
        Player[] ranking = FinalDialog.ranking(game);
        int[] ranks = FinalDialog.ranks(ranking);
        for (int i = 0; i < ranking.length; i++) {
            if (ranking[i].seat() == 0) {
                int winnings = game.record(0, "").winnings();
                String money = winnings == 0 ? "不輸不贏" : (winnings > 0 ? "贏 " : "輸 ") + grouped(Math.abs(winnings));
                return "EZMJ 台灣十六張麻將：" + FinalDialog.headline(ranking) + "，我拿第 " + ranks[i] + " 名，" + money + "。";
            }
        }
        return "EZMJ 台灣十六張麻將：" + FinalDialog.headline(ranking) + "。";
    }

    private String infoLine() {
        return "底 " + grouped(game.basePay()) + "／每台 " + grouped(game.taiPay()) + "　共打 " + game.roundsPlayed()
                + " 局，流局 " + game.drawnGames() + " 次";
    }

    // ---- 結果圖 ----

    /** 這一場的成績表，欄位和牌局結束視窗的表格一樣。自己那一列有金色的底、粗金框與金色名字，金框比表格左右各寬 MINE_OUTSET。 */
    HTMLCanvasElement shareImage() {
        HTMLCanvasElement canvas = canvas(IMAGE_W, IMAGE_H);
        CanvasRenderingContext2D context = context(canvas);
        Gfx g = new Gfx(context, 1);
        CanvasGradient felt = context.createRadialGradient(IMAGE_W / 2.0, IMAGE_H * .4, 0, IMAGE_W / 2.0, IMAGE_H * .4,
                IMAGE_H * .8);
        felt.addColorStop(0, Theme.FELT);
        felt.addColorStop(1, Theme.FELT_DARK);
        context.setFillStyle(felt);
        context.fillRect(0, 0, IMAGE_W, IMAGE_H);
        g.color(Theme.GOLD);
        g.roundStroke(4);
        g.drawRoundRect(28, 28, IMAGE_W - 56, IMAGE_H - 56, 40);
        g.color(GOLD_FAINT);
        g.roundStroke(2);
        g.drawRoundRect(42, 42, IMAGE_W - 84, IMAGE_H - 84, 30);

        Player[] ranking = FinalDialog.ranking(game);
        int[] ranks = FinalDialog.ranks(ranking);
        double middle = IMAGE_W / 2.0;
        g.color(Theme.GOLD);
        text(context, "EZMJ 台灣十六張麻將", true, 58, 40, IMAGE_W - 160, middle, 128, "center");
        g.color(Theme.TEXT);
        text(context, FinalDialog.headline(ranking), true, 42, 28, IMAGE_W - 180, middle, 205, "center");
        g.color(Theme.MUTED);
        text(context, infoLine(), false, 28, 20, IMAGE_W - 180, middle, 262, "center");
        text(context, when() + (roomName.isEmpty() ? "" : "　" + roomName), false, 28, 20, IMAGE_W - 180, middle, 304,
                "center");

        paintShareTable(g, ranking, ranks);
        g.color(Theme.MUTED);
        text(context, "胡牌次數包含自摸", false, 26, 20, IMAGE_W - 180, middle, shareRowTop(SEATS) + 44, "center");
        text(context, Browser.siteAddress(), false, 26, 18, IMAGE_W - 180, middle, IMAGE_H - 82, "center");
        return canvas;
    }

    /** 結果圖上第 index 列（0 是第一名）的上緣；index 是 4 時是表格的下緣。 */
    private static double shareRowTop(int index) {
        return SHARE_TABLE_TOP + SHARE_HEAD_H + index * SHARE_ROW_H;
    }

    private void paintShareTable(Gfx g, Player[] ranking, int[] ranks) {
        CanvasRenderingContext2D context = g.context();
        double width = sum(SHARE_COLUMNS);
        g.color("rgba(0,0,0,.30)");
        g.fillRoundRect(SHARE_TABLE_X, SHARE_TABLE_TOP, width, shareRowTop(SEATS) - SHARE_TABLE_TOP, 30);
        String[] heads = {"名次　玩家", "胡牌", "自摸", "放槍", "輸贏", "籌碼"};
        g.color(Theme.MUTED);
        for (int i = 0; i < heads.length; i++) {
            cell(context, heads[i], true, 28, 20, SHARE_TABLE_X, SHARE_COLUMNS, i, SHARE_ALIGNS[i],
                    SHARE_TABLE_TOP + SHARE_HEAD_H / 2);
        }
        g.color(GOLD_FAINT);
        g.fillRect(SHARE_TABLE_X, shareRowTop(0) - 1, width, 2);
        for (int i = 0; i < ranking.length; i++) {
            // 自己那一列有金框，上下就不畫分隔線
            if (i + 1 < ranking.length && ranking[i].seat() != 0 && ranking[i + 1].seat() != 0) {
                g.color("rgba(255,255,255,.10)");
                g.fillRect(SHARE_TABLE_X + 20, shareRowTop(i + 1) - 1, width - 40, 2);
            }
            paintShareRow(g, ranking[i], ranks[i], shareRowTop(i));
        }
    }

    private void paintShareRow(Gfx g, Player player, int rank, double top) {
        CanvasRenderingContext2D context = g.context();
        boolean mine = player.seat() == 0;
        GameRecord record = game.record(player.seat(), "");
        double middle = top + SHARE_ROW_H / 2;
        if (mine) {
            double x = SHARE_TABLE_X - MINE_OUTSET;
            double w = sum(SHARE_COLUMNS) + MINE_OUTSET * 2;
            g.color("rgba(232,192,90,.22)");
            g.fillRoundRect(x, top + 6, w, SHARE_ROW_H - 12, 28);
            g.color(Theme.GOLD);
            g.roundStroke(5);
            g.drawRoundRect(x, top + 6, w, SHARE_ROW_H - 12, 28);
        }

        double cx = SHARE_TABLE_X + 12 + 36;
        g.color(MEDALS[Math.min(rank, MEDALS.length) - 1]);
        g.fillEllipse(cx - 36, middle - 36, 72, 72);
        g.color(MEDAL_INK);
        text(context, String.valueOf(rank), true, 42, 42, 64, cx, middle + 2, "center");

        double nameX = cx + 36 + 20;
        double nameMax = SHARE_TABLE_X + SHARE_COLUMNS[0] - 12 - nameX;
        g.color(mine ? Theme.GOLD : Theme.TEXT);
        text(context, player.name(), true, 44, 26, nameMax, nameX, middle, "left");

        g.color(Theme.TEXT);
        int[] counts = {record.wins(), record.selfDraws(), record.dealIns()};
        for (int i = 0; i < counts.length; i++) {
            cell(context, String.valueOf(counts[i]), mine, 44, SHARE_MIN_NUMBER, SHARE_TABLE_X, SHARE_COLUMNS, 1 + i,
                    "center", middle);
        }
        g.color(ResultDialog.moneyColor(record.winnings()));
        cell(context, signed(record.winnings()), true, 42, SHARE_MIN_NUMBER, SHARE_TABLE_X, SHARE_COLUMNS, 4, "right",
                middle);
        g.color(Theme.TEXT);
        cell(context, grouped(player.chips()), mine, 36, SHARE_MIN_NUMBER, SHARE_TABLE_X, SHARE_COLUMNS, 5, "right",
                middle);
    }

    // ---- 輸贏歷程 PDF ----

    /** 整份 PDF，base64。每頁先畫在同一張 Canvas 上，轉成 JPEG 再換下一頁。 */
    String pdfBase64() {
        List<RoundRecord> history = game.history();
        int firstRows = rowsFrom(FIRST_TABLE_TOP);
        int nextRows = rowsFrom(NEXT_TABLE_TOP);
        // 最後一列是合計
        int rows = history.size() + 1;
        int pageCount = rows <= firstRows ? 1 : 1 + (rows - firstRows + nextRows - 1) / nextRows;

        HTMLCanvasElement canvas = canvas(PAGE_W, PAGE_H);
        CanvasRenderingContext2D context = context(canvas);
        PdfWriter pdf = new PdfWriter(PDF_W, PDF_H);
        int[] totals = new int[SEATS];
        int next = 0;
        for (int page = 0; page < pageCount; page++) {
            Gfx g = new Gfx(context, 1);
            g.color("#FFFFFF");
            g.fillRect(0, 0, PAGE_W, PAGE_H);
            paintPageFrame(g, page, pageCount);
            double y;
            if (page == 0) {
                paintRankTable(g);
                section(context, "每局輸贏", FIRST_TABLE_TOP);
                y = FIRST_TABLE_TOP;
            } else {
                section(context, "每局輸贏（續）", NEXT_TABLE_TOP);
                y = NEXT_TABLE_TOP;
            }
            y = paintRoundHeader(g, y);
            int capacity = page == 0 ? firstRows : nextRows;
            for (int row = 0; row < capacity && next <= history.size(); row++, next++) {
                if (next == history.size()) {
                    paintTotals(g, y, totals);
                } else {
                    RoundRecord record = history.get(next);
                    for (int seat = 0; seat < SEATS; seat++) {
                        totals[seat] += record.deltas()[seat];
                    }
                    paintRound(g, y, record, totals[0], row % 2 == 1);
                }
                y += ROW_H;
            }
            String jpeg = canvas.toDataURL("image/jpeg", JPEG_QUALITY);
            pdf.addPage(Base64.getDecoder().decode(jpeg.substring(jpeg.indexOf(',') + 1)), PAGE_W, PAGE_H);
        }
        return Base64.getEncoder().encodeToString(pdf.toBytes("EZMJ 輸贏歷程 " + when(), "D:" + digits + "00"));
    }

    /** 表頭下面放得下幾列。 */
    private static int rowsFrom(double tableTop) {
        return (int) ((BOTTOM - tableTop - HEAD_H) / ROW_H);
    }

    /** 每頁都有的標題、時間與頁碼。 */
    private void paintPageFrame(Gfx g, int page, int pageCount) {
        CanvasRenderingContext2D context = g.context();
        g.color(INK);
        text(context, "EZMJ 台灣十六張麻將　輸贏歷程", true, 36, 36, 1100, MARGIN, 86, "left");
        g.color(GRAY);
        text(context, "牌局結束 " + when(), false, 20, 20, 400, PAGE_W - MARGIN, 90, "right");
        g.color(INK);
        g.fillRect(MARGIN, 114, PAGE_W - MARGIN * 2, 2);
        g.color(GRAY);
        text(context, "第 " + (page + 1) + " 頁，共 " + pageCount + " 頁", false, 18, 18, 300, PAGE_W / 2.0, PAGE_H - 40,
                "center");
        text(context, Browser.siteAddress(), false, 18, 14, 500, PAGE_W - MARGIN, PAGE_H - 40, "right");
    }

    /** 表格上面的小標題，tableTop 是表格的上緣。 */
    private static void section(CanvasRenderingContext2D context, String title, double tableTop) {
        context.setFillStyle(INK);
        text(context, title, true, 24, 18, PAGE_W - MARGIN * 2, MARGIN, tableTop - 22, "left");
    }

    /** 第一頁上面：房間的設定與最終排名。 */
    private void paintRankTable(Gfx g) {
        CanvasRenderingContext2D context = g.context();
        g.color(GRAY);
        text(context, infoLine() + (roomName.isEmpty() ? "" : "　房間：" + roomName), false, 22, 16, PAGE_W - MARGIN * 2,
                MARGIN, 150, "left");
        section(context, "最終排名　" + FinalDialog.headline(FinalDialog.ranking(game)), RANK_TOP);

        double y = RANK_TOP;
        String[] heads = {"名次", "玩家", "胡牌", "自摸", "放槍", "輸贏", "籌碼"};
        String[] aligns = {"center", "left", "center", "center", "center", "right", "right"};
        g.color(HEAD);
        g.fillRect(MARGIN, y, sum(RANK_COLUMNS), HEAD_H);
        g.color(INK);
        cells(context, heads, aligns, RANK_COLUMNS, y + HEAD_H / 2, true, 20);
        y += HEAD_H;

        Player[] ranking = FinalDialog.ranking(game);
        int[] ranks = FinalDialog.ranks(ranking);
        for (int i = 0; i < ranking.length; i++) {
            Player player = ranking[i];
            GameRecord record = game.record(player.seat(), "");
            if (player.seat() == 0) {
                g.color(MINE);
                g.fillRect(MARGIN, y, sum(RANK_COLUMNS), RANK_ROW_H);
            }
            g.color(RULE);
            g.fillRect(MARGIN, y + RANK_ROW_H - 1, sum(RANK_COLUMNS), 1);
            // 輸贏那一欄另外上色，先空著
            String[] values = {String.valueOf(ranks[i]), player.name() + (player.seat() == 0 ? "（我）" : ""),
                String.valueOf(record.wins()), String.valueOf(record.selfDraws()), String.valueOf(record.dealIns()),
                "", grouped(player.chips())};
            g.color(INK);
            cells(context, values, aligns, RANK_COLUMNS, y + RANK_ROW_H / 2, player.seat() == 0, 22);
            context.setFillStyle(printMoneyColor(record.winnings()));
            cell(context, signed(record.winnings()), true, 22, RANK_COLUMNS, 5, "right", y + RANK_ROW_H / 2);
            y += RANK_ROW_H;
        }
    }

    private double paintRoundHeader(Gfx g, double y) {
        CanvasRenderingContext2D context = g.context();
        List<String> heads = new ArrayList<>(List.of("局", "圈風・莊家", "結果與牌型", "台"));
        for (int seat = 0; seat < SEATS; seat++) {
            heads.add(game.player(seat).name());
        }
        heads.add("我的累計");
        g.color(HEAD);
        g.fillRect(MARGIN, y, sum(ROUND_COLUMNS), HEAD_H);
        g.color(INK);
        cells(context, heads.toArray(new String[0]), roundAligns(true), ROUND_COLUMNS, y + HEAD_H / 2, true, 20);
        return y + HEAD_H;
    }

    private static String[] roundAligns(boolean header) {
        String money = header ? "center" : "right";
        return new String[] {"center", "left", "left", "center", money, money, money, money, money};
    }

    private void paintRound(Gfx g, double y, RoundRecord record, int running, boolean stripe) {
        CanvasRenderingContext2D context = g.context();
        double width = sum(ROUND_COLUMNS);
        if (stripe) {
            g.color(STRIPE);
            g.fillRect(MARGIN, y, width, ROW_H);
        }
        g.color(RULE);
        g.fillRect(MARGIN, y + ROW_H - 1, width, 1);
        RoundResult result = record.result();
        double middle = y + ROW_H / 2;
        String[] aligns = roundAligns(false);
        context.setFillStyle(INK);
        cell(context, String.valueOf(record.number()), false, 22, ROUND_COLUMNS, 0, aligns[0], middle);
        cell(context, HistoryDialog.windText(game, record), false, 22, ROUND_COLUMNS, 1, aligns[1], middle);
        cell(context, result.isDrawnGame() ? "—" : String.valueOf(result.score().totalTai()), false, 22, ROUND_COLUMNS, 3,
                aligns[3], middle);
        String score = HistoryDialog.scoreText(result);
        if (score == null) {
            cell(context, HistoryDialog.resultText(game, result), false, 22, ROUND_COLUMNS, 2, aligns[2], middle);
        } else {
            cell(context, HistoryDialog.resultText(game, result), false, 22, ROUND_COLUMNS, 2, aligns[2], y + 22);
            context.setFillStyle(GRAY);
            cell(context, score, false, 17, ROUND_COLUMNS, 2, aligns[2], y + 46);
        }
        for (int seat = 0; seat < SEATS; seat++) {
            int delta = record.deltas()[seat];
            context.setFillStyle(printMoneyColor(delta));
            cell(context, signed(delta), seat == 0, 22, ROUND_COLUMNS, 4 + seat, aligns[4 + seat], middle);
        }
        context.setFillStyle(printMoneyColor(running));
        cell(context, signed(running), false, 22, ROUND_COLUMNS, 8, aligns[8], middle);
    }

    private void paintTotals(Gfx g, double y, int[] totals) {
        CanvasRenderingContext2D context = g.context();
        g.color(HEAD);
        g.fillRect(MARGIN, y, sum(ROUND_COLUMNS), ROW_H);
        double middle = y + ROW_H / 2;
        context.setFillStyle(INK);
        cell(context, "合計", true, 22, ROUND_COLUMNS, 1, "left", middle);
        for (int seat = 0; seat < SEATS; seat++) {
            context.setFillStyle(printMoneyColor(totals[seat]));
            cell(context, signed(totals[seat]), true, 22, ROUND_COLUMNS, 4 + seat, "right", middle);
        }
        context.setFillStyle(printMoneyColor(totals[0]));
        cell(context, signed(totals[0]), true, 22, ROUND_COLUMNS, 8, "right", middle);
    }

    private static String printMoneyColor(int delta) {
        return delta == 0 ? GRAY : delta > 0 ? WIN : LOSS;
    }

    /** 一整列，顏色用目前的。 */
    private static void cells(CanvasRenderingContext2D context, String[] values, String[] aligns, double[] columns,
                              double middle, boolean bold, double size) {
        for (int i = 0; i < values.length; i++) {
            cell(context, values[i], bold, size, columns, i, aligns[i], middle);
        }
    }

    /** PDF 表格第 column 欄的一格。 */
    private static void cell(CanvasRenderingContext2D context, String value, boolean bold, double size, double[] columns,
                             int column, String align, double middle) {
        cell(context, value, bold, size, size * .75, MARGIN, columns, column, align, middle);
    }

    /** 從 tableLeft 開始的表格第 column 欄的一格，左右各留 12 像素。放不下就縮小字，縮到 minSize 還放不下就截掉。 */
    private static void cell(CanvasRenderingContext2D context, String value, boolean bold, double size, double minSize,
                             double tableLeft, double[] columns, int column, String align, double middle) {
        double left = tableLeft;
        for (int i = 0; i < column; i++) {
            left += columns[i];
        }
        double pad = 12;
        double width = columns[column] - pad * 2;
        double x = align.equals("left") ? left + pad : align.equals("right") ? left + columns[column] - pad
                : left + columns[column] / 2;
        text(context, value, bold, size, minSize, width, x, middle, align);
    }

    private static double sum(double[] columns) {
        double total = 0;
        for (double column : columns) {
            total += column;
        }
        return total;
    }

    // ---- 共用 ----

    private static HTMLCanvasElement canvas(int width, int height) {
        HTMLCanvasElement canvas = (HTMLCanvasElement) HTMLDocument.current().createElement("canvas");
        canvas.setWidth(width);
        canvas.setHeight(height);
        return canvas;
    }

    private static CanvasRenderingContext2D context(HTMLCanvasElement canvas) {
        return (CanvasRenderingContext2D) canvas.getContext("2d");
    }

    /**
     * 畫一行字，(x, y) 是 align 那一端、垂直置中。放不下 maxWidth 就縮小字，縮到 minSize 還放不下就截掉、加刪節號。
     * 回傳實際畫出來的字，字型留在 context 上，可以接著量寬度。
     */
    private static String text(CanvasRenderingContext2D context, String text, boolean bold, double size, double minSize,
                               double maxWidth, double x, double y, String align) {
        context.setFont(Theme.fixedFont(bold, size));
        double width = context.measureText(text).getWidth();
        String shown = text;
        if (width > maxWidth) {
            double fitted = Math.max(minSize, size * maxWidth / width);
            context.setFont(Theme.fixedFont(bold, fitted));
            if (context.measureText(text).getWidth() > maxWidth) {
                shown = clipped(context, text, maxWidth);
            }
        }
        context.setTextAlign(align);
        context.setTextBaseline("middle");
        context.fillText(shown, x, y);
        return shown;
    }

    /** 用目前的字型，從後面一個字一個字拿掉，直到加上刪節號放得下。 */
    private static String clipped(CanvasRenderingContext2D context, String text, double maxWidth) {
        int end = text.length();
        while (end > 0) {
            end--;
            // 不要切在一個字的兩半中間
            if (end > 0 && Character.isLowSurrogate(text.charAt(end))) {
                end--;
            }
            String candidate = text.substring(0, end) + "…";
            if (context.measureText(candidate).getWidth() <= maxWidth) {
                return candidate;
            }
        }
        return "…";
    }

    /** 千分位，例如 1,234,567。 */
    static String grouped(int value) {
        String digits = String.valueOf(Math.abs((long) value));
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && (digits.length() - i) % 3 == 0) {
                out.append(',');
            }
            out.append(digits.charAt(i));
        }
        return (value < 0 ? "-" : "") + out;
    }

    /** 贏的前面加 +，例如 +12,340。 */
    static String signed(int value) {
        return (value > 0 ? "+" : "") + grouped(value);
    }
}
