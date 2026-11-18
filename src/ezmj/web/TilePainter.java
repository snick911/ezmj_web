package ezmj.web;

import java.util.LinkedHashMap;
import java.util.Map;

import org.teavm.jso.canvas.CanvasRenderingContext2D;
import org.teavm.jso.canvas.Path2D;
import org.teavm.jso.dom.html.HTMLCanvasElement;
import org.teavm.jso.dom.html.HTMLDocument;

import ezmj.model.Suit;
import ezmj.model.Tile;

/** 以向量繪製麻將牌，不需要圖片素材，任何尺寸都清晰。座標、路徑與顏色照 Swing 版的 TilePainter。 */
final class TilePainter {

    static final String IVORY = "#FBF7EA";
    private static final String IVORY_SHADE = "#E6DEC6";
    private static final String IVORY_SIDE = "#CFC5A6";
    private static final String EDGE = "rgba(138,128,102,.588)";
    /** 影子三層，越外越淡，邊緣才是軟的。 */
    private static final String SHADOW = "rgba(0,0,0,.149)";
    private static final String SHADOW_MID = "rgba(0,0,0,.102)";
    private static final String SHADOW_FAR = "rgba(0,0,0,.059)";
    /** 牌面左上那團反光。 */
    private static final String SHEEN = "rgba(255,255,255,.294)";
    private static final String CLEAR = "rgba(255,255,255,0)";
    private static final String GLOSS = "rgba(255,255,255,.745)";
    private static final String DIM = "rgba(0,0,0,.176)";
    private static final double DEPTH = .13;
    private static final int CACHE_LIMIT = 800;
    private static final String BACK = "#1F7A5C";
    private static final String BACK_DARK = "#12553F";
    private static final String MASK = "rgba(11,26,21,.667)";
    static final String INK = "#151B2E";
    static final String CRIMSON = "#B5202A";
    static final String JADE = "#1A6E3C";

    /** 牌面上的數字，五萬寫成大寫的「伍」。 */
    private static final String[] WAN_NUMBERS = {"一", "二", "三", "四", "伍", "六", "七", "八", "九"};

    /** 筒與條的點位，座標為牌面內 0-1 的比例。 */
    private static final double[][][] TONG_PIPS = {
        {{.5, .5}},
        {{.5, .25}, {.5, .75}},
        {{.22, .17}, {.5, .5}, {.78, .83}},
        {{.25, .26}, {.75, .26}, {.25, .74}, {.75, .74}},
        {{.22, .2}, {.78, .2}, {.5, .5}, {.22, .8}, {.78, .8}},
        {{.27, .15}, {.73, .15}, {.27, .55}, {.73, .55}, {.27, .85}, {.73, .85}},
        {{.18, .11}, {.5, .21}, {.82, .31}, {.27, .6}, {.73, .6}, {.27, .87}, {.73, .87}},
        {{.29, .12}, {.71, .12}, {.29, .37}, {.71, .37}, {.29, .63}, {.71, .63}, {.29, .88}, {.71, .88}},
        {{.17, .17}, {.5, .17}, {.83, .17}, {.17, .5}, {.5, .5}, {.83, .5}, {.17, .83}, {.5, .83}, {.83, .83}},
    };

    /** 各點數的筒子半徑，以牌面寬度為比例。 */
    private static final double[] TONG_RADIUS = {.47, .29, .22, .22, .2, .2, .17, .165, .155};

    /** 每個點位的顏色：G 綠、N 墨、R 紅。 */
    private static final String[] TONG_INKS = {
        "R", "GN", "GRN", "GNNG", "GNRNG", "GGRRRR", "GGGRRRR", "NNNNNNNN", "NNNRRRGGG",
    };

    /** 一條是鳥、八條是 WM 圖形，其他點數才用這張表。 */
    private static final double[][][] TIAO_PIPS = {
        {},
        {{.5, .25}, {.5, .75}},
        {{.5, .25}, {.25, .75}, {.75, .75}},
        {{.25, .25}, {.75, .25}, {.25, .75}, {.75, .75}},
        {{.2, .25}, {.8, .25}, {.5, .5}, {.2, .75}, {.8, .75}},
        {{.2, .25}, {.5, .25}, {.8, .25}, {.2, .75}, {.5, .75}, {.8, .75}},
        {{.5, .17}, {.2, .5}, {.5, .5}, {.8, .5}, {.2, .83}, {.5, .83}, {.8, .83}},
        {},
        {{.2, .17}, {.5, .17}, {.8, .17}, {.2, .5}, {.5, .5}, {.8, .5}, {.2, .83}, {.5, .83}, {.8, .83}},
    };

