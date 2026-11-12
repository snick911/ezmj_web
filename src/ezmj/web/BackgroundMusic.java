package ezmj.web;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;
import org.teavm.jso.webaudio.AudioBuffer;
import org.teavm.jso.webaudio.AudioBufferSourceNode;
import org.teavm.jso.webaudio.AudioContext;
import org.teavm.jso.webaudio.GainNode;

import ezmj.web.MusicScores.Score;
import ezmj.web.MusicScores.Voice;

/**
 * 背景音樂，都循環播放。合成的曲子依 MusicScores 的樂譜即時合成，不需要音樂檔，音色照 Swing 版的 BackgroundMusic；
 * 錄好的曲子選到才下載 MP3（各約 4 MB），由瀏覽器解碼，沒選的人不用下載。
 * 瀏覽器只有一條執行緒，整首一次合成完畫面會卡住，所以每次只算一小段，算完讓畫面喘口氣再接著算。
 */
final class BackgroundMusic {

    /** 整首要算很久，取樣率比音效低一半，最高的泛音也還在範圍內。 */
    private static final int RATE = 22_050;
    private static final double PEAK = .6;
    /** 一次最多算這麼多毫秒。 */
    private static final double SLICE_MS = 8;

    /** 錄好的曲子下載、解碼完。 */
    @JSFunctor
    interface Decoded extends JSObject {
        /** 下載或解碼失敗時 buffer 是 null。rmsDb 是整首所有聲道的 RMS，以滿格為 0 dB。 */
        void done(AudioBuffer buffer, double rmsDb);
    }

    private static GainNode output;
    private static AudioBufferSourceNode playing;
    private static WebMusic current = WebMusic.OFF;
    /** 每次換曲加一，還在算的舊曲子就知道不用算了。 */
    private static int serial;
    private static int volume = 100;

    private BackgroundMusic() {
    }

    static void setVolume(int percent) {
        volume = percent;
        if (output != null) {
            output.getGain().setValue(AudioOut.gain(percent));
        }
    }

    /** 還不能出聲的話什麼都不做，等第一次操作之後再選一次。錄好的曲子要等下載完才響。 */
    static void select(WebMusic track) {
        AudioContext context = AudioOut.context();
        if (context == null || track == current) {
            return;
        }
        current = track;
        int mine = ++serial;
        if (playing != null) {
            playing.stop();
            playing = null;
        }
        if (track.isRecorded()) {
            load(track, mine);
        } else if (track != WebMusic.OFF) {
            render(track, mine);
        }
    }

    /** 下載好之前又換了曲子就不播。下載不了（例如離線）就不出聲，其他曲子照常。 */
    private static void load(WebMusic track, int mine) {
        decode(AudioOut.context(), track.recordingUrl(), (buffer, rmsDb) -> {
            if (mine == serial && buffer != null) {
                play(buffer, WebMusic.levelGain(rmsDb));
            }
        });
    }

    /**
     * 下載 url 的音樂檔交給瀏覽器解碼，再算整首的 RMS。decodeAudioData 用回呼的寫法，舊的 Safari 不支援 Promise 的寫法。
     * 一首約 3 分鐘，解碼成 48 kHz 立體聲在記憶體裡約 70 MB，只留正在播的那首。
     */
    @JSBody(params = {"context", "url", "done"}, script = "fetch(url).then(function (response) {"
            + " if (!response.ok) { throw new Error('HTTP ' + response.status); }"
            + " return response.arrayBuffer(); })"
            + ".then(function (data) { return new Promise(function (resolve, reject) {"
            + " context.decodeAudioData(data, resolve, reject); }); })"
            + ".then(function (buffer) {"
            + " var sum = 0, count = 0;"
            + " for (var c = 0; c < buffer.numberOfChannels; c++) {"
            + "  var samples = buffer.getChannelData(c);"
            + "  for (var i = 0; i < samples.length; i++) { sum += samples[i] * samples[i]; }"
            + "  count += samples.length; }"
            + " done(buffer, sum > 0 ? 10 * Math.log10(sum / count) : -Infinity); })"
            + ".catch(function (e) {"
            + " console.warn('背景音樂載入失敗：' + url + ' ' + (e && e.message ? e.message : e));"
            + " done(null, 0); });")
    private static native void decode(AudioContext context, String url, Decoded done);

