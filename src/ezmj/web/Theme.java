package ezmj.web;

import org.teavm.jso.dom.html.HTMLDocument;

/** 共用的顏色與字型，數值照 Swing 版的 Theme。按鈕與對話框的樣式在 style.css。 */
final class Theme {

    static final String FELT = "#145A43";
    static final String FELT_DARK = "#0B3B2C";
    static final String PANEL = "#122620";
    static final String PANEL_LIGHT = "#1E3830";
    static final String TEXT = "#F2EFE4";
    static final String MUTED = "#A9BDB3";
    static final String GOLD = "#E8C05A";
    static final String RED = "#C93A32";
    static final String BUTTON = "#2C6E58";
    /** 莊家與連莊的紅，和 style.css 的 .dealer-badge 一樣，照 Swing 版的 DealerBadge.RED。 */
    static final String DEALER_RED = "#E5483D";

    private static final String FAMILY = "\"Microsoft JhengHei\", \"PingFang TC\", \"Noto Sans CJK TC\", \"Noto Sans TC\", sans-serif";
    /** 系統有楷書就用系統的，和 Swing 版一樣；沒有時用網頁帶的字型，只有牌面會用到的字。 */
    private static final String BRUSH_FAMILY = "\"DFKai-SB\", \"標楷體\", \"BiauKai\", \"Kaiti TC\", \"AR PL UKai TW\", \"KaiTi\", \"EZMJ Kai\", "
            + FAMILY;

    private static double fontScale = 1;
    private static double tileScale = 1;

    private Theme() {
    }

    /** 牌面上的毛筆字。 */
    static String brushFont(double size) {
        return "bold " + size + "px " + BRUSH_FAMILY;
    }

    /** 會隨字體大小設定縮放的字型。 */
    static String font(boolean bold, double size) {
        return fixedFont(bold, size * fontScale);
    }

    /** 不縮放的字型，用在要剛好塞進牌面的文字。 */
    static String fixedFont(boolean bold, double size) {
        return (bold ? "bold " : "") + size + "px " + FAMILY;
    }

    static double tileScale() {
        return tileScale;
    }

    static void setTilePercent(int percent) {
        tileScale = percent / 100.0;
    }

    /** HTML 的字與側欄大小都看這個變數。 */
    static void setFontPercent(int percent) {
        fontScale = percent / 100.0;
        HTMLDocument.current().getDocumentElement().getStyle().setProperty("--font-scale", String.valueOf(fontScale));
    }
}
