package ezmj.web;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;
import org.teavm.jso.typedarrays.Float32Array;
import org.teavm.jso.webaudio.AudioContext;

/**
 * 麥克風錄音。用 getUserMedia 拿聲音，ScriptProcessorNode 一段一段收下來，停下來時接成一整段。
 * ScriptProcessorNode 已經不建議用，但每個瀏覽器都還支援；AudioWorklet 要另外放一個 JS 檔，要換也只差在這裡。
 * 每次錄完就關掉麥克風，分頁上才不會一直亮著正在使用麥克風。打開回音消除，牌桌的音樂、音效與喊牌就不會一起錄進去。
 * 壓縮在 VoiceClip，和 Swing 版一樣。
 */
final class VoiceRecorder {

    @JSFunctor
    interface Ready extends JSObject {
        /** 麥克風開好、開始收音了；error 不是空字串時是打不開，內容是瀏覽器的錯誤名稱，見 explain。 */
        void ready(String error);
    }

    private final JSObject handle;
    private final int rate;

    private VoiceRecorder(JSObject handle, int rate) {
        this.handle = handle;
        this.rate = rate;
    }

    /** 要在按下去的當下呼叫，瀏覽器才會跳出要不要允許麥克風。停下來之前開好才會呼叫 ready，停了就不呼叫。 */
    static VoiceRecorder start(AudioContext context, Ready ready) {
        return new VoiceRecorder(open(context, ready), (int) context.getSampleRate());
    }

    /** 收音的取樣率，就是 AudioContext 的，多半是 48 kHz。 */
    int rate() {
        return rate;
    }

    /** 停下來、關掉麥克風，回傳錄到的聲音（-1 到 1）。還沒開好就停的是空的。 */
    float[] stop() {
        Float32Array recorded = finish(handle);
        float[] samples = new float[recorded.getLength()];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = recorded.get(i);
        }
        return samples;
    }

    /** 停下來，錄到的不要。還沒開好的話，開好時直接關掉。 */
    void cancel() {
        finish(handle);
    }

    /** 打不開麥克風時給玩家看的話。 */
    static String explain(String error) {
        switch (error) {
            case "NotAllowedError":
            case "PermissionDeniedError":
                return "沒有允許使用麥克風，要在瀏覽器網址列旁邊的設定允許";
            case "NotFoundError":
            case "DevicesNotFoundError":
                return "找不到麥克風";
            case "NotReadableError":
            case "TrackStartError":
                return "麥克風被別的程式占用了";
            case "unsupported":
                return "這個瀏覽器不能錄音";
            default:
                return "打不開麥克風（" + error + "）";
        }
    }

    /**
     * ScriptProcessorNode 要接到輸出才會收到聲音，所以中間放一個音量 0 的 GainNode，喇叭不會出聲。
     * 錯誤名稱：玩家不准是 NotAllowedError，沒有麥克風是 NotFoundError；不是 https 或太舊的瀏覽器沒有 mediaDevices。
     */
    @JSBody(params = {"context", "ready"}, script = "var handle = {chunks: [], length: 0, stopped: false};"
            + "handle.release = function () {"
            + " if (handle.node) { handle.node.onaudioprocess = null;"
            + "  try { handle.node.disconnect(); handle.mute.disconnect(); } catch (e) { } }"
            + " if (handle.source) { try { handle.source.disconnect(); } catch (e) { } }"
            + " if (handle.stream) { handle.stream.getTracks().forEach(function (t) { t.stop(); }); } };"
            + "if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {"
            + " setTimeout(function () { if (!handle.stopped) { handle.stopped = true; ready('unsupported'); } }, 0);"
            + " return handle; }"
            + "navigator.mediaDevices.getUserMedia({audio: {echoCancellation: true, noiseSuppression: true,"
            + " autoGainControl: true, channelCount: 1}}).then(function (stream) {"
            + " handle.stream = stream;"
            + " if (handle.stopped) { handle.release(); return; }"
            + " handle.source = context.createMediaStreamSource(stream);"
            + " handle.node = context.createScriptProcessor(4096, 1, 1);"
            + " handle.mute = context.createGain(); handle.mute.gain.value = 0;"
            + " handle.node.onaudioprocess = function (e) {"
            + "  if (handle.stopped) { return; }"
            + "  var data = e.inputBuffer.getChannelData(0);"
            + "  handle.chunks.push(new Float32Array(data)); handle.length += data.length; };"
            + " handle.source.connect(handle.node); handle.node.connect(handle.mute); handle.mute.connect(context.destination);"
            + " ready('');"
            + "}).catch(function (e) {"
            + " handle.release();"
            + " if (!handle.stopped) { handle.stopped = true; ready(e && e.name ? e.name : String(e)); } });"
            + "return handle;")
    private static native JSObject open(AudioContext context, Ready ready);

    @JSBody(params = "handle", script = "handle.stopped = true; handle.release();"
            + "var out = new Float32Array(handle.length), at = 0;"
            + "handle.chunks.forEach(function (c) { out.set(c, at); at += c.length; });"
            + "handle.chunks = []; handle.length = 0;"
            + "return out;")
    private static native Float32Array finish(JSObject handle);
}
