package ezmj.web;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;
import org.teavm.jso.browser.Window;
import org.teavm.jso.dom.events.Event;
import org.teavm.jso.dom.html.HTMLCanvasElement;
import org.teavm.jso.dom.html.HTMLElement;

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

    /**
     * 瀏覽器要等使用者操作才准出聲。手指要到放開才算數，手機切到背景回來聲音也可能被停掉，
     * 所以每次操作都通知，不是只有第一次。
     */
    @JSBody(params = "action", script = "['touchend', 'mousedown', 'keydown'].forEach(function (type) {"
            + " document.addEventListener(type, function () { action(); }, true); });")
    static native void onGesture(Callback action);

    /** 這個元件大小改變時通知。 */
    @JSBody(params = {"element", "action"}, script = "if (window.ResizeObserver) {"
            + " new ResizeObserver(function () { action(); }).observe(element); }")
    static native void onResize(HTMLElement element, Callback action);

    /** iPhone 的 Safari 只有影片能全螢幕，所以沒有這個功能。放在不准全螢幕的 iframe 裡也是。 */
    @JSBody(script = "return !!document.fullscreenEnabled;")
    static native boolean canFullscreen();

    /** 再按一次或用手機的返回鍵離開。做不到就維持原樣。 */
    @JSBody(script = "var change = document.fullscreenElement ? document.exitFullscreen()"
            + " : document.documentElement.requestFullscreen();"
            + "change.catch(function (e) { console.warn('不能切換全螢幕：' + e.message); });")
    static native void toggleFullscreen();

    /** 手機版面，條件要和 style.css 一樣。 */
    @JSBody(script = "return window.matchMedia('(max-width: 900px), (max-height: 560px)').matches;")
    static native boolean isCompact();

    /** 手機橫向：提示、按鈕與選單浮在牌桌上，名字移到各家旁邊。條件要和 style.css 的橫向版面一樣。 */
    @JSBody(script = "return window.matchMedia('(max-height: 560px) and (orientation: landscape)').matches;")
    static native boolean isLandscapeCompact();

    @JSBody(script = "return 'ontouchstart' in window;")
    static native boolean isTouchDevice();

    /** 中文輸入法還在選字，這時的 Enter 是選字，不是送出。 */
    @JSBody(params = "event", script = "return !!(event.isComposing || event.keyCode === 229);")
    static native boolean isComposing(Event event);

    /**
     * 按鍵該留給別人：游標在輸入框、按鈕或連結上，或畫面被視窗、等待的提示蓋住。
     * 這時 Enter 照原本的意思，不是打開牌桌上的聊天。
     */
    @JSBody(script = "var active = document.activeElement;"
            + "if (active && active !== document.body"
            + " && (/^(INPUT|TEXTAREA|SELECT|BUTTON|A)$/.test(active.tagName) || active.isContentEditable)) { return true; }"
            + "return !!document.querySelector('.backdrop, .waiting');")
    static native boolean keyBelongsElsewhere();

    /** 嵌入的字型載入好時通知，牌面要重畫。 */
    @JSBody(params = "action", script = "if (document.fonts) {"
            + " document.fonts.addEventListener('loadingdone', function () { action(); }); }")
    static native void onFontsLoaded(Callback action);

    @JSBody(params = "text", script = "if (navigator.clipboard) { navigator.clipboard.writeText(text).catch(function () { }); }")
    static native void copy(String text);

    @JSBody(script = "location.reload();")
    static native void reload();

    /** 現在的當地時間，例如 202611062130，依序是年月日時分。 */
    @JSBody(script = "var d = new Date();"
            + "function two(n) { return (n < 10 ? '0' : '') + n; }"
            + "return d.getFullYear() + two(d.getMonth() + 1) + two(d.getDate()) + two(d.getHours()) + two(d.getMinutes());")
    static native String nowDigits();

    /** 網站的網址，不含查詢參數，例如 snick911.github.io/ezmj_web/。寫在分享出去的圖上。 */
    @JSBody(script = "return location.host + location.pathname.replace(/index\\.html$/, '');")
    static native String siteAddress();

    /** 網址不含查詢參數，本機測試時的 server 參數不會分享出去。 */
    @JSBody(script = "return location.origin + location.pathname;")
    static native String siteUrl();

    /**
     * 存成檔案。內容用 base64 傳過來，換成 Blob 再用連結下載；網址放一陣子才收回，
     * 有些瀏覽器點了連結之後才開始讀。
     */
    @JSBody(params = {"base64", "type", "fileName"}, script = "var raw = atob(base64);"
            + "var bytes = new Uint8Array(raw.length);"
            + "for (var i = 0; i < raw.length; i++) { bytes[i] = raw.charCodeAt(i); }"
            + "var url = URL.createObjectURL(new Blob([bytes], {type: type}));"
            + "var link = document.createElement('a');"
            + "link.href = url; link.download = fileName; link.rel = 'noopener';"
            + "document.body.appendChild(link); link.click(); link.remove();"
            + "setTimeout(function () { URL.revokeObjectURL(url); }, 60000);")
    static native void downloadBase64(String base64, String type, String fileName);

    /**
     * 先把畫好的圖轉成 PNG 留在 canvas 上。分享要在按下去的當下呼叫，等轉檔就來不及了，所以視窗一打開就先轉。
     */
    @JSBody(params = "canvas", script = "canvas.toBlob(function (blob) { canvas.ezmjBlob = blob; }, 'image/png');")
    static native void preparePng(HTMLCanvasElement canvas);

    /** 這個瀏覽器能不能把圖檔分享給其他 App，手機與部分電腦可以。 */
    @JSBody(script = "try { return !!(navigator.canShare && window.File"
            + " && navigator.canShare({files: [new File([''], 'ezmj.png', {type: 'image/png'})]})); }"
            + " catch (e) { return false; }")
    static native boolean canShareFiles();

    /** 分享 preparePng 準備好的圖，還沒準備好或不能分享就回 false。按了取消不算失敗。 */
    @JSBody(params = {"canvas", "fileName", "title", "text"}, script = "var blob = canvas.ezmjBlob;"
            + "if (!blob || !navigator.canShare || !window.File) { return false; }"
            + "var file = new File([blob], fileName, {type: 'image/png'});"
            + "if (!navigator.canShare({files: [file]})) { return false; }"
            + "navigator.share({files: [file], title: title, text: text}).catch(function (e) {"
            + " if (e.name !== 'AbortError') { console.warn('不能分享：' + e.message); } });"
            + "return true;")
    static native boolean sharePng(HTMLCanvasElement canvas, String fileName, String title, String text);

    /** 把畫好的圖存成 PNG。preparePng 轉好了就直接用，不然當場轉。 */
    @JSBody(params = {"canvas", "fileName"}, script = "function save(blob) {"
            + " if (!blob) { return; }"
            + " var url = URL.createObjectURL(blob);"
            + " var link = document.createElement('a');"
            + " link.href = url; link.download = fileName; link.rel = 'noopener';"
            + " document.body.appendChild(link); link.click(); link.remove();"
            + " setTimeout(function () { URL.revokeObjectURL(url); }, 60000); }"
            + "if (canvas.ezmjBlob) { save(canvas.ezmjBlob); } else { canvas.toBlob(save, 'image/png'); }")
    static native void downloadPng(HTMLCanvasElement canvas, String fileName);

    @JSBody(params = "text", script = "console.warn(text);")
    static native void warn(String text);

    @JSBody(params = "name", script = "var meta = document.querySelector('meta[name=\"' + name + '\"]');"
            + "return meta ? meta.content : '';")
    static native String meta(String name);

    /** speak 的聲音：喊牌的女聲、男聲，以及司儀。 */
    static final int VOICE_FEMALE = 0;
    static final int VOICE_MALE = 1;
    static final int VOICE_ANNOUNCER = 2;
    /** 司儀的一句話最多等這麼久（包括排在正在喊的那句後面）。瀏覽器偶爾不通知說完了，過了這段時間喊牌就不再讓它。 */
    private static final int ANNOUNCER_MAX_MS = 6_000;

    /**
     * 念一句話。voice 是 VOICE_FEMALE、VOICE_MALE 或 VOICE_ANNOUNCER；要男聲卻找不到男聲就靠壓低音調。
     * 喊牌的新一句會打斷還沒喊完的上一句。司儀不打斷，排在正在喊的那句後面，和 Swing 版一樣一句念完才念下一句；
     * 司儀說完之前，droppable 的牌名就不喊了，吃碰槓胡排在司儀後面，一定念到。
     * 司儀挑和喊牌不同的女聲，依序找 Yating、HsiaoChen、HsiaoYu、Hanhan，再來是任何台灣女聲、任何中文女聲（粵語除外），
     * 都沒有才用喊牌的女聲。
     */
    @JSBody(params = {"text", "voice", "volume", "pitch", "rate", "droppable"},
            script = "if (!window.speechSynthesis) { return; }"
            + "var synth = window.speechSynthesis;"
            + "var voices = synth.getVoices();"
            + "var chinese = voices.filter(function (v) { return /^zh/i.test(v.lang); });"
            + "if (voices.length > 0 && chinese.length === 0) { return; }"
            + "var taiwan = chinese.filter(function (v) { return /TW/i.test(v.lang); });"
            + "var pool = taiwan.length > 0 ? taiwan : chinese;"
            + "var maleNames = /male|Zhiwei|Yunxi|Yunjhe|Yunyang|Kangkang|男/i;"
            + "function isMale(v) { return !/female/i.test(v.name) && maleNames.test(v.name); }"
            + "var male = voice === 1;"
            + "var matching = pool.filter(function (v) { return isMale(v) === male; });"
            + "var chosen = matching.length > 0 ? matching[0] : pool[0];"
            + "if (voice === 2) {"
            + " var others = chinese.filter(function (v) {"
            + "  return v !== chosen && !isMale(v) && !/HK|MO|yue/i.test(v.lang); });"
            + " var named = null;"
            + " ['Yating', 'HsiaoChen', 'HsiaoYu', 'Hanhan'].forEach(function (n) { if (!named) {"
            + "  named = others.filter(function (v) { return v.name.indexOf(n) >= 0; })[0] || null; } });"
            + " chosen = named || others.filter(function (v) { return /TW/i.test(v.lang); })[0] || others[0] || chosen; }"
            + "var utterance = new SpeechSynthesisUtterance(text);"
            + "utterance.lang = voice === 2 && chosen ? chosen.lang : 'zh-TW';"
            + "if (chosen) { utterance.voice = chosen; }"
            + "utterance.volume = volume;"
            + "utterance.pitch = male && matching.length === 0 ? pitch * .6 : pitch;"
            + "utterance.rate = rate;"
            + "var now = performance.now();"
            + "if (voice === 2) {"
            + " var state = {utterance: utterance, until: now + " + ANNOUNCER_MAX_MS + "};"
            + " window.ezmjAnnouncer = state;"
            + " utterance.onend = utterance.onerror = function () {"
            + "  if (window.ezmjAnnouncer === state) { window.ezmjAnnouncer = null; } };"
            + " synth.speak(utterance);"
            + " return; }"
            + "if (window.ezmjAnnouncer && window.ezmjAnnouncer.until > now) {"
            + " if (!droppable) { synth.speak(utterance); }"
            + " return; }"
            + "synth.cancel();"
            + "synth.speak(utterance);")
    static native void speak(String text, int voice, double volume, double pitch, double rate, boolean droppable);

    @JSBody(script = "return !!window.speechSynthesis;")
    static native boolean canSpeak();
}
