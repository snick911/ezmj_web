package ezmj.web;

import org.teavm.jso.browser.Window;
import org.teavm.jso.canvas.CanvasGradient;
import org.teavm.jso.canvas.CanvasRenderingContext2D;
import org.teavm.jso.dom.html.HTMLCanvasElement;
import org.teavm.jso.dom.html.HTMLDocument;
import org.teavm.jso.dom.html.HTMLElement;

/**
 * 「胡」與「自摸」按鈕外面的一圈火焰，照 Swing 版 TablePanel.FlameFrame 畫：上緣與兩側一排火舌各自忽高忽低、左右搖，
 * 外層橘到紅、裡面一層比較矮的黃色火心，尖端淡掉。
 *
 * 按鈕包在 .flame-frame 裡，火焰畫在一張蓋住按鈕四周的畫布上（.flame-canvas，在按鈕後面、不擋點擊）。
 * 畫布比按鈕大出去的部分用負的 margin 抵掉，所以動作列的排版和沒有火焰時一樣，牌桌不會跟著跳；
 * 火焰往上會伸到提示那一行後面。按鈕離開畫面就停止重畫；系統設定減少動態效果時只畫一次，不跳動。
 */
final class FlameFrame {

    /** 火焰往上、往兩側、往下各伸出多少，單位是 rem，和 style.css 的 .flame-canvas 一樣。 */
    private static final double TOP_REM = 1.5;
    private static final double SIDE_REM = 1.2;
    private static final double BOTTOM_REM = .35;
    private static final double[] STOPS = {0, .45, 1};
    private static final String[] OUTER = {"rgba(255,170,40,.92)", "rgba(240,90,20,.8)", "rgba(190,20,10,0)"};
    private static final String[] INNER = {"rgba(255,250,210,.96)", "rgba(255,214,90,.88)", "rgba(255,150,30,0)"};

    private final HTMLElement root = Dom.el("span", "flame-frame");
    private final HTMLCanvasElement canvas;
    private final HTMLElement button;
    private final double start = Browser.now();

    FlameFrame(HTMLElement button) {
        this.button = button;
        canvas = (HTMLCanvasElement) HTMLDocument.current().createElement("canvas");
        canvas.setClassName("flame-canvas");
        root.appendChild(canvas);
        root.appendChild(button);
        Window.requestAnimationFrame(time -> frame());
    }

    HTMLElement element() {
        return root;
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
        double top = TOP_REM * rem;
        double side = SIDE_REM * rem;
        double bottom = BOTTOM_REM * rem;
        double bw = button.getOffsetWidth();
        double bh = button.getOffsetHeight();
        if (bw <= 0 || bh <= 0) {
            return;
        }
        double ratio = Math.max(1, Window.current().getDevicePixelRatio());
        int pixelW = (int) Math.round((bw + side * 2) * ratio);
        int pixelH = (int) Math.round((bh + top + bottom) * ratio);
        if (canvas.getWidth() != pixelW || canvas.getHeight() != pixelH) {
            canvas.setWidth(pixelW);
            canvas.setHeight(pixelH);
        }
        CanvasRenderingContext2D g = (CanvasRenderingContext2D) canvas.getContext("2d");
        g.setTransform(ratio, 0, 0, ratio, 0, 0);
        g.clearRect(0, 0, bw + side * 2, bh + top + bottom);
        double t = (Browser.now() - start) / 1000;
        // 先畫外層橘紅的火，再畫裡面比較矮的黃色火心
        flames(g, side, top, bw, bh, t, top, side, rem, 1, OUTER, 0);
        flames(g, side, top, bw, bh, t, top * .62, side * .6, rem, .72, INNER, 40);
    }

    /**
     * 一層火。按鈕的左上角在 (x, y)、寬 w 高 h；reach 是上緣最高竄多高，sideReach 是兩側往外多遠，
     * thickness 是火舌寬度的比例，seed 讓每一層的節奏不一樣。
     */
    private static void flames(CanvasRenderingContext2D g, double x, double y, double w, double h, double t, double reach,
                               double sideReach, double rem, double thickness, String[] colors, int seed) {
        int count = Math.max(4, (int) Math.round(w / (rem * .8)));
        double step = w / count;
        for (int i = 0; i < count; i++) {
            double flicker = flicker(t, i + seed);
            double baseX = x + step * (i + .5);
            double height = reach * (.4 + .6 * flicker);
            double width = step * 1.9 * thickness;
            double sway = Math.sin(t * 6 + (i + seed) * 1.3) * width * .3;
            tongue(g, baseX, y + 3, baseX + sway, y + 3 - height, width, colors);
        }
        int sideCount = 3;
        for (int direction = -1; direction <= 1; direction += 2) {
            double edge = direction < 0 ? x + 2 : x + w - 2;
            for (int i = 0; i < sideCount; i++) {
                double flicker = flicker(t, i + seed + 20 + direction * 7);
                double baseY = y + h * (1 - .42 * i);
                double out = sideReach * (.45 + .5 * flicker);
                double up = reach * (.7 + .5 * flicker) * (.7 + .15 * i);
                tongue(g, edge, baseY, edge + direction * out, baseY - up, h * .55 * thickness, colors);
            }
        }
    }

    /** 0 到 1 的忽大忽小，每條火舌的節奏不一樣。 */
    private static double flicker(double t, int index) {
        return .5 + .5 * Math.sin(t * 11 + index * 1.7) * Math.sin(t * 6.3 + index * 2.9);
    }

    /** 一條火舌：根部在 (baseX, baseY)、寬 width，尖端在 (tipX, tipY)，顏色從根部往尖端變。 */
    private static void tongue(CanvasRenderingContext2D g, double baseX, double baseY, double tipX, double tipY,
                               double width, String[] colors) {
        double dx = tipX - baseX;
        double dy = tipY - baseY;
        double length = Math.sqrt(dx * dx + dy * dy);
        if (length < 1) {
            return;
        }
        // 根部沿著和火舌垂直的方向展開
        double nx = -dy / length * width / 2;
        double ny = dx / length * width / 2;
        CanvasGradient gradient = g.createLinearGradient(baseX, baseY, tipX, tipY);
        for (int i = 0; i < STOPS.length; i++) {
            gradient.addColorStop(STOPS[i], colors[i]);
        }
        g.setFillStyle(gradient);
        g.beginPath();
        g.moveTo(baseX - nx, baseY - ny);
        g.quadraticCurveTo(baseX - nx * .7 + dx * .5, baseY - ny * .7 + dy * .5, tipX, tipY);
        g.quadraticCurveTo(baseX + nx * .7 + dx * .5, baseY + ny * .7 + dy * .5, baseX + nx, baseY + ny);
        g.closePath();
        g.fill();
    }

    @org.teavm.jso.JSBody(params = "element", script = "return element.isConnected;")
    private static native boolean isConnected(HTMLElement element);

    @org.teavm.jso.JSBody(script = "return parseFloat(getComputedStyle(document.documentElement).fontSize) || 16;")
    private static native double rootFontSize();

    @org.teavm.jso.JSBody(script = "return !!(window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches);")
    private static native boolean reducedMotion();
}