    private static final String[] TIAO_INKS = {
        "", "NN", "GNG", "GNNG", "GNRNG", "NNNGGG", "RGNGGNG", "", "GRNGRNGRN",
    };

    /** 八條上半部的 W，座標為牌面內 0-1 的比例；下半部的 M 是它的上下翻轉。 */
    private static final double[][] EIGHT_W = {{.02, .02}, {.1, .42}, {.5, .08}, {.9, .42}, {.98, .02}};

    /** 半徑為 1 的筒子：圓盤挖出五片花瓣。 */
    private static final Path2D TONG_PIP = tongPip();

    private TilePainter() {
    }

    /** 牌的正面在目前座標裡的位置，與厚度延伸的方向。 */
    private record Body(double x, double y, double w, double h, double dx, double dy, double arc) {
    }

    /**
     * 牌面內縮一點，留出厚度。厚度與光線都以畫面為準：不管這張牌屬於哪一家、轉了幾度，
     * 都是光從左上照下來、厚度露在右下，整桌看起來才像同一張桌子。
     */
    private static Body body(Gfx g, double x, double y, double w, double h) {
        double depth = Math.min(w, h) * DEPTH;
        double[] local = fromScreen(g, .3 * depth, depth);
        double faceW = w - Math.abs(local[0]);
        double faceH = h - Math.abs(local[1]);
        return new Body(x + Math.max(0, -local[0]), y + Math.max(0, -local[1]), faceW, faceH,
                local[0], local[1], faceW * .2);
    }

    /** 把畫面上的方向換算成目前座標裡的方向。 */
    private static double[] fromScreen(Gfx g, double screenX, double screenY) {
        double angle = g.rotation();
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return new double[] {cos * screenX + sin * screenY, -sin * screenX + cos * screenY};
    }

    private static void fillShape(Gfx g, Body b, double shift) {
        g.fillRoundRect(b.x + b.dx * shift, b.y + b.dy * shift, b.w, b.h, b.arc);
    }

    /** 畫好的牌，tile 為 null 表示牌背。尺寸是實際像素，quarter 是轉了幾個 90 度，standing 是站著的手牌。 */
    private record CacheKey(Tile tile, int w, int h, int quarter, boolean standing) {
    }

