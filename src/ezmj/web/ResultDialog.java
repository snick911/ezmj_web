package ezmj.web;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.teavm.jso.dom.html.HTMLButtonElement;
import org.teavm.jso.dom.html.HTMLElement;

import ezmj.engine.GameView;
import ezmj.engine.RoundResult;
import ezmj.engine.RoundView;
import ezmj.engine.Settlement;
import ezmj.model.Meld;
import ezmj.model.Player;
import ezmj.model.Tile;
import ezmj.rules.ScoreItem;
import ezmj.rules.ScoreResult;

/** 一局結束的結果視窗：胡牌牌型、台數明細、各家輸贏與四家手牌。時間到自動關閉。 */
final class ResultDialog extends Dialog {

    private final HTMLButtonElement next;
    private int countdownTimer = -1;

    ResultDialog(GameView game, RoundView round, Settlement settlement, String buttonText, int autoCloseSeconds) {
        super(true);
        card.setClassName("card result-card");

        RoundResult result = round.result();
        card.appendChild(Dom.text("h2", "headline", headline(round, result)));

        if (result.isDrawnGame()) {
            card.appendChild(drawnTable(game).element());
        } else {
            card.appendChild(handStrip(round.player(result.winner()), result.winTile(), 36, 50, 9));
            HTMLElement tables = Dom.el("div", "result-tables");
            tables.appendChild(scoreTable(round, settlement).element());
            tables.appendChild(payTable(game, round, settlement).element());
            card.appendChild(tables);
            card.appendChild(Dom.text("div", "muted", "每筆付款＝底 " + game.basePay() + " ＋ 台數 × " + game.taiPay()
                    + (countsDealer(round, settlement) ? "，莊家台只算與莊家相關的付款" : "")));
        }
        card.appendChild(otherHands(round));

        next = Dom.button(buttonText, Theme.RED, this::finish);
        card.appendChild(next);
        startAutoClose(buttonText, autoCloseSeconds);
    }

    @Override
    void show() {
        super.show();
        next.focus();
    }

    @Override
    void close() {
        if (countdownTimer >= 0) {
            Browser.cancelEvery(countdownTimer);
            countdownTimer = -1;
        }
        super.close();
    }

    private void startAutoClose(String buttonText, int seconds) {
        int[] left = {seconds};
        next.setInnerText(buttonText + "（" + left[0] + "）");
        countdownTimer = Browser.every(1000, () -> {
            left[0]--;
            if (left[0] > 0) {
                next.setInnerText(buttonText + "（" + left[0] + "）");
            } else {
                finish();
            }
        });
    }

    private static String headline(RoundView round, RoundResult result) {
        if (result.isDrawnGame()) {
            return "流局";
        }
        String winner = round.player(result.winner()).name();
        if (result.isFlowerWin()) {
            return result.isSelfDraw()
                    ? winner + " 集滿八張花牌，八仙過海"
                    : winner + " 七搶一，搶走 " + round.player(result.from()).name() + " 的 " + result.winTile().label();
        }
        return result.isSelfDraw()
                ? winner + " 自摸 " + result.winTile().label()
                : winner + " 胡牌，" + round.player(result.from()).name() + " 放槍 " + result.winTile().label();
    }

    /** 其他家的手牌，打完才看得到別人在做什麼。胡牌者上面已經有了；靠花牌胡的上面只有花牌，這裡也列手牌。 */
    private static HTMLElement otherHands(RoundView round) {
        RoundResult result = round.result();
        HTMLElement hands = Dom.el("div", "other-hands");
        for (int seat = 0; seat < 4; seat++) {
            if (seat == result.winner() && !result.isFlowerWin()) {
                continue;
            }
            Player player = round.player(seat);
            String mark = !result.isDrawnGame() && !result.isSelfDraw() && seat == result.from() ? "（放槍）" : "";
            hands.appendChild(Dom.text("div", seat == 0 ? "bold" : "", player.name() + mark));
            hands.appendChild(handStrip(player, null, 24, 33, 6));
        }
        return hands;
    }

