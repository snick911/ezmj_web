package ezmj.web;

import org.teavm.jso.dom.html.HTMLButtonElement;
import org.teavm.jso.dom.html.HTMLDocument;
import org.teavm.jso.dom.html.HTMLElement;

/**
 * 蓋在畫面上的視窗。modal 的會擋住後面；不 modal 的可以邊看邊打。
 * 瀏覽器不能像 Swing 那樣停在 setVisible 等視窗關掉，所以要接著做的事放在 onClose。
 * close 是程式自己收掉，不會呼叫 onClose；只有玩家按了或時間到才會，也就是 finish。
 */
class Dialog {

    final HTMLElement card = Dom.el("div", "card");
    private final HTMLElement layer;
    private Runnable onClose = () -> { };
    private boolean closed;

    Dialog(boolean modal) {
        layer = Dom.el("div", modal ? "backdrop" : "floating");
        layer.appendChild(card);
    }

    void setOnClose(Runnable onClose) {
        this.onClose = onClose;
    }

    void show() {
        HTMLDocument.current().getBody().appendChild(layer);
    }

    /** 程式自己收掉。 */
    void close() {
        closed = true;
        Dom.remove(layer);
    }

    boolean isOpen() {
        return !closed && layer.getParentNode() != null;
    }

    /** 玩家看完了，繼續接下來的事。 */
    final void finish() {
        if (!closed) {
            close();
            onClose.run();
        }
    }

    /** 只有一段文字和一顆按鈕。 */
    static void message(String text) {
        Dialog dialog = new Dialog(true);
        dialog.card.appendChild(Dom.text("p", "dialog-text", text));
        HTMLButtonElement ok = Dom.button("確定", Theme.BUTTON, dialog::finish);
        dialog.card.appendChild(ok);
        dialog.show();
        ok.focus();
    }

    /** 確定才做 action。 */
    static void confirm(String text, Runnable action) {
        Dialog dialog = new Dialog(true);
        dialog.card.appendChild(Dom.text("p", "dialog-text", text));
        HTMLElement buttons = Dom.el("div", "button-row");
        buttons.appendChild(Dom.button("確定", Theme.RED, () -> {
            dialog.close();
            action.run();
        }));
        HTMLButtonElement cancel = Dom.button("取消", Theme.PANEL_LIGHT, dialog::close);
        buttons.appendChild(cancel);
        dialog.card.appendChild(buttons);
        dialog.show();
        cancel.focus();
    }

    /** 網頁比服務器舊，要重新整理才拿得到新版。 */
    static void askReload(String text) {
        Dialog dialog = new Dialog(true);
        dialog.card.appendChild(Dom.text("p", "dialog-text", text));
        HTMLButtonElement reload = Dom.button("重新整理", Theme.RED, Browser::reload);
        dialog.card.appendChild(reload);
        dialog.show();
        reload.focus();
    }
}
