package ezmj.web;

import java.util.List;

import org.teavm.jso.browser.Window;
import org.teavm.jso.canvas.CanvasRenderingContext2D;
import org.teavm.jso.dom.html.HTMLCanvasElement;
import org.teavm.jso.dom.html.HTMLDocument;

import ezmj.model.Tile;

/** 一排牌畫成一張小圖，結果視窗與說明都用它。 */
final class TileStrip {

    /** 圖上的一組牌，組與組之間留空，marked 的那組會加紅框。 */
    record Group(List<Tile> tiles, boolean marked) {
    }

    private TileStrip() {
    }

    static HTMLCanvasElement of(List<Group> groups, double tileW, double tileH, double gap) {
        int count = groups.stream().mapToInt(group -> group.tiles().size()).sum();
        int width = (int) Math.ceil(count * tileW + (groups.size() - 1) * gap) + 4;
        int height = (int) Math.ceil(tileH) + 6;
        double ratio = Math.max(1, Window.current().getDevicePixelRatio());

        HTMLCanvasElement canvas = (HTMLCanvasElement) HTMLDocument.current().createElement("canvas");
        canvas.setWidth((int) (width * ratio));
        canvas.setHeight((int) (height * ratio));
        canvas.getStyle().setProperty("width", width + "px");
        canvas.getStyle().setProperty("height", height + "px");
        canvas.setClassName("tile-strip");

        Gfx g = new Gfx((CanvasRenderingContext2D) canvas.getContext("2d"), ratio);
        double x = 2;
        for (Group group : groups) {
            for (Tile tile : group.tiles()) {
                TilePainter.paintFace(g, tile, x, 2, tileW - 1, tileH);
                if (group.marked()) {
                    TilePainter.paintHighlight(g, x, 2, tileW - 1, tileH, Theme.RED);
                }
                x += tileW;
            }
            x += gap;
        }
        return canvas;
    }
}
