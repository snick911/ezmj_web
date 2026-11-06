package ezmj.web;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.teavm.jso.dom.html.HTMLElement;

import ezmj.engine.GameView;
import ezmj.engine.RoundRecord;
import ezmj.engine.RoundResult;
import ezmj.model.Tile;

/** 這場牌局每一局的輸贏歷程。開著可以繼續打，局數多時可以捲動，表頭固定不動。 */
final class HistoryDialog extends Dialog {

    private static final int SEATS = 4;
    private static final int LEFT_COLUMNS = 3;
    private static final int FIRST_MONEY_COLUMN = 4;

    private final GameView game;
    private HTMLElement scroll;

    HistoryDialog(GameView game) {
        super(false);
        this.game = game;
        card.setClassName("card history-card");
        refresh();
    }

    /** 依目前打完的局數重畫，並捲到最新的一局。 */
    void refresh() {
        Dom.clear(card);
        List<RoundRecord> history = game.history();
        card.appendChild(Dom.text("h2", "headline", "輸贏歷程"));
        card.appendChild(Dom.text("div", "muted",
                history.isEmpty() ? "還沒有打完的局" : "共打 " + history.size() + " 局，把滑鼠停在結果上可以看牌型"));
        scroll = Dom.el("div", "history-scroll");
        if (!history.isEmpty()) {
            scroll.appendChild(table().element());
            card.appendChild(scroll);
        }
        card.appendChild(Dom.button("關閉", Theme.BUTTON, this::close));
        scrollToNewest();
    }

    /** 放上畫面之前量不到高度，所以第一次要等到 show 才捲得動。 */
    @Override
    void show() {
        super.show();
        scrollToNewest();
    }

    private void scrollToNewest() {
        scroll.setScrollTop(scroll.getScrollHeight());
    }

    private GridTable table() {
        GridTable table = new GridTable(LEFT_COLUMNS, -1);
        List<String> header = new ArrayList<>(List.of("局", "圈風", "結果", "牌型台"));
        for (int seat = 0; seat < SEATS; seat++) {
            header.add(game.player(seat).name());
        }
        header.add("我的累計");
        table.header(header.toArray(new String[0]));

        int[] totals = new int[SEATS];
        for (RoundRecord record : game.history()) {
            for (int seat = 0; seat < SEATS; seat++) {
                totals[seat] += record.deltas()[seat];
            }
            addRound(table, record, totals[0]);
        }
        addTotals(table, totals);
        return table;
    }

    private void addRound(GridTable table, RoundRecord record, int running) {
        RoundResult result = record.result();
        List<String> cells = new ArrayList<>(List.of(String.valueOf(record.number()), windText(game, record),
                resultText(game, result),
                result.isDrawnGame() ? "—" : String.valueOf(result.score().totalTai())));
        for (int seat = 0; seat < SEATS; seat++) {
            cells.add(ResultDialog.money(record.deltas()[seat]));
        }
        cells.add(ResultDialog.money(running));
        HTMLElement[] labels = table.row(false, cells.toArray(new String[0]));
        String score = scoreText(result);
        if (score != null) {
            labels[2].setAttribute("title", score);
        }
        for (int seat = 0; seat < SEATS; seat++) {
            Dom.color(labels[FIRST_MONEY_COLUMN + seat], ResultDialog.moneyColor(record.deltas()[seat]));
        }
        Dom.color(labels[FIRST_MONEY_COLUMN + SEATS], ResultDialog.moneyColor(running));
        labels[FIRST_MONEY_COLUMN].getStyle().setProperty("font-weight", "bold");
    }

    private void addTotals(GridTable table, int[] totals) {
        List<String> cells = new ArrayList<>(List.of("合計", "", "", ""));
        for (int total : totals) {
            cells.add(ResultDialog.money(total));
        }
        cells.add(ResultDialog.money(totals[0]));
        HTMLElement[] labels = table.row(true, cells.toArray(new String[0]));
        for (int seat = 0; seat < SEATS; seat++) {
            Dom.color(labels[FIRST_MONEY_COLUMN + seat], ResultDialog.moneyColor(totals[seat]));
        }
        Dom.color(labels[FIRST_MONEY_COLUMN + SEATS], ResultDialog.moneyColor(totals[0]));
    }

    /** 圈風與莊家，輸贏歷程的 PDF 也用。 */
    static String windText(GameView game, RoundRecord record) {
        return Tile.windName(record.roundWind()) + "風 莊" + game.player(record.dealer()).name()
                + (record.streak() > 0 ? " 連" + record.streak() : "");
    }

    static String resultText(GameView game, RoundResult result) {
        if (result.isDrawnGame()) {
            return "流局";
        }
        String winner = game.player(result.winner()).name();
        if (result.isFlowerWin()) {
            return result.isSelfDraw()
                    ? winner + " 八仙過海"
                    : winner + " 七搶一 " + game.player(result.from()).name() + " 的 " + result.winTile().label();
        }
        return result.isSelfDraw()
                ? winner + " 自摸 " + result.winTile().label()
                : winner + " 胡 " + game.player(result.from()).name() + " 的 " + result.winTile().label();
    }

    /** 滑鼠提示裡的牌型明細，PDF 寫在結果下面；流局沒有。 */
    static String scoreText(RoundResult result) {
        if (result.isDrawnGame()) {
            return null;
        }
        if (result.score().items().isEmpty()) {
            return "屁胡 0 台";
        }
        return result.score().items().stream()
                .map(item -> item.name() + " " + item.tai() + " 台")
                .collect(Collectors.joining("、"));
    }
}
