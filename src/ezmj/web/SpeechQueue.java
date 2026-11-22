package ezmj.web;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 喊牌與司儀的先後。吃碰槓胡、自摸、聽牌與司儀（下面叫「一定要念的話」）彼此不打斷，排在後面一句念完才念下一句，
 * 和 Swing 版的 VoiceLines.playFile 一樣。牌名只喊最新的一張：還沒喊完又有新的牌名就打斷它，一定要念的話還在排或在念時
 * 先等著，只留最新的一張，念完才喊；又有一定要念的話進來，等著的牌名就不喊了。
 * <p>
 * 和 Swing 版不同的有兩點，都是因為瀏覽器念一句連前後的空白要一秒多，不能像 Swing 版修掉空白，排隊會越落越後面：
 * 一定要念的話會打斷正在念的牌名（牌就擺在桌上，被打斷沒關係）；吃碰槓排了 CALL_STALE_MS 還沒輪到就不念了。
 * 聽牌、胡、自摸與司儀一定念到。
 * 以前每一句都打斷上一句，聽牌時「聽牌」一喊出來就被下一家打牌的牌名切掉，聽起來像沒念。
 * <p>
 * 自己排隊，一次只交一句給 Speaker，念完才交下一句，排隊的順序、誰打斷誰都在這裡決定。瀏覽器偶爾不通知說完了，
 * 一句念了 MAX_SPEAK_MS 還沒說完，下一次要念時就停掉它往下念。
 * 只管先後，不碰瀏覽器，念的東西是 T，實際出聲交給 Speaker，所以可以在 JVM 上測。
 */
final class SpeechQueue<T> {

    /** 實際出聲的地方。 */
    interface Speaker<T> {
        /** 念這一句。念完、被打斷或念不出來時呼叫 done，也可能永遠不呼叫。 */
        void speak(T line, Runnable done);

        /** 停掉正在念的。 */
        void cancel();

        /** 毫秒，只用來算經過多久。 */
        double now();
    }

    /** 一句最多念這麼久。最長的是司儀的「連莊，連二十拉二十」，三秒內念得完。 */
    static final int MAX_SPEAK_MS = 6_000;
    /**
     * 吃碰槓排了這麼久還沒輪到就不念了。快的牌局一直有人吃碰時，一句將近一秒的話會越排越長，落後好幾秒才念出來反而誤導；
     * 吃碰槓是一下子的事，看牌桌就知道。聽牌、胡、自摸與司儀照舊一定念。
     */
    static final int CALL_STALE_MS = 1_500;

    /** 排著的一定要念的話。mayGoStale 的是吃碰槓，排太久就不念。 */
    private static final class Waiting<T> {
        final T line;
        final boolean mayGoStale;
        final double since;

        Waiting(T line, boolean mayGoStale, double since) {
            this.line = line;
            this.mayGoStale = mayGoStale;
            this.since = since;
        }
    }

    /** 正在念的一句。每次念都是新的物件，被打斷的那句之後才說念完也認得出來。 */
    private static final class Speaking<T> {
        final T line;
        final boolean must;
        final double since;

        Speaking(T line, boolean must, double since) {
            this.line = line;
            this.must = must;
            this.since = since;
        }
    }

    private final Speaker<T> speaker;
    /** 正在念的，沒有是 null。 */
    private Speaking<T> speaking;
    /** 排著等前一句念完的一定要念的話。 */
    private final Deque<Waiting<T>> mustWait = new ArrayDeque<>();
    /** 等著一定要念的話念完再喊的牌名，沒有是 null。 */
    private T tileWait;

    SpeechQueue(Speaker<T> speaker) {
        this.speaker = speaker;
    }

    /** 牌名：沒在念或念的是牌名就立刻喊（打斷上一張），一定要念的話還沒念完就等著。 */
    void tile(T line) {
        expire();
        if (speaking == null) {
            start(line, false);
        } else if (!speaking.must) {
            speaker.cancel();
            start(line, false);
        } else {
            tileWait = line;
        }
    }

    /** 聽牌、胡、自摸與司儀：沒在念或念的是牌名就立刻念（打斷牌名），前面還有一定要念的話就排在後面，一定念到。 */
    void mustSay(T line) {
        say(line, false);
    }

    /** 吃碰槓：和 mustSay 一樣，只是排了 CALL_STALE_MS 還沒輪到就不念了。 */
    void call(T line) {
        say(line, true);
    }

    private void say(T line, boolean mayGoStale) {
        expire();
        tileWait = null;
        if (speaking == null) {
            start(line, true);
        } else if (!speaking.must) {
            speaker.cancel();
            start(line, true);
        } else {
            mustWait.add(new Waiting<>(line, mayGoStale, speaker.now()));
        }
    }

    private void start(T line, boolean must) {
        Speaking<T> now = new Speaking<>(line, must, speaker.now());
        speaking = now;
        speaker.speak(line, () -> finished(now));
    }

    private void finished(Speaking<T> done) {
        // 被打斷或當作念完的那句，之後才通知的不算
        if (done != speaking) {
            return;
        }
        speaking = null;
        next();
    }

    private void next() {
        double now = speaker.now();
        while (!mustWait.isEmpty()) {
            Waiting<T> waiting = mustWait.poll();
            if (!waiting.mayGoStale || now - waiting.since < CALL_STALE_MS) {
                start(waiting.line, true);
                return;
            }
        }
        if (tileWait != null) {
            T tile = tileWait;
            tileWait = null;
            start(tile, false);
        }
    }

    /** 念太久了：瀏覽器沒通知說完，或語音卡住了。停掉它往下念；等著的牌名已經過時，不喊了。 */
    private void expire() {
        if (speaking != null && speaker.now() - speaking.since >= MAX_SPEAK_MS) {
            speaking = null;
            tileWait = null;
            speaker.cancel();
            next();
        }
    }
}
