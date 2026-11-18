package ezmj.web;

import org.teavm.jso.JSBody;
import org.teavm.jso.browser.Window;
import org.teavm.jso.canvas.CanvasGradient;
import org.teavm.jso.canvas.CanvasRenderingContext2D;
import org.teavm.jso.dom.html.HTMLCanvasElement;
import org.teavm.jso.dom.html.HTMLDocument;
import org.teavm.jso.dom.html.HTMLElement;

/**
 * 「胡」與「自摸」按鈕外面的火焰，照 Swing 版的 FlameArt 畫，分成按鈕後面與前面兩張畫布：
 * 後面是一圈一明一暗、跟著圓角的熱氣光暈，一層糊開的淡光，與三層火舌（深紅的外焰、橘黃的中焰、白黃的火心）
 * 從按鈕上緣、兩個上角與兩側竄出來，根部被按鈕蓋住，看起來是從按鈕邊上燒起來；
 * 前面是按鈕邊上一道發亮的橘邊，與一顆顆往上飄、越飄越紅越淡的火星。全部照時間算，沒有亂數。
 *
 * 按鈕包在 .flame-frame 裡，兩張畫布（.flame-canvas）比按鈕大出去的部分用負的位移抵掉，不擋點擊，
 * 所以動作列的高度和沒有火焰時一樣，牌桌不會跟著跳；火焰往上竄到提示那一行。.flame-frame 左右各留 SIDE_REM，
 * 旁邊的按鈕才不會被火蓋住。按鈕離開畫面就停止重畫；系統設定減少動態效果時只畫一次、不跳動。
 */
final class FlameFrame {

    /** 上緣的火最高竄多高、兩側往外多遠，單位是 rem。往上到動作列的頂端；兩側和 Swing 版一樣是 18 像素。 */
    private static final double UP_REM = 2;
    private static final double SIDE_REM = 1.125;
    /** 畫布往上、往兩側、往下比按鈕大多少，單位是 rem，和 style.css 的 .flame-canvas 一樣。 */
    private static final double CANVAS_TOP_REM = 2.6;
    private static final double CANVAS_SIDE_REM = 2.6;
    private static final double CANVAS_BOTTOM_REM = 1;
    private static final double[] STOPS = {0, .4, 1};
    private static final String[] HALO = {"rgba(255,90,20,.275)", "rgba(255,120,30,.176)", "rgba(220,40,10,0)"};
    private static final String[] OUTER = {"rgba(225,55,18,.882)", "rgba(248,105,24,.667)", "rgba(190,25,8,0)"};
    private static final String[] MIDDLE = {"rgba(255,150,35,.961)", "rgba(255,185,55,.824)", "rgba(255,120,20,0)"};
    private static final String[] CORE = {"rgba(255,253,230,.98)", "rgba(255,232,140,.882)", "rgba(255,195,80,0)"};
    private static final int[] EMBER_HOT = {255, 236, 150};
    private static final int[] EMBER_COOL = {235, 70, 20};
    private static final int EMBERS = 14;
    private static final double GOLDEN = .6180339887;

    private final HTMLElement root = Dom.el("span", "flame-frame");
    private final HTMLCanvasElement behind = canvas("flame-canvas behind");
    private final HTMLCanvasElement front = canvas("flame-canvas front");
    private final HTMLElement button;
    private final double start = Browser.now();

    FlameFrame(HTMLElement button) {
        this.button = button;
        root.appendChild(behind);
        root.appendChild(button);
        root.appendChild(front);
        Window.requestAnimationFrame(time -> frame());
    }

    HTMLElement element() {
        return root;
    }

    private static HTMLCanvasElement canvas(String cssClass) {
        HTMLCanvasElement canvas = (HTMLCanvasElement) HTMLDocument.current().createElement("canvas");
        canvas.setClassName(cssClass);
        return canvas;
    }

    private void frame() {
        // 按鈕換掉了（動作列清空）就不再畫
        if (!isConnected(root)) {
            return;
        }
        paint();
        if (!reducedMotion()) {
            Window.requestAnimationFrame(time -> frame());
        }
    }

    private void paint() {
        double rem = rootFontSize();
        double bw = button.getOffsetWidth();
        double bh = button.getOffsetHeight();
        if (bw <= 0 || bh <= 0) {
            return;
        }
        double left = CANVAS_SIDE_REM * rem;
        double top = CANVAS_TOP_REM * rem;
        double w = bw + left * 2;
        double h = bh + top + CANVAS_BOTTOM_REM * rem;
        double ratio = Math.max(1, Window.current().getDevicePixelRatio());
        double t = (Browser.now() - start) / 1000;
        double arc = cornerRadius(button) * 2;
        double up = UP_REM * rem;
        Gfx g = prepare(behind, w, h, ratio);
        g.translate(left, top);
        paintBehind(g, bw, bh, arc, t, up, SIDE_REM * rem);
        g = prepare(front, w, h, ratio);
        g.translate(left, top);
        paintFront(g, bw, bh, arc, t, up);
    }

