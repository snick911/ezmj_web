package ezmj.web;

import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Collectors;

import org.teavm.jso.dom.html.HTMLButtonElement;
import org.teavm.jso.dom.html.HTMLElement;

import ezmj.engine.GameView;
import ezmj.model.GameRecord;
import ezmj.model.Player;

/**
 * 牌局結束的視窗：結束原因與四家的最終排名和整場成績。
 * 可以把結果畫成一張圖分享出去，也可以把每一局的輸贏存成 PDF。
 */
final class FinalDialog extends Dialog {

    private final HTMLButtonElement close;
    private final GameReport report;
    private ShareDialog share;

    /** roomName 是房間名稱，寫在結果圖與 PDF 上；沒有就是空字串。 */
    FinalDialog(GameView game, String roomName, String closeLabel, Runnable onHistory) {
        super(true);
        Player[] ranking = ranking(game);
        report = new GameReport(game, roomName);

        card.appendChild(Dom.text("h2", "headline", headline(ranking)));
        card.appendChild(Dom.text("div", "muted", "共打 " + game.roundsPlayed() + " 局，流局 " + game.drawnGames() + " 次"));
        card.appendChild(table(game, ranking).element());
        card.appendChild(Dom.text("div", "muted", "胡牌次數包含自摸"));

        close = Dom.button(closeLabel, Theme.RED, this::finish);
        HTMLElement buttons = Dom.el("div", "button-row");
        buttons.appendChild(close);
        buttons.appendChild(Dom.button("輸贏歷程", Theme.BUTTON, onHistory));
        buttons.appendChild(Dom.button("分享結果圖", Theme.BUTTON, this::showShare));
        buttons.appendChild(Dom.button("下載輸贏 PDF", Theme.BUTTON, this::downloadPdf));
        card.appendChild(buttons);
    }

    @Override
    void show() {
        super.show();
        close.focus();
    }

    /** 下一場開打時程式會收掉這個視窗，分享的視窗也一起收。 */
    @Override
    void close() {
        if (share != null) {
            share.close();
            share = null;
        }
        super.close();
    }

    private void showShare() {
        if (share != null && share.isOpen()) {
            return;
        }
        share = new ShareDialog(report);
        share.show();
    }

    private void downloadPdf() {
        try {
            Browser.downloadBase64(report.pdfBase64(), "application/pdf", report.pdfFileName());
        } catch (RuntimeException e) {
            Browser.warn("產生 PDF 失敗：" + e.getMessage());
            Dialog.message("產生 PDF 失敗，請再試一次。");
        }
    }

    /** 依籌碼由多到少。 */
    static Player[] ranking(GameView game) {
        Player[] ranking = {game.player(0), game.player(1), game.player(2), game.player(3)};
        Arrays.sort(ranking, Comparator.comparingInt(Player::chips).reversed());
        return ranking;
    }

    /** 和 ranking 同樣順序的名次，籌碼相同就是同一名。 */
    static int[] ranks(Player[] ranking) {
        int[] ranks = new int[ranking.length];
        for (int i = 0; i < ranking.length; i++) {
            ranks[i] = i == 0 || ranking[i].chips() != ranking[i - 1].chips() ? i + 1 : ranks[i - 1];
        }
        return ranks;
    }

    static String headline(Player[] ranking) {
        String broke = Arrays.stream(ranking)
                .filter(player -> player.chips() <= 0)
                .map(Player::name)
                .collect(Collectors.joining("、"));
        return broke.isEmpty() ? "四圈打完，牌局結束" : broke + " 輸光籌碼，牌局提早結束";
    }

    private static GridTable table(GameView game, Player[] ranking) {
        GridTable table = new GridTable(1);
        table.header("名次　玩家", "胡牌", "自摸", "放槍", "輸贏", "籌碼");
        int[] ranks = ranks(ranking);
        for (int i = 0; i < ranking.length; i++) {
            Player player = ranking[i];
            GameRecord record = game.record(player.seat(), "");
            HTMLElement[] cells = table.row(player.seat() == 0, ranks[i] + "　" + player.name(),
                    String.valueOf(record.wins()), String.valueOf(record.selfDraws()), String.valueOf(record.dealIns()),
                    ResultDialog.money(record.winnings()), String.valueOf(player.chips()));
            Dom.color(cells[4], ResultDialog.moneyColor(record.winnings()));
        }
        return table;
    }
}
