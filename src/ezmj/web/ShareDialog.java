package ezmj.web;

import org.teavm.jso.dom.html.HTMLButtonElement;
import org.teavm.jso.dom.html.HTMLCanvasElement;
import org.teavm.jso.dom.html.HTMLElement;

/**
 * 結果圖的預覽：看得到要分享的那張圖，可以分享給其他 App 或存成圖檔。
 * 瀏覽器只准在按下去的當下分享，所以視窗一打開就先把圖轉成 PNG。不能分享檔案的瀏覽器只有「下載圖片」。
 */
final class ShareDialog extends Dialog {

    private final HTMLButtonElement close;

    ShareDialog(GameReport report) {
        super(true);
        card.setClassName("card share-card");
        HTMLCanvasElement image = report.shareImage();
        image.setClassName("share-preview");
        image.setAttribute("role", "img");
        image.setAttribute("aria-label", "牌局結果圖");
        Browser.preparePng(image);

        card.appendChild(Dom.text("h2", "headline", "分享結果圖"));
        card.appendChild(image);
        boolean canShare = Browser.canShareFiles();
        card.appendChild(Dom.text("div", "muted", canShare
                ? "按「分享」可以傳到 LINE、Messenger 等 App，或存成圖檔。"
                : "這個瀏覽器不能直接分享，先下載圖片再傳出去。"));

        HTMLElement buttons = Dom.el("div", "button-row");
        if (canShare) {
            buttons.appendChild(Dom.button("分享", Theme.RED, () -> {
                if (!Browser.sharePng(image, report.imageFileName(), "EZMJ 牌局結果", report.shareText() + "\n" + Browser.siteUrl())) {
                    // 圖還沒轉好或這張圖不能分享，就存成檔案
                    Browser.downloadPng(image, report.imageFileName());
                }
            }));
        }
        buttons.appendChild(Dom.button("下載圖片", canShare ? Theme.BUTTON : Theme.RED,
                () -> Browser.downloadPng(image, report.imageFileName())));
        close = Dom.button("關閉", Theme.PANEL_LIGHT, this::close);
        buttons.appendChild(close);
        card.appendChild(buttons);
    }

    @Override
    void show() {
        super.show();
        close.focus();
    }
}
