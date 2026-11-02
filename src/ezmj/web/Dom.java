package ezmj.web;

import org.teavm.jso.dom.html.HTMLButtonElement;
import org.teavm.jso.dom.html.HTMLDocument;
import org.teavm.jso.dom.html.HTMLElement;

/**
 * 組 HTML 元件的小工具。文字一律用 innerText，絕對不要用 innerHTML：
 * 玩家的名字來自網路，如果被當成 HTML，token 就可能被偷走。
 */
final class Dom {

    private Dom() {
    }

    static HTMLElement el(String tag, String cssClass) {
        HTMLElement element = HTMLDocument.current().createElement(tag);
        element.setClassName(cssClass);
        return element;
    }

    static HTMLElement text(String tag, String cssClass, String text) {
        HTMLElement element = el(tag, cssClass);
        element.setInnerText(text);
        return element;
    }

    static HTMLButtonElement button(String text, String color, Runnable action) {
        HTMLButtonElement button = (HTMLButtonElement) el("button", "btn");
        button.setType("button");
        button.setInnerText(text);
        button.getStyle().setProperty("background-color", color);
        button.addEventListener("click", event -> action.run());
        return button;
    }

    static void color(HTMLElement element, String color) {
        element.getStyle().setProperty("color", color);
    }

    static void clear(HTMLElement element) {
        while (element.getFirstChild() != null) {
            element.removeChild(element.getFirstChild());
        }
    }

    static void visible(HTMLElement element, boolean visible) {
        element.getStyle().setProperty("display", visible ? "" : "none");
    }

    /** 從畫面上拿掉，沒有放上去也沒關係。 */
    static void remove(HTMLElement element) {
        if (element.getParentNode() != null) {
            element.getParentNode().removeChild(element);
        }
    }
}
