package ezmj.web;

import org.teavm.jso.JSBody;
import org.teavm.jso.webaudio.AudioBuffer;
import org.teavm.jso.webaudio.AudioContext;

/** 瀏覽器的音訊。要等使用者碰過畫面才准出聲，所以第一次操作時才建立。 */
final class AudioOut {

    private static AudioContext context;

    private AudioOut() {
    }

    /** 還沒有操作過畫面，或這個瀏覽器不能出聲時是 null。 */
    static AudioContext context() {
        return context;
    }

    /** 第一次操作時呼叫。 */
    static void unlock() {
        context = create();
        if (context != null) {
            context.resume();
        }
    }

    /** TeaVM 0.16 的 AudioContext.create() 會產生 new Context()，那個名字不存在，所以自己建。 */
    @JSBody(script = "var Context = window.AudioContext || window.webkitAudioContext;"
            + "if (!Context) { return null; }"
            + "try { return new Context(); } catch (e) { return null; }")
    private static native AudioContext create();

    /** 音量百分比換算成倍率。人耳對大小的感覺接近對數，這樣拉到一半聽起來也差不多是一半，和 Swing 版的分貝換算一樣。 */
    static float gain(int percent) {
        double share = percent / 100.0;
        return (float) (share * share);
    }

    static AudioBuffer buffer(float[] samples, int rate) {
        AudioBuffer buffer = context.createBuffer(1, samples.length, rate);
        buffer.copyToChannel(samples, 0);
        return buffer;
    }
}
