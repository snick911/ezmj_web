package ezmj.web;

import org.teavm.jso.dom.html.HTMLButtonElement;
import org.teavm.jso.dom.html.HTMLDocument;
import org.teavm.jso.dom.html.HTMLElement;

/**
 * 開房間、開局這類要等服務器回應的時候蓋住整個畫面：轉圈、一行字與說明，還在連線時可以取消。
 * 擋住畫面也就不會再按一次。服務器很快就回應的話看不到，淡入有延遲，見 style.css 的 .waiting。
 */
final class WaitingOverlay {

    private final HTMLElement layer = Dom.el("div", "waiting");
    private final HTMLElement title = Dom.text("div", "waiting-title", " ");
    /** 沒有說明時是空的，整行藏起來。 */
    private final HTMLElement detail = Dom.text("div", "muted waiting-detail", "");
    private final HTMLButtonElement cancel;
    private Runnable onCancel;

    WaitingOverlay() {
        HTMLElement card = Dom.el("div", "card waiting-card");
        card.setAttribute("role", "status");
        card.setAttribute("aria-live", "polite");
        card.appendChild(Dom.el("div", "spinner"));
        card.appendChild(title);
        card.appendChild(detail);
        cancel = Dom.button("取消", Theme.PANEL_LIGHT, () -> {
            if (onCancel != null) {
                onCancel.run();
            }
        });
        card.appendChild(cancel);
        layer.appendChild(card);
    }

    /** onCancel 是 null 就沒有取消。已經顯示時只換字，淡入不會重來，畫面不會閃。 */
    void show(String text, Runnable onCancel) {
        this.onCancel = onCancel;
        title.setInnerText(text);
        Dom.visible(cancel, onCancel != null);
        if (!isShowing()) {
            detail.setInnerText("");
            HTMLDocument.current().getBody().appendChild(layer);
        }
    }

    /** 標題下面的小字，例如服務器正在醒來。 */
    void setDetail(String text) {
        detail.setInnerText(text);
    }

    void hide() {
        onCancel = null;
        Dom.remove(layer);
    }

    boolean isShowing() {
        return layer.getParentNode() != null;
    }
}