    /** 畫布對齊大小、清空，座標單位是 CSS 像素。 */
    private static Gfx prepare(HTMLCanvasElement canvas, double w, double h, double ratio) {
        int pixelW = (int) Math.round(w * ratio);
        int pixelH = (int) Math.round(h * ratio);
        if (canvas.getWidth() != pixelW || canvas.getHeight() != pixelH) {
            canvas.setWidth(pixelW);
            canvas.setHeight(pixelH);
        }
        CanvasRenderingContext2D context = (CanvasRenderingContext2D) canvas.getContext("2d");
        context.setTransform(1, 0, 0, 1, 0, 0);
        context.clearRect(0, 0, pixelW, pixelH);
        context.setGlobalAlpha(1);
        return new Gfx(context, ratio);
    }

    /**
     * 按鈕後面的光暈與火舌。按鈕的左上角在原點、寬 w 高 h，arc 是圓角的直徑，t 是燒了幾秒，
     * up 是上緣的火最高竄多高，side 是兩側往外多寬。
     */
    private static void paintBehind(Gfx g, double w, double h, double arc, double t, double up, double side) {
        paintHeat(g, w, h, arc, t, side);
        // 外焰先畫一次又寬又淡的，當作火的邊緣糊開的光
        paintTongues(g, w, h, t, up * 1.08, side * 1.1, 1.7, HALO, 0);
        double[] scales = {1, .7, .42};
        String[][] layers = {OUTER, MIDDLE, CORE};
        for (int layer = 0; layer < layers.length; layer++) {
            paintTongues(g, w, h, t, up * scales[layer], side * scales[layer], Math.sqrt(scales[layer]),
                    layers[layer], layer * 31);
        }
    }

    /** 按鈕前面：邊上一道一明一暗的橘邊，與往上飄的火星。 */
    private static void paintFront(Gfx g, double w, double h, double arc, double t, double up) {
        double pulse = .5 + .5 * Math.sin(t * 6.2);
        double width = Math.max(1.2, h * .045);
        g.color(rgba(255, 175, 70, (110 + 90 * pulse) / 255));
        g.stroke(width);
        double inset = width / 2;
        g.drawRoundRect(inset, inset, w - width, h - width, arc);
        paintEmbers(g, w, h, t, up);
    }

    /** 熱氣：按鈕外面幾圈越外越淡的橘光，整圈一起一明一暗，跟著按鈕的圓角。 */
    private static void paintHeat(Gfx g, double w, double h, double arc, double t, double side) {
        double pulse = .7 + .3 * Math.sin(t * 5.1) * Math.sin(t * 2.3 + 1);
        int rings = 5;
        for (int ring = rings; ring >= 1; ring--) {
            double grow = side * .75 * ring / rings;
            g.color(rgba(255, 105, 25, 30 * pulse * (rings + 1 - ring) / rings / 255));
            g.fillRoundRect(-grow, -grow * .8, w + grow * 2, h + grow * 1.6, arc + grow * 2);
        }
    }

    /**
     * 一層火舌：上緣一排往上竄、兩端短一點；兩個上角各一條往外斜的大火舌；兩側各三條越上面越往外飄。
     * fatness 是火舌寬度的比例，seed 讓每一層的節奏不一樣。
     */
    private static void paintTongues(Gfx g, double w, double h, double t, double up, double side, double fatness,
                                     String[] colors, int seed) {
        double spacing = Math.max(4, h * .3);
        int count = Math.max(4, (int) Math.round(w / spacing));
        double step = w / count;
        double base = h * .2;
        for (int i = 0; i < count; i++) {
            double u = (i + .5) / count;
            double f = flicker(t, i + seed);
            double envelope = .72 + .28 * Math.sin(Math.PI * u);
            // 單數的矮一點，高高低低交錯
            double length = up * envelope * (i % 2 == 0 ? .55 + .45 * f : .4 + .35 * f) + h * .2;
            double width = step * 2 * fatness;
            double sway = Math.sin(t * 5.3 + (i + seed) * 1.9) * width * .6 + (f - .5) * width * .3;
            double jitter = Math.sin((i + seed) * 12.9898) * step * .18;
            tongue(g, step * (i + .5) + jitter, base, 0, width, length, sway, colors);
        }
        // 兩個上角往外斜
        for (int dir = -1; dir <= 1; dir += 2) {
            double f = flicker(t, seed + 50 + dir * 7);
            double cornerX = dir < 0 ? h * .15 : w - h * .15;
            double length = up * (.75 + .3 * f) + h * .25;
            tongue(g, cornerX, base, dir * Math.toRadians(24 + 8 * f), h * .55 * fatness, length,
                    Math.sin(t * 6.1 + seed + dir) * h * .12, colors);
        }
        // 兩側：從下往上三條，越上面越長、越往外斜
        for (int dir = -1; dir <= 1; dir += 2) {
            double edge = dir < 0 ? 2 : w - 2;
            for (int j = 0; j < 3; j++) {
                double f = flicker(t, seed + 20 + j * 3 + dir * 11);
                double baseY = h * (.85 - .3 * j);
                double angle = dir * Math.toRadians(62 - 12 * j + 10 * (f - .5));
                double length = side * (1.05 + .25 * j) * (.6 + .5 * f) + h * .12;
                tongue(g, edge, baseY, angle, h * (.42 + .06 * j) * fatness, length,
                        Math.sin(t * 7.3 + j + seed) * h * .08, colors);
            }
        }
    }