    /** 同一種牌只畫一次，之後直接貼圖，整桌重畫才快得起來。 */
    private static final Map<CacheKey, HTMLCanvasElement> CACHE = new LinkedHashMap<>(256, .75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<CacheKey, HTMLCanvasElement> eldest) {
            return size() > CACHE_LIMIT;
        }
    };

    /** 字型晚一點才載入好的話，已經畫好的牌要重畫。 */
    static void clearCache() {
        CACHE.clear();
    }

    static void paintFace(Gfx g, Tile tile, double x, double y, double w, double h) {
        paintTile(g, tile, x, y, w, h, false);
    }

    static void paintBack(Gfx g, double x, double y, double w, double h) {
        paintTile(g, null, x, y, w, h, false);
    }

    /**
     * 自己面前站著的手牌，照 Swing 版：躺著的牌露在下緣的厚度，站著時改成牌面上面的一道牌頂，
     * 靠牌面的一半是象牙色、後面一半是綠色的牌背，像從斜上方看著立起來的牌。整張還是畫在 (x, y, w, h) 裡面，
     * 和躺著的牌一樣大，排版不用另外留地方。
     */
    static void paintStanding(Gfx g, Tile tile, double x, double y, double w, double h) {
        paintTile(g, tile, x, y, w, h, true);
    }

    /** 站著的牌的遮罩，和 paintMask 一樣連厚度一起蓋住，形狀照站著的牌。 */
    static void paintStandingMask(Gfx g, double x, double y, double w, double h) {
        Body whole = standingWhole(x, y, w, h);
        g.color(MASK);
        g.context().beginPath();
        g.roundRectPath(whole.x, whole.y, whole.w, whole.h, whole.arc);
        g.roundRectPath(whole.x + whole.dx, whole.y + whole.dy, whole.w, whole.h, whole.arc);
        g.context().fill();
    }

    /** 站著的牌的外框，和 paintHighlight 一樣框住牌面。 */
    static void paintStandingHighlight(Gfx g, double x, double y, double w, double h, String color) {
        Body face = standingFace(x, y, w, h);
        g.color(color);
        g.stroke(Math.max(2, w * .07));
        g.drawRoundRect(face.x, face.y, face.w, face.h, face.arc);
    }

    /** 站著的牌頂有多高，是厚度的這個倍數。 */
    private static final double LID = 1.15;

    /** 站著的牌整張的外形：牌頂加牌面，右邊照樣露一點厚度，下緣只留一條細邊。 */
    private static Body standingWhole(double x, double y, double w, double h) {
        double depth = Math.min(w, h) * DEPTH;
        double side = depth * .3;
        double base = depth * .25;
        return new Body(x, y, w - side, h - base, side, base, (w - side) * .2);
    }

    /** 站著的牌的牌面：整張扣掉上面的牌頂。 */
    private static Body standingFace(double x, double y, double w, double h) {
        Body whole = standingWhole(x, y, w, h);
        double lid = Math.min(w, h) * DEPTH * LID;
        return new Body(whole.x, whole.y + lid, whole.w, whole.h - lid, whole.dx, whole.dy, whole.arc);
    }

    /** 轉正或轉 90 度的牌走快取；飛行中轉到一半的牌角度太多種，就直接畫。 */
    private static void paintTile(Gfx g, Tile tile, double x, double y, double w, double h, boolean standing) {
        double[] t = g.transform();
        int quarter = quarterTurns(t);
        if (quarter < 0) {
            drawTile(g, tile, x, y, w, h, standing);
            return;
        }
        double x0 = t[0] * x + t[2] * y + t[4];
        double y0 = t[1] * x + t[3] * y + t[5];
        double x1 = t[0] * (x + w) + t[2] * (y + h) + t[4];
        double y1 = t[1] * (x + w) + t[3] * (y + h) + t[5];
        double boxX = Math.min(x0, x1);
        double boxY = Math.min(y0, y1);
        CacheKey key = new CacheKey(tile, (int) Math.round(Math.abs(x1 - x0)), (int) Math.round(Math.abs(y1 - y0)), quarter,
                standing);
        int margin = margin(key);
        HTMLCanvasElement image = CACHE.get(key);
        if (image == null) {
            image = (HTMLCanvasElement) HTMLDocument.current().createElement("canvas");
            image.setWidth(key.w() + margin * 2);
            image.setHeight(key.h() + margin * 2);
            Gfx painter = new Gfx((CanvasRenderingContext2D) image.getContext("2d"), 1);
            painter.setTransform(new double[] {t[0], t[1], t[2], t[3], t[4] + margin - boxX, t[5] + margin - boxY});
            drawTile(painter, tile, x, y, w, h, standing);
            CACHE.put(key, image);
        }
        g.context().setTransform(1, 0, 0, 1, 0, 0);
        g.context().drawImage(image, Math.round(boxX) - margin, Math.round(boxY) - margin);
        g.setTransform(t);
    }

    /** 陰影會超出牌的範圍，圖要多留這麼多像素；最外層的影子錯開厚度的 2.2 倍。 */
    private static int margin(CacheKey key) {
        return (int) (Math.max(key.w(), key.h()) * .32) + 3;
    }

    /** 轉了幾個 90 度；不是剛好的直角時回傳 -1。 */
    private static int quarterTurns(double[] t) {
        boolean straight = Math.abs(t[2]) < 1e-6 && Math.abs(t[1]) < 1e-6;
        boolean sideways = Math.abs(t[0]) < 1e-6 && Math.abs(t[3]) < 1e-6;
        if (!straight && !sideways) {
            return -1;
        }
        return Math.floorMod((int) Math.round(Math.atan2(t[1], t[0]) / (Math.PI / 2)), 4);
    }

    private static void drawTile(Gfx g, Tile tile, double x, double y, double w, double h, boolean standing) {
        if (tile == null) {
            paintBody(g, body(g, x, y, w, h), BACK, BACK_DARK, BACK_DARK, IVORY_SHADE);
        } else {
            drawFace(g, tile, x, y, w, h, standing);
        }
    }

    private static void drawFace(Gfx g, Tile tile, double x, double y, double w, double h, boolean standing) {
        Body b;
        if (standing) {
            // 整張立起來的牌（牌頂加牌面）一起投影子、露厚度，再畫牌頂，牌面蓋在最上面
            Body whole = standingWhole(x, y, w, h);
            b = standingFace(x, y, w, h);
            paintSides(g, whole, BACK_DARK, IVORY_SIDE);
            paintLid(g, whole, b);
            paintTop(g, b, IVORY, IVORY_SHADE);
        } else {
            b = body(g, x, y, w, h);
            paintBody(g, b, IVORY, IVORY_SHADE, IVORY_SIDE, BACK_DARK);
        }
        double px = b.x + b.w * .1;
        double py = b.y + b.h * .08;
        double pw = b.w * .8;
        double ph = b.h * .84;
        switch (tile.suit()) {
            case WAN:
                paintWan(g, tile, px, py, pw, ph);
                break;
            case TONG:
                paintTong(g, tile, px, py, pw, ph);
                break;
            case TIAO:
                paintTiao(g, tile, px, py, pw, ph);
                break;
            case FLOWER:
                paintFlower(g, tile, px, py, pw, ph);
                break;
            default:
                paintHonor(g, tile, px, py, pw, ph);
                break;
        }
    }

    /** 蓋在牌上的半透明遮罩，連厚度一起蓋住，表示這張牌現在不能選。 */
    static void paintMask(Gfx g, double x, double y, double w, double h) {
        Body b = body(g, x, y, w, h);
        g.color(MASK);
        g.context().beginPath();
        g.roundRectPath(b.x, b.y, b.w, b.h, b.arc);
        g.roundRectPath(b.x + b.dx, b.y + b.dy, b.w, b.h, b.arc);
        g.context().fill();
    }

    static void paintHighlight(Gfx g, double x, double y, double w, double h, String color) {
        Body b = body(g, x, y, w, h);
        g.color(color);
        g.stroke(Math.max(2, w * .07));
        g.drawRoundRect(b.x, b.y, b.w, b.h, b.arc);
    }

    /** 由下往上疊：陰影、底層、側面，最後是有光影的牌面與邊緣高光。 */
    private static void paintBody(Gfx g, Body b, String top, String bottom, String side, String base) {
        paintSides(g, b, base, side);
        paintTop(g, b, top, bottom);
    }

    /**
     * 影子與厚度，照 Swing 版。影子是三層越外越淡的，邊緣才是軟的；厚度是一層一層錯開的同一個形狀，
     * 一個像素以內就疊一層，放大了也是連續的斜面。後半是 base（牌面那張是綠色的牌背），前半是 side，越後面越暗。
     */
    private static void paintSides(Gfx g, Body b, String base, String side) {
        g.color(SHADOW_FAR);
        fillShape(g, b, 2.2);
        g.color(SHADOW_MID);
        fillShape(g, b, 1.75);
        g.color(SHADOW);
        fillShape(g, b, 1.35);
        double[] t = g.transform();
        double pixels = Math.hypot(b.dx, b.dy) * Math.hypot(t[0], t[1]);
        int steps = Math.max(4, Math.min(28, (int) Math.ceil(pixels * 1.5)));
        for (int i = steps; i >= 1; i--) {
            double s = (double) i / steps;
            g.color(s > .5 ? toBlack(base, (s - .5) * .5) : toBlack(side, s * .2));
            fillShape(g, b, s);
        }
    }

    /** "#RRGGBB" 往黑色調 amount（0 到 1），寫成 rgb()，和 Swing 版的 Theme.mix(color, BLACK, amount) 一樣。 */
    private static String toBlack(String hex, double amount) {
        int rgb = Integer.parseInt(hex.substring(1), 16);
        double keep = 1 - amount;
        return "rgb(" + Math.round((rgb >> 16 & 255) * keep) + "," + Math.round((rgb >> 8 & 255) * keep) + ","
                + Math.round((rgb & 255) * keep) + ")";
    }

    /** 牌面：從光那一邊亮到另一邊暗，左上再一團淡淡的反光，看起來是微微鼓起來的；邊上一圈高光與細的外框。 */
    private static void paintTop(Gfx g, Body b, String top, String bottom) {
        double reach = Math.max(b.w, b.h) * .6;
        double[] light = fromScreen(g, -.64 * reach, -.77 * reach);
        double centerX = b.x + b.w / 2;
        double centerY = b.y + b.h / 2;
        double lightX = centerX + light[0];
        double lightY = centerY + light[1];
        double darkX = centerX - light[0];
        double darkY = centerY - light[1];
        g.gradient(lightX, lightY, top, darkX, darkY, bottom);
        fillShape(g, b, 0);
        g.radial(centerX + light[0] * .55, centerY + light[1] * .55, reach * 1.1, SHEEN, CLEAR);
        fillShape(g, b, 0);

        double stroke = Math.max(1, b.w * .035);
        double inset = stroke / 2;
        g.stroke(stroke);
        g.gradient(lightX, lightY, GLOSS, darkX, darkY, DIM);
        g.drawRoundRect(b.x + inset, b.y + inset, b.w - stroke, b.h - stroke, b.arc);
        g.color(EDGE);
        g.stroke(.8);
        g.drawRoundRect(b.x, b.y, b.w, b.h, b.arc);
    }

    /**
     * 站著的牌露出來的牌頂，在牌面上面：靠近牌面的一半是象牙色，後面一半是綠色的牌背，中間一道細線分開，
     * 最上緣一道亮邊。whole 是整張立起來的牌，face 是牌面。照 Swing 版的 paintLid。
     */
    private static void paintLid(Gfx g, Body whole, Body face) {
        CanvasRenderingContext2D context = g.context();
        // 牌頂到牌面圓角的一半，下面被牌面蓋住
        double bottom = face.y + face.arc / 2;
        double split = whole.y + (face.y - whole.y) * .55;
        double line = Math.max(.6, face.w * .02);
        context.save();
        context.beginPath();
        g.roundRectPath(whole.x, whole.y, whole.w, whole.h, whole.arc);
        context.clip();
        context.beginPath();
        context.rect(whole.x - 1, whole.y - 1, whole.w + 2, bottom - whole.y + 1);
        context.clip();
        g.gradient(0, whole.y, BACK, 0, split, BACK_DARK);
        g.fillRect(whole.x - 1, whole.y - 1, whole.w + 2, bottom - whole.y + 1);
        g.gradient(0, split, IVORY_SIDE, 0, face.y, IVORY_SHADE);
        g.fillRect(whole.x - 1, split, whole.w + 2, bottom - split);
        g.color("rgba(0,0,0,.216)");
        g.stroke(line);
        g.drawLine(whole.x + whole.arc * .3, split, whole.x + whole.w - whole.arc * .3, split);
        g.color("rgba(255,255,255,.275)");
        g.stroke(line * 1.6);
        g.drawRoundRect(whole.x, whole.y, whole.w, whole.h, whole.arc);
        context.restore();
    }

    private static void paintWan(Gfx g, Tile tile, double x, double y, double w, double h) {
        drawCentered(g, WAN_NUMBERS[tile.rank() - 1], x, y, w, h * .46, INK, Theme.brushFont(h * .44), h * .44);
        drawCentered(g, "萬", x, y + h * .46, w, h * .54, CRIMSON, Theme.brushFont(h * .54), h * .54);
    }

    private static String ink(char code) {
        return code == 'R' ? CRIMSON : code == 'G' ? JADE : INK;
    }

    private static Path2D tongPip() {
        Path2D pip = Path2D.create();
        pip.arc(0, 0, 1, 0, Math.PI * 2);
        pip.closePath();
        for (int i = 0; i < 5; i++) {
            double angle = i * 2 * Math.PI / 5;
            double[][] petal = {{0, -.14}, {.12, -.28}, {.34, -.48}, {.27, -.74}, {0, -.9}, {-.27, -.74},
                {-.34, -.48}, {-.12, -.28}, {0, -.14}};
            for (double[] point : petal) {
                double px = point[0];
                point[0] = px * Math.cos(angle) - point[1] * Math.sin(angle);
                point[1] = px * Math.sin(angle) + point[1] * Math.cos(angle);
            }
            pip.moveTo(petal[0][0], petal[0][1]);
            pip.bezierCurveTo(petal[1][0], petal[1][1], petal[2][0], petal[2][1], petal[3][0], petal[3][1]);
            pip.quadraticCurveTo(petal[4][0], petal[4][1], petal[5][0], petal[5][1]);
            pip.bezierCurveTo(petal[6][0], petal[6][1], petal[7][0], petal[7][1], petal[8][0], petal[8][1]);
            pip.closePath();
        }
        return pip;
    }

    private static void fillTongPip(Gfx g, double cx, double cy, double radius, String color) {
        double[] saved = g.transform();
        g.translate(cx, cy);
        g.scale(radius, radius);
        g.color(color);
        g.fillEvenOdd(TONG_PIP);
        g.setTransform(saved);
    }

    private static void paintTong(Gfx g, Tile tile, double x, double y, double w, double h) {
        double radius = w * TONG_RADIUS[tile.rank() - 1];
        if (tile.rank() == 1) {
            paintOneTong(g, x + w / 2, y + h / 2, radius);
            return;
        }
        double[][] pips = TONG_PIPS[tile.rank() - 1];
        String inks = TONG_INKS[tile.rank() - 1];
        for (int i = 0; i < pips.length; i++) {
            fillTongPip(g, x + pips[i][0] * w, y + pips[i][1] * h, radius, ink(inks.charAt(i)));
        }
    }

    /** 一筒：外圈綠環，中間一圈花瓣，花心是紅色的筒子。 */
    private static void paintOneTong(Gfx g, double cx, double cy, double radius) {
        g.color(JADE);
        g.stroke(radius * .12);
        g.drawEllipse(cx - radius * .95, cy - radius * .95, radius * 1.9, radius * 1.9);
        g.stroke(radius * .05);
        g.drawEllipse(cx - radius * .36, cy - radius * .36, radius * .72, radius * .72);
        g.stroke(radius * .065);
        for (int i = 0; i < 12; i++) {
            double[] saved = g.transform();
            g.translate(cx, cy);
            g.rotate(i * Math.PI / 6);
            g.drawEllipse(-radius * .1, -radius * .79, radius * .2, radius * .34);
            g.setTransform(saved);
        }
        fillTongPip(g, cx, cy, radius * .31, CRIMSON);
    }

    private static void paintTiao(Gfx g, Tile tile, double x, double y, double w, double h) {
        if (tile.rank() == 1) {
            TileArt.paintBird(g, x, y, w, h);
            return;
        }
        if (tile.rank() == 8) {
            paintEightTiao(g, x, y, w, h);
            return;
        }
        double[][] pips = TIAO_PIPS[tile.rank() - 1];
        String inks = TIAO_INKS[tile.rank() - 1];
        double length = h * (tile.rank() <= 6 ? .42 : .29);
        double ring = length / 7.1;
        for (int i = 0; i < pips.length; i++) {
            double cx = x + pips[i][0] * w;
            double cy = y + pips[i][1] * h;
            g.color(ink(inks.charAt(i)));
            paintChain(g, cx, cy - length / 2 + ring, cx, cy + length / 2 - ring, 4, ring);
        }
    }

    /** 八條：上半部綠色的 W，下半部墨色的 M，都由小圓圈串成。 */
    private static void paintEightTiao(Gfx g, double x, double y, double w, double h) {
        double ring = w * .07;
        double left = x + ring;
        double top = y + ring;
        double spanW = w - ring * 2;
        double spanH = h - ring * 2;
        for (int leg = 0; leg + 1 < EIGHT_W.length; leg++) {
            double x0 = left + EIGHT_W[leg][0] * spanW;
            double x1 = left + EIGHT_W[leg + 1][0] * spanW;
            double y0 = EIGHT_W[leg][1] * spanH;
            double y1 = EIGHT_W[leg + 1][1] * spanH;
            // 每段長短不同，依長度決定圓圈數，圓圈才會一個挨一個
            int rings = (int) Math.round(Math.hypot(x1 - x0, y1 - y0) / (ring * 1.7)) + 1;
            g.color(JADE);
            paintChain(g, x0, top + y0, x1, top + y1, rings, ring);
            g.color(INK);
            paintChain(g, x0, top + spanH - y0, x1, top + spanH - y1, rings, ring);
        }
    }

    /** 由 (x0, y0) 到 (x1, y1) 串起一列空心小圓，那就是一根竹條。 */
    private static void paintChain(Gfx g, double x0, double y0, double x1, double y1, int rings, double radius) {
        g.stroke(Math.max(.7, radius * .52));
        for (int i = 0; i < rings; i++) {
            double t = i / (double) (rings - 1);
            double cx = x0 + (x1 - x0) * t;
            double cy = y0 + (y1 - y0) * t;
            g.drawEllipse(cx - radius, cy - radius, radius * 2, radius * 2);
        }
    }

    private static void paintHonor(Gfx g, Tile tile, double x, double y, double w, double h) {
        if (tile.suit() == Suit.DRAGON && tile.rank() == 3) {
            paintWhiteDragon(g, x, y, w, h);
            return;
        }
        String color = tile.suit() == Suit.WIND ? INK : tile.rank() == 1 ? CRIMSON : JADE;
        drawCentered(g, tile.label(), x, y, w, h, color, Theme.brushFont(h * .68), h * .68);
    }

    /** 白板：墨色的外框，框上有一列白色花紋，中間留白。 */
    private static void paintWhiteDragon(Gfx g, double x, double y, double w, double h) {
        double frameW = w * .9;
        double frameH = h * .86;
        double left = x + (w - frameW) / 2;
        double top = y + (h - frameH) / 2;
        double band = frameW * .2;
        Path2D frame = Path2D.create();
        frame.rect(left, top, frameW, frameH);
        frame.rect(left + band, top + band, frameW - band * 2, frameH - band * 2);
        g.color(INK);
        g.fillEvenOdd(frame);
        g.color(IVORY);
        g.stroke(Math.max(.6, band * .13));
        for (int i = 0; i < 2; i++) {
            double cx = left + frameW * (i == 0 ? .34 : .66);
            paintScroll(g, cx, top + band / 2, band * .36);
            paintScroll(g, cx, top + frameH - band / 2, band * .36);
        }
        for (int i = 0; i < 4; i++) {
            double cy = top + band + (frameH - band * 2) * (i + .5) / 4;
            paintScroll(g, left + band / 2, cy, band * .36);
            paintScroll(g, left + frameW - band / 2, cy, band * .36);
        }
    }

    /** 白板框上的一個花紋：兩道相背的弧線。 */
    private static void paintScroll(Gfx g, double cx, double cy, double size) {
        CanvasRenderingContext2D context = g.context();
        context.beginPath();
        context.moveTo(cx - size, cy - size);
        context.quadraticCurveTo(cx + size * .5, cy, cx - size, cy + size);
        context.moveTo(cx + size, cy - size);
        context.quadraticCurveTo(cx - size * .5, cy, cx + size, cy + size);
        context.stroke();
    }

    /** 春夏秋冬是數字在左、紅字在右；梅蘭竹菊是墨字在左、紅數字在右。 */
    private static void paintFlower(Gfx g, Tile tile, double x, double y, double w, double h) {
        String seat = String.valueOf((tile.rank() - 1) % 4 + 1);
        double headH = h * .27;
        String number = Theme.fixedFont(true, h * .21);
        String name = Theme.brushFont(h * .25);
        if (tile.rank() <= 4) {
            drawCentered(g, seat, x, y, w * .4, headH, INK, number, h * .21);
            drawCentered(g, tile.label(), x + w * .45, y, w * .55, headH, CRIMSON, name, h * .25);
        } else {
            drawCentered(g, tile.label(), x, y, w * .55, headH, INK, name, h * .25);
            drawCentered(g, seat, x + w * .6, y, w * .4, headH, CRIMSON, number, h * .21);
        }
        TileArt.paintFlower(g, tile.rank(), x, y + headH, w, h - headH);
    }

    private static void drawCentered(Gfx g, String text, double x, double y, double w, double h,
                                     String color, String font, double size) {
        g.font(font);
        double cx = x + w / 2;
        double cy = y + h / 2;
        // 先畫一道淡淡的亮邊，字看起來像刻在牌面上
        double carve = Math.max(.5, size * .025);
        double[] offset = fromScreen(g, carve, carve);
        g.color(GLOSS);
        g.drawCentered(text, cx + offset[0], cy + offset[1]);
        g.color(color);
        g.drawCentered(text, cx, cy);
    }
}
