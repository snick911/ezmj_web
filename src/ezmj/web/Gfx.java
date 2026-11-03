package ezmj.web;

import org.teavm.jso.JSBody;
import org.teavm.jso.canvas.CanvasGradient;
import org.teavm.jso.canvas.CanvasRenderingContext2D;
import org.teavm.jso.canvas.Path2D;

/**
 * Canvas 的小包裝，讓 Swing 版的繪製程式可以照著翻。
 * Canvas 讀不到目前的座標轉換，牌的厚度與貼圖快取都要用，所以這裡自己記一份，單位是實際像素。
 */
final class Gfx {

    private final CanvasRenderingContext2D context;
    private double a;
    private double b;
    private double c;
    private double d;
    private double e;
    private double f;

    /** scale 是每個座標單位佔幾個實際像素。 */
    Gfx(CanvasRenderingContext2D context, double scale) {
        this.context = context;
        setTransform(new double[] {scale, 0, 0, scale, 0, 0});
    }

    CanvasRenderingContext2D context() {
        return context;
    }

    double[] transform() {
        return new double[] {a, b, c, d, e, f};
    }

    void setTransform(double[] m) {
        a = m[0];
        b = m[1];
        c = m[2];
        d = m[3];
        e = m[4];
        f = m[5];
        context.setTransform(a, b, c, d, e, f);
    }

    void translate(double x, double y) {
        e += a * x + c * y;
        f += b * x + d * y;
        context.setTransform(a, b, c, d, e, f);
    }

    void scale(double x, double y) {
        a *= x;
        b *= x;
        c *= y;
        d *= y;
        context.setTransform(a, b, c, d, e, f);
    }

    void rotate(double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double nextA = a * cos + c * sin;
        double nextB = b * cos + d * sin;
        double nextC = c * cos - a * sin;
        double nextD = d * cos - b * sin;
        a = nextA;
        b = nextB;
        c = nextC;
        d = nextD;
        context.setTransform(a, b, c, d, e, f);
    }

    /** 目前座標轉了幾度。 */
    double rotation() {
        return Math.atan2(b, a);
    }

    void color(String color) {
        context.setFillStyle(color);
        context.setStrokeStyle(color);
    }

    void gradient(double x0, double y0, String from, double x1, double y1, String to) {
        CanvasGradient gradient = context.createLinearGradient(x0, y0, x1, y1);
        gradient.addColorStop(0, from);
        gradient.addColorStop(1, to);
        context.setFillStyle(gradient);
        context.setStrokeStyle(gradient);
    }

    /** 以原點為圓心、半徑 radius 的圓，用放射漸層填滿，和 Swing 的 RadialGradientPaint 一樣。stops 由內往外。 */
    void fillRadial(double radius, double[] stops, String[] colors) {
        CanvasGradient gradient = context.createRadialGradient(0, 0, 0, 0, 0, radius);
        for (int i = 0; i < stops.length; i++) {
            gradient.addColorStop(stops[i], colors[i]);
        }
        context.setFillStyle(gradient);
        context.beginPath();
        context.arc(0, 0, radius, 0, Math.PI * 2);
        context.fill();
    }

    /** 和 Swing 的 new BasicStroke(width) 一樣是方頭尖角。 */
    void stroke(double width) {
        context.setLineWidth(width);
        context.setLineCap("square");
        context.setLineJoin("miter");
    }

    void roundStroke(double width) {
        context.setLineWidth(width);
        context.setLineCap("round");
        context.setLineJoin("round");
    }

    void alpha(double alpha) {
        context.setGlobalAlpha(alpha);
    }

    double alpha() {
        return context.getGlobalAlpha();
    }

    /** arc 和 Swing 的 RoundRectangle2D 一樣是圓角的直徑。 */
    void roundRectPath(double x, double y, double w, double h, double arc) {
        // 視窗很小時牌的寬度可能是負的，arcTo 不接受負的半徑
        double r = Math.max(0, Math.min(arc / 2, Math.min(w, h) / 2));
        context.moveTo(x + r, y);
        context.arcTo(x + w, y, x + w, y + h, r);
        context.arcTo(x + w, y + h, x, y + h, r);
        context.arcTo(x, y + h, x, y, r);
        context.arcTo(x, y, x + w, y, r);
        context.closePath();
    }

    void fillRoundRect(double x, double y, double w, double h, double arc) {
        context.beginPath();
        roundRectPath(x, y, w, h, arc);
        context.fill();
    }

    void drawRoundRect(double x, double y, double w, double h, double arc) {
        context.beginPath();
        roundRectPath(x, y, w, h, arc);
        context.stroke();
    }

    void fillRect(double x, double y, double w, double h) {
        context.fillRect(x, y, w, h);
    }

    /** 以左上角與寬高指定的橢圓，和 Swing 的 Ellipse2D 一樣。 */
    void drawEllipse(double x, double y, double w, double h) {
        context.beginPath();
        context.ellipse(x + w / 2, y + h / 2, w / 2, h / 2, 0, 0, Math.PI * 2);
        context.stroke();
    }

    void drawLine(double x0, double y0, double x1, double y1) {
        context.beginPath();
        context.moveTo(x0, y0);
        context.lineTo(x1, y1);
        context.stroke();
    }

    void font(String font) {
        context.setFont(font);
    }

    double textWidth(String text) {
        return context.measureText(text).getWidth();
    }

    /** 從基準線開始畫，y 是基準線。 */
    void drawString(String text, double x, double y) {
        context.setTextAlign("left");
        context.setTextBaseline("alphabetic");
        context.fillText(text, x, y);
    }

    /** (x, y) 是文字的正中央。 */
    void drawCentered(String text, double x, double y) {
        context.setTextAlign("center");
        context.setTextBaseline("middle");
        context.fillText(text, x, y);
    }

    void fillEvenOdd(Path2D path) {
        fillEvenOdd(context, path);
    }

    @JSBody(params = {"context", "path"}, script = "context.fill(path, 'evenodd');")
    private static native void fillEvenOdd(CanvasRenderingContext2D context, Path2D path);
}