    private static void render(WebMusic track, int mine) {
        Score score = MusicScores.score(track);
        double secondsPerBeat = 60.0 / score.bpm();
        double[] mix = new double[(int) (score.totalBeats() * secondsPerBeat * RATE)];
        Random noise = new Random(track.ordinal());
        Deque<Runnable> notes = new ArrayDeque<>();
        for (Voice voice : score.voices()) {
            double beat = 0;
            for (String note : notes(voice)) {
                int colon = note.indexOf(':');
                String pitch = note.substring(0, colon);
                double beats = Double.parseDouble(note.substring(colon + 1));
                if (!pitch.equals("R")) {
                    int start = (int) (beat * secondsPerBeat * RATE);
                    double seconds = beats * secondsPerBeat;
                    notes.add(() -> addNote(mix, voice, start, seconds, pitch, noise));
                }
                beat += beats;
            }
        }
        renderSlice(notes, mix, mine);
    }

    private static void renderSlice(Deque<Runnable> notes, double[] mix, int mine) {
        if (mine != serial) {
            return;
        }
        double until = Browser.now() + SLICE_MS;
        while (!notes.isEmpty() && Browser.now() < until) {
            notes.poll().run();
        }
        if (notes.isEmpty()) {
            play(AudioOut.buffer(toSamples(withEcho(mix)), RATE), 1);
        } else {
            Browser.after(1, () -> renderSlice(notes, mix, mine));
        }
    }

    /** 循環播放 buffer。level 是這首曲子自己的倍率，「音樂音量」在後面的 output 調。 */
    private static void play(AudioBuffer buffer, double level) {
        AudioContext context = AudioOut.context();
        if (output == null) {
            output = context.createGain();
            output.getGain().setValue(AudioOut.gain(volume));
            output.connect(context.getDestination());
        }
        GainNode leveled = context.createGain();
        leveled.getGain().setValue((float) level);
        leveled.connect(output);
        AudioBufferSourceNode source = context.createBufferSource();
        source.setBuffer(buffer);
        source.setLoop(true);
        source.connect(leveled);
        source.start();
        playing = source;
    }

    private static List<String> notes(Voice voice) {
        List<String> notes = new ArrayList<>();
        for (String token : voice.notes().trim().split("\\s+")) {
            if (!token.equals("|")) {
                notes.add(token);
            }
        }
        return notes;
    }

    private static double frequency(String pitch) {
        int semitone = "C D EF G A B".indexOf(pitch.charAt(0));
        int octave = pitch.charAt(pitch.length() - 1) - '0';
        return 440 * Math.pow(2, (semitone + (octave + 1) * 12 - 69) / 12.0);
    }

    private static void addNote(double[] mix, Voice voice, int start, double seconds, String pitch, Random noise) {
        double gain = voice.gain();
        switch (voice.instrument()) {
            case PLUCK:
                struck(mix, start, frequency(pitch), gain, new double[] {1, .6, .35, .2, .12, .06}, 2.8, .35, 1.6, .001);
                break;
            case KEYS:
                struck(mix, start, frequency(pitch), gain, new double[] {1, .35, .12, .05}, 1.4, .5, 2.2, .003);
                break;
            case BASS:
                struck(mix, start, frequency(pitch), gain, new double[] {1, .4, .1}, 3, .3, 1, .003);
                break;
            case PAD:
                pad(mix, start, seconds, frequency(pitch), gain);
                break;
            case FLUTE:
                flute(mix, start, seconds, frequency(pitch), gain, noise);
                break;
            default:
                partial(mix, start, .12, 980, gain, 60, .001);
                partial(mix, start, .12, 1430, gain * .5, 90, .001);
                break;
        }
    }

