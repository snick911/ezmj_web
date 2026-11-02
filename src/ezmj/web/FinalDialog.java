package ezmj.web;

import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Collectors;

import org.teavm.jso.dom.html.HTMLButtonElement;
import org.teavm.jso.dom.html.HTMLElement;

import ezmj.engine.GameView;
import ezmj.model.GameRecord;
import ezmj.model.Player;

/** 牌局結束的視窗：結束原因與四家的最終排名和整場成績。 */
final class FinalDialog extends Dialog {

    private final HTMLButtonElement close;

    FinalDialog(GameView game, String closeLabel, Runnable onHistory) {
        super(true);
        Player[] ranking = {game.player(0), game.player(1), game.player(2), game.player(3)};
        Arrays.sort(ranking, Comparator.comparingInt(Player::chips).reversed());

        card.appendChild(Dom.text("h2", "headline", headline(ranking)));
        card.appendChild(Dom.text("div", "muted", "共打 " + game.roundsPlayed() + " 局，流局 " + game.drawnGames() + " 次"));
        card.appendChild(table(game, ranking).element());
        card.appendChild(Dom.text("div", "muted", "胡牌次數包含自摸"));

        close = Dom.button(closeLabel, Theme.RED, this::finish);
        HTMLElement buttons = Dom.el("div", "button-row");
        buttons.appendChild(close);
        buttons.appendChild(Dom.button("輸贏歷程", Theme.BUTTON, onHistory));
        card.appendChild(buttons);
    }

    @Override
    void show() {
        super.show();
        close.focus();
    }

    private static String headline(Player[] ranking) {
        String broke = Arrays.stream(ranking)
                .filter(player -> player.chips() <= 0)
                .map(Player::name)
                .collect(Collectors.joining("、"));
        return broke.isEmpty() ? "四圈打完，牌局結束" : broke + " 輸光籌碼，牌局提早結束";
    }

    private static GridTable table(GameView game, Player[] ranking) {
        GridTable table = new GridTable(1);
        table.header("名次　玩家", "胡牌", "自摸", "放槍", "輸贏", "籌碼");
        int rank = 0;
        for (int i = 0; i < ranking.length; i++) {
            Player player = ranking[i];
            // 籌碼相同就是同一名
            if (i == 0 || player.chips() != ranking[i - 1].chips()) {
                rank = i + 1;
            }
            GameRecord record = game.record(player.seat(), "");
            HTMLElement[] cells = table.row(player.seat() == 0, rank + "　" + player.name(),
                    String.valueOf(record.wins()), String.valueOf(record.selfDraws()), String.valueOf(record.dealIns()),
                    ResultDialog.money(record.winnings()), String.valueOf(player.chips()));
            Dom.color(cells[4], ResultDialog.moneyColor(record.winnings()));
        }
        return table;
    }
}
