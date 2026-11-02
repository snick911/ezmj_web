package ezmj.web;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;
import org.teavm.jso.browser.Window;

/** 瀏覽器提供的小功能。儲存空間與語音在隱私模式或舊瀏覽器可能不能用，一律靜靜地失敗。 */
final class Browser {

    /** 給瀏覽器呼叫的函式。 */
    @JSFunctor
    interface Callback extends JSObject {
        void run();
    }

    private Browser() {
    }

    /** 毫秒，只用來算經過多久。 */
    @JSBody(script = "return performance.now();")
    static native double now();

    static int after(int delayMs, Runnable action) {
        return Window.setTimeout(action::run, delayMs);
    }

    static void cancelAfter(int id) {
        Window.clearTimeout(id);
    }

    static int every(int periodMs, Runnable action) {
        return Window.setInterval(action::run, periodMs);
    }

    static void cancelEvery(int id) {
        Window.clearInterval(id);
    }

    @JSBody(params = "key", script = "try { return localStorage.getItem(key); } catch (e) { return null; }")
    static native String load(String key);

    @JSBody(params = {"key", "value"}, script = "try { localStorage.setItem(key, value); } catch (e) { }")
    static native void save(String key, String value);

    @JSBody(params = "key", script = "try { localStorage.removeItem(key); } catch (e) { }")
    static native void forget(String key);

    @JSBody(params = "key", script = "return new URLSearchParams(location.search).get(key);")
    static native String queryParameter(String key);

    @JSBody(script = "return location.hostname;")
    static native String hostName();

    /** 分頁回到前景時通知。 */
    @JSBody(params = "action", script = "document.addEventListener('visibilitychange', function () {"
            + " if (!document.hidden) { action(); } });")
    static native void onVisible(Callback action);

    /** 瀏覽器要等使用者碰過畫面才准出聲，這裡等第一次操作。 */
    @JSBody(params = "action", script = "var fired = false;"
            + "var once = function () { if (!fired) { fired = true; action(); } };"
            + "document.addEventListener('pointerdown', once, true);"
            + "document.addEventListener('keydown', once, true);")
    static native void onFirstGesture(Callback action);

    /** 嵌入的字型載入好時通知，牌面要重畫。 */
    @JSBody(params = "action", script = "if (document.fonts) {"
            + " document.fonts.addEventListener('loadingdone', function () { action(); }); }")
    static native void onFontsLoaded(Callback action);

    @JSBody(params = "text", script = "if (navigator.clipboard) { navigator.clipboard.writeText(text).catch(function () { }); }")
    static native void copy(String text);

    @JSBody(script = "location.reload();")
    static native void reload();

    @JSBody(params = "text", script = "console.warn(text);")
    static native void warn(String text);

    @JSBody(params = "name", script = "var meta = document.querySelector('meta[name=\"' + name + '\"]');"
            + "return meta ? meta.content : '';")
    static native String meta(String name);

    /** 新的一句會打斷還沒喊完的上一句。male 表示要男聲，找不到男聲就靠壓低音調。 */
    @JSBody(params = {"text", "male", "volume", "pitch", "rate"}, script = "if (!window.speechSynthesis) { return; }"
            + "var synth = window.speechSynthesis;"
            + "var voices = synth.getVoices();"
            + "var chinese = voices.filter(function (v) { return /^zh/i.test(v.lang); });"
            + "if (voices.length > 0 && chinese.length === 0) { return; }"
            + "var taiwan = chinese.filter(function (v) { return /TW/i.test(v.lang); });"
            + "var pool = taiwan.length > 0 ? taiwan : chinese;"
            + "var maleNames = /male|Zhiwei|Yunxi|Yunjhe|Yunyang|Kangkang|男/i;"
            + "var matching = pool.filter(function (v) { return /female/i.test(v.name) ? !male : maleNames.test(v.name) === male; });"
            + "var utterance = new SpeechSynthesisUtterance(text);"
            + "utterance.lang = 'zh-TW';"
            + "var voice = matching.length > 0 ? matching[0] : pool[0];"
            + "if (voice) { utterance.voice = voice; }"
            + "utterance.volume = volume;"
            + "utterance.pitch = male && matching.length === 0 ? pitch * .6 : pitch;"
            + "utterance.rate = rate;"
            + "synth.cancel();"
            + "synth.speak(utterance);")
    static native void speak(String text, boolean male, double volume, double pitch, double rate);

    @JSBody(script = "return !!window.speechSynthesis;")
    static native boolean canSpeak();
}