    /** 撥弦、敲擊類：幾個泛音各自衰減，越高的泛音消失得越快。 */
    private static void struck(double[] mix, int start, double frequency, double gain, double[] harmonics,
                               double decay, double decayGrowth, double seconds, double attack) {
        for (int h = 0; h < harmonics.length; h++) {
            partial(mix, start, seconds, frequency * (h + 1), gain * harmonics[h],
                    decay * (1 + decayGrowth * h), attack);
        }
    }

    /** 一個會衰減的正弦波。用遞迴公式算，比每個取樣都呼叫 Math.sin 快很多。 */
    private static void partial(double[] mix, int start, double seconds, double frequency, double amplitude,
                                double decayPerSecond, double attackSeconds) {
        int samples = (int) (seconds * RATE);
        int attack = Math.max(1, (int) (attackSeconds * RATE));
        int release = 128;
        double w = 2 * Math.PI * frequency / RATE;
        double c = 2 * Math.cos(w);
        double y1 = Math.sin(-w);
        double y2 = Math.sin(-2 * w);
        double envelope = amplitude;
        double fall = Math.exp(-decayPerSecond / RATE);
        for (int n = 0; n < samples; n++) {
            double y = c * y1 - y2;
            y2 = y1;
            y1 = y;
            double shape = Math.min(1, Math.min((double) n / attack, (double) (samples - n) / release));
            mix[(start + n) % mix.length] += envelope * shape * y;
            envelope *= fall;
        }
    }

    /** 長音襯底：三個略微走音的正弦波疊在一起，緩緩進出。 */
    private static void pad(double[] mix, int start, double seconds, double frequency, double gain) {
        double release = 1;
        int samples = (int) ((seconds + release) * RATE);
        for (double detune : new double[] {1, 1.003, .997}) {
            double w = 2 * Math.PI * frequency * detune / RATE;
            for (int n = 0; n < samples; n++) {
                double t = (double) n / RATE;
                double shape = Math.min(1, Math.min(t / .8, (seconds + release - t) / release));
                mix[(start + n) % mix.length] += gain * .33 * shape
                        * (Math.sin(w * n) + .1 * Math.sin(2 * w * n));
            }
        }
    }

    /** 笛聲：吹氣聲加上慢慢出現的顫音。 */
    private static void flute(double[] mix, int start, double seconds, double frequency, double gain, Random noise) {
        double release = .12;
        int samples = (int) ((seconds + release) * RATE);
        double phase = 0;
        for (int n = 0; n < samples; n++) {
            double t = (double) n / RATE;
            double vibrato = 1 + .004 * Math.min(1, t / .5) * Math.sin(2 * Math.PI * 5 * t);
            phase += 2 * Math.PI * frequency * vibrato / RATE;
            double shape = Math.min(1, Math.min(t / .06, (seconds + release - t) / release));
            double breath = .02 * (noise.nextDouble() * 2 - 1);
            mix[(start + n) % mix.length] += gain * shape
                    * (Math.sin(phase) + .12 * Math.sin(2 * phase) + .04 * Math.sin(3 * phase) + breath);
        }
    }

    /** 加上兩道回音，讓聲音不那麼乾。 */
    private static double[] withEcho(double[] mix) {
        int first = (int) (.29 * RATE);
        int second = (int) (.47 * RATE);
        double[] out = new double[mix.length];
        for (int n = 0; n < mix.length; n++) {
            out[n] = mix[n]
                    + .22 * mix[Math.floorMod(n - first, mix.length)]
                    + .13 * mix[Math.floorMod(n - second, mix.length)];
        }
        return out;
    }

    private static float[] toSamples(double[] mix) {
        double peak = 1e-9;
        for (double sample : mix) {
            peak = Math.max(peak, Math.abs(sample));
        }
        float[] samples = new float[mix.length];
        for (int n = 0; n < mix.length; n++) {
            samples[n] = (float) (mix[n] / peak * PEAK);
        }
        return samples;
    }
}
