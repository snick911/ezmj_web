package ezmj.web;

import java.util.ArrayList;
import java.util.List;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSByRef;
import org.teavm.jso.JSObject;
import org.teavm.jso.webaudio.AudioBuffer;
import org.teavm.jso.webaudio.AudioBufferSourceNode;
import org.teavm.jso.webaudio.AudioContext;
import org.teavm.jso.webaudio.GainNode;

import ezmj.online.VoiceClip;

/**
 * 播放玩家的語音訊息：一則播完才播下一則，排在 AudioContext 的時間上，分頁在背景計時器變慢也不會亂。
 * 播的時候背景音樂壓低，播完再回來。自動播放時排得太多（後面還有 MAX_WAIT_S 秒以上）就不播，
 * 聊天區照樣可以按重播；按重播會停掉正在播與排著的，馬上播這一則。
 */
final class VoicePlayer {

    private static final double MAX_WAIT_S = 20;
    /** 第一則稍微晚一點開始，排程才不會因為已經過了時間而被切掉開頭。 */
    private static final double LEAD_S = .05;

    private record Scheduled(AudioBufferSourceNode source, double end) {
    }

    private static final List<Scheduled> SCHEDULED = new ArrayList<>();
    private static double endsAt;
    private static int unduckTimer = -1;

    private VoicePlayer() {
    }

    /** samples 是 VoiceClip.decode 解開的。interrupt 是按重播：先停掉其他的。不能出聲或排太多就回傳 false。 */
    static boolean play(float[] samples, int volumePercent, boolean interrupt) {
        AudioContext context = AudioOut.context();
        if (context == null) {
            return false;
        }
        double now = context.getCurrentTime();
        if (interrupt) {
            stopAll();
        }
        SCHEDULED.removeIf(item -> item.end() < now);
        double start = Math.max(now + LEAD_S, endsAt);
        if (start - now > MAX_WAIT_S) {
            return false;
        }
        AudioBuffer buffer = buffer(context, samples);
        if (buffer == null) {
            return false;
        }
        GainNode gain = context.createGain();
        gain.getGain().setValue(AudioOut.gain(volumePercent));
        gain.connect(context.getDestination());
        AudioBufferSourceNode source = context.createBufferSource();
        source.setBuffer(buffer);
        source.connect(gain);
        startAt(source, start);
        endsAt = start + buffer.getDuration();
        SCHEDULED.add(new Scheduled(source, endsAt));
        BackgroundMusic.duck(true);
        if (unduckTimer >= 0) {
            Browser.cancelAfter(unduckTimer);
        }
        unduckTimer = Browser.after((int) Math.ceil((endsAt - now) * 1000) + 100, () -> {
            unduckTimer = -1;
            BackgroundMusic.duck(false);
        });
        return true;
    }

    /** 停掉正在播與排著的，例如牌桌換掉或按了重播。 */
    static void stopAll() {
        for (Scheduled item : SCHEDULED) {
            stopQuietly(item.source());
        }
        SCHEDULED.clear();
        endsAt = 0;
        if (unduckTimer >= 0) {
            Browser.cancelAfter(unduckTimer);
            unduckTimer = -1;
        }
        BackgroundMusic.duck(false);
    }

    /**
     * 8 kHz 的 AudioBuffer。規格說至少要支援 8 到 96 kHz，但舊的 Safari 只收 22.05 kHz 以上，
     * 建不起來就拉長三倍用 24 kHz，取樣之間線性內插。
     */
    private static AudioBuffer buffer(AudioContext context, float[] samples) {
        AudioBuffer buffer = createBuffer(context, samples, VoiceClip.RATE);
        if (buffer != null) {
            return buffer;
        }
        float[] stretched = new float[samples.length * 3];
        for (int n = 0; n < stretched.length; n++) {
            int i = n / 3;
            float next = i + 1 < samples.length ? samples[i + 1] : samples[i];
            stretched[n] = samples[i] + (next - samples[i]) * (n % 3) / 3f;
        }
        return createBuffer(context, stretched, VoiceClip.RATE * 3);
    }

    @JSBody(params = {"context", "samples", "rate"}, script = "try {"
            + " var buffer = context.createBuffer(1, samples.length, rate);"
            + " buffer.getChannelData(0).set(samples); return buffer; } catch (e) { return null; }")
    private static native AudioBuffer createBuffer(AudioContext context, @JSByRef float[] samples, int rate);

    @JSBody(params = {"source", "when"}, script = "source.start(when);")
    private static native void startAt(AudioBufferSourceNode source, double when);

    @JSBody(params = "source", script = "try { source.stop(); } catch (e) { }")
    private static native void stopQuietly(JSObject source);
}
