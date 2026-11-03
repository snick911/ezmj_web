package ezmj.web;

import org.teavm.jso.dom.html.HTMLElement;

/** 欄位對齊的表格。前 leftColumns 欄是文字靠左，其餘是數字靠右。多出來的寬度給 stretchColumn。 */
final class GridTable {

    private final HTMLElement root = Dom.el("table", "grid");
    private final HTMLElement head = Dom.el("thead", "");
    private final HTMLElement body = Dom.el("tbody", "");
    private final int leftColumns;
    private final int stretchColumn;

    GridTable(int leftColumns) {
        this(leftColumns, 0);
    }

    GridTable(int leftColumns, int stretchColumn) {
        this.leftColumns = leftColumns;
        this.stretchColumn = stretchColumn;
        root.appendChild(head);
        root.appendChild(body);
    }

    HTMLElement element() {
        return root;
    }

    HTMLElement[] header(String... texts) {
        return add(head, "th", "", texts);
    }

    HTMLElement[] row(boolean bold, String... texts) {
        return add(body, "td", bold ? "bold" : "", texts);
    }

    private HTMLElement[] add(HTMLElement section, String tag, String rowClass, String[] texts) {
        HTMLElement row = Dom.el("tr", rowClass);
        HTMLElement[] cells = new HTMLElement[texts.length];
        for (int column = 0; column < texts.length; column++) {
            String align = column < leftColumns ? "left" : "right";
            cells[column] = Dom.text(tag, column == stretchColumn ? align + " stretch" : align, texts[column]);
            row.appendChild(cells[column]);
        }
        section.appendChild(row);
        return cells;
    }
}
