package ezmj.web;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

import org.teavm.jso.webaudio.AudioBuffer;
import org.teavm.jso.webaudio.AudioBufferSourceNode;
import org.teavm.jso.webaudio.AudioContext;
import org.teavm.jso.webaudio.GainNode;

/** 音效全部由程式合成，不需要音效檔，公式照 Swing 版的 SoundEffects。還不能出聲時就安靜地不出聲。 */
final class SoundEffects {

    /** variants 是同一種音效的版本數，避免連續打牌時每一聲都一模一樣。 */
    enum Cue {
        DISCARD(3), CHOW(1), PONG(1), KONG(1), WIN(1), DRAWN_GAME(1), DEAL(1), DRAW(1), FLOWER(1);

        private final int variants;

        Cue(int variants) {
            this.variants = variants;
        }

        int variants() {
            return variants;
        }
    }

    private static final int RATE = 44_100;
    private static final double[] DISCARD_PITCHES = {.94, 1, 1.07};

    private static final Map<Cue, AudioBuffer[]> BUFFERS = new EnumMap<>(Cue.class);
    private static final Random PICKER = new Random();
    private static GainNode output;
    private static int volume = 100;

    private SoundEffects() {
    }

    static void setVolume(int percent) {
        volume = percent;
        if (output != null) {
            output.getGain().setValue(AudioOut.gain(percent));
        }
    }

    static void play(Cue cue) {
        AudioContext context = AudioOut.context();
        if (context == null) {
            return;
        }
        if (output == null) {
            output = context.createGain();
            output.getGain().setValue(AudioOut.gain(volume));
            output.connect(context.getDestination());
        }
        AudioBuffer[] buffers = buffers(cue);
        AudioBufferSourceNode source = context.createBufferSource();
        source.setBuffer(buffers[PICKER.nextInt(buffers.length)]);
        source.connect(output);
        source.start();
    }

    private static AudioBuffer[] buffers(Cue cue) {
        AudioBuffer[] buffers = BUFFERS.get(cue);
        if (buffers == null) {
            buffers = new AudioBuffer[cue.variants()];
            for (int variant = 0; variant < buffers.length; variant++) {
                buffers[variant] = AudioOut.buffer(render(cue, variant), RATE);
            }
            BUFFERS.put(cue, buffers);
        }
        return buffers;
    }

    /** 合成音效：44.1 kHz、單聲道。 */
    private static float[] render(Cue cue, int variant) {
        Random noise = new Random(cue.ordinal() * 31L + variant);
        double[] mix;
        double level;
        switch (cue) {
            case DISCARD:
                mix = new double[seconds(.13)];
                click(mix, 0, DISCARD_PITCHES[variant], 1, noise);
                level = .6;
                break;
            case CHOW:
                mix = new double[seconds(.22)];
                click(mix, 0, 1.05, .8, noise);
                click(mix, .075, .95, 1, noise);
                level = .7;
                break;
            case PONG:
                mix = new double[seconds(.32)];
                click(mix, 0, 1, .85, noise);
                click(mix, .06, .97, .85, noise);
                click(mix, .12, .94, 1, noise);
                thump(mix, .12, .9);
                level = .8;
                break;
            case KONG:
                mix = new double[seconds(.4)];
                for (int i = 0; i < 4; i++) {
                    click(mix, i * .055, 1.04 - i * .03, .75 + i * .08, noise);
                }
                thump(mix, .165, 1);
                level = .85;
                break;
            case WIN:
                mix = new double[seconds(1.15)];
                click(mix, 0, .94, 1, noise);
                thump(mix, 0, 1);
                bell(mix, .07, 523.25, .5, .22);
                bell(mix, .16, 659.25, .55, .24);
                bell(mix, .25, 783.99, .6, .28);
                bell(mix, .34, 1046.50, .75, .5);
                level = .75;
                break;
            case DEAL:
                mix = new double[seconds(.13)];
                click(mix, 0, 1.15, .8, noise);
                click(mix, .035, 1.05, 1, noise);
                level = .35;
                break;
            case DRAW:
                mix = new double[seconds(.1)];
                click(mix, 0, 1.2, 1, noise);
                level = .35;
                break;
            case FLOWER:
                mix = new double[seconds(.5)];
                click(mix, 0, 1.1, .7, noise);
                bell(mix, .03, 880, .5, .12);
                bell(mix, .11, 1318.51, .45, .16);
                level = .4;
                break;
            default:
                mix = new double[seconds(.75)];
                bell(mix, 0, 329.63, .6, .25);
                bell(mix, .2, 261.63, .7, .35);
                level = .45;
                break;
        }
        return toSamples(mix, level);
    }

    private static int seconds(double value) {
        return (int) (value * RATE);
    }

    /** 牌敲在桌上：幾個很快衰減的高頻，加上一點雜訊與低音。 */
    private static void click(double[] mix, double at, double pitch, double gain, Random noise) {
        int start = seconds(at);
        int length = Math.min(seconds(.09), mix.length - start);
        for (int n = 0; n < length; n++) {
            double t = (double) n / RATE;
            mix[start + n] += gain * (.55 * Math.exp(-t / .006) * Math.sin(2 * Math.PI * 2600 * pitch * t)
                    + .35 * Math.exp(-t / .010) * Math.sin(2 * Math.PI * 1500 * pitch * t)
                    + .25 * Math.exp(-t / .004) * Math.sin(2 * Math.PI * 4700 * pitch * t)
                    + .5 * Math.exp(-t / .018) * Math.sin(2 * Math.PI * 210 * t)
                    + .35 * Math.exp(-t / .0015) * (noise.nextDouble() * 2 - 1));
        }
    }

    /** 重重放下的低音。 */
    private static void thump(double[] mix, double at, double gain) {
        int start = seconds(at);
        int length = Math.min(seconds(.16), mix.length - start);
        for (int n = 0; n < length; n++) {
            double t = (double) n / RATE;
            mix[start + n] += gain * .9 * Math.exp(-t / .05) * Math.sin(2 * Math.PI * 115 * t);
        }
    }

    private static void bell(double[] mix, double at, double frequency, double gain, double decay) {
        int start = seconds(at);
        int length = Math.min(seconds(decay * 5), mix.length - start);
        for (int n = 0; n < length; n++) {
            double t = (double) n / RATE;
            double attack = Math.min(1, t / .003);
            mix[start + n] += gain * attack * Math.exp(-t / decay)
                    * (Math.sin(2 * Math.PI * frequency * t)
                    + .4 * Math.sin(4 * Math.PI * frequency * t)
                    + .15 * Math.sin(6 * Math.PI * frequency * t));
        }
    }

    /** 調整到指定音量，尾端淡出避免爆音。 */
    private static float[] toSamples(double[] mix, double level) {
        double peak = 1e-9;
        for (double sample : mix) {
            peak = Math.max(peak, Math.abs(sample));
        }
        int fade = seconds(.01);
        float[] samples = new float[mix.length];
        for (int n = 0; n < mix.length; n++) {
            double envelope = Math.min(1, (double) (mix.length - 1 - n) / fade);
            samples[n] = (float) (mix[n] / peak * level * envelope);
        }
        return samples;
    }
}