    /** 0 到 1 的忽大忽小，三個頻率疊起來，看不出規律。 */
    private static double flicker(double t, double seed) {
        double v = .55 * Math.sin(t * 8.7 + seed * 1.37) + .3 * Math.sin(t * 13.9 + seed * 2.71)
                + .15 * Math.sin(t * 23.3 + seed * .53);
        return .5 + .5 * v;
    }

    /**
     * 一條火舌：根部中心在 (baseX, baseY)、寬 width，往 angle 的方向長 length（0 是正上方，正的往右斜），
     * 尖端往旁邊偏 sway。下半鼓起來、上面收成往旁邊彎的細尖，像一滴倒過來的水，顏色從根部往尖端變淡。
     */
    private static void tongue(Gfx g, double baseX, double baseY, double angle, double width, double length,
                               double sway, String[] colors) {
        if (length < 1 || width < .5) {
            return;
        }
        double[] saved = g.transform();
        g.translate(baseX, baseY);
        g.rotate(angle);
        double half = width / 2;
        CanvasRenderingContext2D context = g.context();
        CanvasGradient gradient = context.createLinearGradient(0, half * .35, sway, -length);
        for (int i = 0; i < STOPS.length; i++) {
            gradient.addColorStop(STOPS[i], colors[i]);
        }
        context.setFillStyle(gradient);
        context.beginPath();
        context.moveTo(-half, 0);
        context.bezierCurveTo(-half * 1.3, -length * .32, -half * .3 + sway * .25, -length * .72, sway, -length);
        context.bezierCurveTo(half * .3 + sway * .55, -length * .72, half * 1.3, -length * .32, half, 0);
        context.quadraticCurveTo(0, half * .7, -half, 0);
        context.closePath();
        context.fill();
        g.setTransform(saved);
    }

    /**
     * 火星：從按鈕上緣冒出來，邊飄邊左右晃，飄到 up 那麼高就熄了；一開始是亮黃、越飄越紅越淡。
     * 每一顆有自己的週期，每一輪從上緣不同的地方冒出來。
     */
    private static void paintEmbers(Gfx g, double w, double h, double t, double up) {
        for (int i = 0; i < EMBERS; i++) {
            double period = .9 + frac(i * .373) * .9;
            double cycle = t / period + i * GOLDEN;
            double phase = frac(cycle);
            double spot = frac(i * .381 + Math.floor(cycle) * GOLDEN);
            double x = w * (.06 + .88 * spot) + Math.sin(t * 3.1 + i * 2.3) * h * .14 * phase;
            double y = h * .05 - phase * (up * 1.05);
            double alpha = phase < .15 ? phase / .15 : (1 - phase) / .85;
            double r = Math.max(.8, h * (.04 + .03 * frac(i * .77)) * (1 - .45 * phase));
            int[] color = mix(EMBER_HOT, EMBER_COOL, phase);
            g.alpha(Math.max(0, Math.min(1, alpha)));
            g.radial(x, y, r * 3, rgba(color[0], color[1], color[2], .47), rgba(color[0], color[1], color[2], 0));
            g.fillEllipse(x - r * 3, y - r * 3, r * 6, r * 6);
            int[] core = mix(color, new int[] {255, 255, 255}, .35 * (1 - phase));
            g.color(rgba(core[0], core[1], core[2], 1));
            g.fillEllipse(x - r, y - r, r * 2, r * 2);
        }
        g.alpha(1);
    }

    private static int[] mix(int[] a, int[] b, double amount) {
        double keep = 1 - amount;
        return new int[] {(int) Math.round(a[0] * keep + b[0] * amount), (int) Math.round(a[1] * keep + b[1] * amount),
            (int) Math.round(a[2] * keep + b[2] * amount)};
    }

    /** 取到千分位，太小的數字 Java 會寫成 1.0E-4，CSS 看不懂。 */
    private static String rgba(int r, int g, int b, double alpha) {
        double clamped = Math.round(Math.max(0, Math.min(1, alpha)) * 1000) / 1000.0;
        return "rgba(" + r + "," + g + "," + b + "," + clamped + ")";
    }

    private static double frac(double v) {
        return v - Math.floor(v);
    }

    @JSBody(params = "element", script = "return element.isConnected;")
    private static native boolean isConnected(HTMLElement element);

    @JSBody(script = "return parseFloat(getComputedStyle(document.documentElement).fontSize) || 16;")
    private static native double rootFontSize();

    /** 按鈕圓角的半徑，CSS 像素。 */
    @JSBody(params = "element", script = "return parseFloat(getComputedStyle(element).borderTopLeftRadius) || 0;")
    private static native double cornerRadius(HTMLElement element);

    @JSBody(script = "return !!(window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches);")
    private static native boolean reducedMotion();
}