    /** 一家的完整牌面。winTile 是 null 表示這家沒有胡，全部排在一起。靠花牌胡的不看手牌，只列花牌。 */
    private static HTMLElement handStrip(Player player, Tile winTile, double w, double h, double spacing) {
        double scale = Theme.tileScale();
        List<TileStrip.Group> groups = new ArrayList<>();
        if (winTile != null && winTile.isFlower()) {
            for (Tile flower : player.flowers()) {
                groups.add(new TileStrip.Group(List.of(flower), flower.equals(winTile)));
            }
            return TileStrip.of(groups, w * scale, h * scale, 0);
        }
        for (Meld meld : player.melds()) {
            groups.add(new TileStrip.Group(meld.tiles(), false));
        }
        List<Tile> hand = new ArrayList<>(player.concealed());
        hand.remove(winTile);
        groups.add(new TileStrip.Group(hand, false));
        if (winTile != null) {
            groups.add(new TileStrip.Group(List.of(winTile), true));
        }
        return TileStrip.of(groups, w * scale, h * scale, spacing * scale);
    }

    private static GridTable scoreTable(RoundView round, Settlement settlement) {
        ScoreResult score = round.result().score();
        GridTable table = new GridTable(1);
        table.header("牌型", "台數");
        for (ScoreItem item : score.items()) {
            table.row(false, item.name(), String.valueOf(item.tai()));
        }
        if (score.items().isEmpty()) {
            table.row(false, "屁胡", "0");
        }
        for (HTMLElement cell : table.row(true, "牌型合計", String.valueOf(score.totalTai()))) {
            Dom.color(cell, Theme.GOLD);
        }
        if (countsDealer(round, settlement)) {
            table.row(false, "＋莊家", "1");
            if (round.streak() > 0) {
                table.row(false, "＋連" + round.streak() + "拉" + round.streak(), String.valueOf(2 * round.streak()));
            }
        }
        return table;
    }

    /** 有人的付款比牌型台數多，就是這局有算到莊家台。 */
    private static boolean countsDealer(RoundView round, Settlement settlement) {
        return Arrays.stream(settlement.tais()).anyMatch(tai -> tai > round.result().score().totalTai());
    }

    private static GridTable payTable(GameView game, RoundView round, Settlement settlement) {
        GridTable table = new GridTable(2);
        table.header("玩家", "結果", "台數", "輸贏", "籌碼");
        for (int seat = 0; seat < 4; seat++) {
            Player player = game.player(seat);
            int delta = settlement.deltas()[seat];
            String tai = delta < 0 ? String.valueOf(settlement.tais()[seat]) : "—";
            HTMLElement[] cells = table.row(seat == 0,
                    player.name() + (seat == round.dealer() ? "（莊）" : ""), outcome(round.result(), seat),
                    tai, money(delta), String.valueOf(player.chips()));
            Dom.color(cells[3], moneyColor(delta));
        }
        return table;
    }

    private static GridTable drawnTable(GameView game) {
        GridTable table = new GridTable(1);
        table.header("玩家", "籌碼");
        for (int seat = 0; seat < 4; seat++) {
            Player player = game.player(seat);
            table.row(seat == 0, player.name(), String.valueOf(player.chips()));
        }
        return table;
    }

    private static String outcome(RoundResult result, int seat) {
        if (seat == result.winner()) {
            return result.isSelfDraw() ? "自摸" : "胡牌";
        }
        if (result.isSelfDraw()) {
            return "被自摸";
        }
        return seat == result.from() ? "放槍" : "";
    }

    static String money(int delta) {
        return (delta > 0 ? "+" : "") + delta;
    }

    static String moneyColor(int delta) {
        if (delta == 0) {
            return Theme.MUTED;
        }
        return delta > 0 ? Theme.GOLD : Theme.TEXT;
    }
}
