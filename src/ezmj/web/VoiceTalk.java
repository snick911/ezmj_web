package ezmj.web;

import java.util.function.Consumer;

import org.teavm.jso.webaudio.AudioContext;

import ezmj.online.VoiceClip;

/**
 * 按住說話：按下開始錄音，放開送出；在按鈕外面放開就取消，錄滿 10 秒自動送出。
 * 不到 0.3 秒的不送，告訴他要按住。按下去之前先看自己的頻率限制，和服務器的一樣，免得說完了才被拒絕。
 * 按鈕在 TablePanel，這裡只管錄音與送出，對應 Swing 版的同名類別。
 */
final class VoiceTalk {

    /** 按鈕與提示，由 TablePanel 提供。 */
    interface View {
        /** 按鈕上的字；recording 時按鈕變紅。 */
        void talkState(String label, boolean recording);

        /** 送不出去的原因，和聊天的提示放在同一個地方。 */
        void talkNotice(String text);
    }

    private static final int TICK_MS = 200;

    private final Consumer<byte[]> sender;
    private final VoiceClip.Limiter limiter = new VoiceClip.Limiter();
    private View view;
    private VoiceRecorder recorder;
    private boolean ready;
    private double readyAt;
    private int ticker = -1;

    /** sender 收到壓好的語音，負責送出去。 */
    VoiceTalk(Consumer<byte[]> sender) {
        this.sender = sender;
    }

    void attach(View view) {
        this.view = view;
    }

    boolean isRecording() {
        return recorder != null;
    }

    /** 按下說話的按鈕。要在按下的當下呼叫，瀏覽器才會問要不要允許麥克風。 */
    void press() {
        if (recorder != null || view == null) {
            return;
        }
        long waitMs = limiter.waitMs((long) Browser.now());
        if (waitMs > 0) {
            view.talkNotice(VoiceClip.tooFastText(waitMs));
            return;
        }
        AudioOut.unlock();
        AudioContext context = AudioOut.context();
        if (context == null) {
            view.talkNotice(VoiceRecorder.explain("unsupported"));
            return;
        }
        ready = false;
        VoiceRecorder[] started = new VoiceRecorder[1];
        started[0] = VoiceRecorder.start(context, error -> onReady(started[0], error));
        recorder = started[0];
        view.talkState(VoiceClip.PREPARING_LABEL, true);
    }

    /** 放開。send 是在按鈕上放開，false 是移到外面放開或被系統打斷，錄到的不要。 */
    void release(boolean send) {
        if (recorder == null) {
            return;
        }
        VoiceRecorder done = recorder;
        recorder = null;
        stopTicker();
        view.talkState(VoiceClip.TALK_LABEL, false);
        if (!ready) {
            // 麥克風還沒開好就放開了，多半是按一下而已，或還在問要不要允許
            done.cancel();
            if (send) {
                view.talkNotice(VoiceClip.HOLD_TEXT);
            }
            return;
        }
        float[] samples = done.stop();
        if (!send) {
            view.talkNotice(VoiceClip.CANCELLED_TEXT);
            return;
        }
        if (VoiceClip.millis(samples.length, done.rate()) < VoiceClip.MIN_MS) {
            view.talkNotice(VoiceClip.HOLD_TEXT);
            return;
        }
        long now = (long) Browser.now();
        if (!limiter.tryRecord(now)) {
            view.talkNotice(VoiceClip.tooFastText(limiter.waitMs(now)));
            return;
        }
        sender.accept(VoiceClip.encode(samples, done.rate()));
    }

    /** 牌桌換掉或連線斷了：還在錄的丟掉，麥克風關掉。 */
    void stop() {
        if (recorder != null) {
            recorder.cancel();
            recorder = null;
        }
        stopTicker();
    }

    private void onReady(VoiceRecorder which, String error) {
        if (which != recorder) {
            return;
        }
        if (!error.isEmpty()) {
            recorder = null;
            view.talkState(VoiceClip.TALK_LABEL, false);
            view.talkNotice(VoiceRecorder.explain(error));
            return;
        }
        ready = true;
        readyAt = Browser.now();
        ticker = Browser.every(TICK_MS, this::tick);
        tick();
    }

    private void tick() {
        if (recorder == null) {
            return;
        }
        int ms = (int) (Browser.now() - readyAt);
        if (ms >= VoiceClip.MAX_MS) {
            release(true);
            return;
        }
        view.talkState(VoiceClip.recordingLabel(ms), true);
    }

    private void stopTicker() {
        if (ticker >= 0) {
            Browser.cancelEvery(ticker);
            ticker = -1;
        }
    }
}
