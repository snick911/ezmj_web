package ezmj.web;

import java.util.function.IntConsumer;

import org.teavm.jso.dom.html.HTMLElement;
import org.teavm.jso.dom.html.HTMLInputElement;
import org.teavm.jso.dom.html.HTMLOptionElement;
import org.teavm.jso.dom.html.HTMLSelectElement;

import ezmj.model.Gender;
import ezmj.model.MusicTrack;

/** 環境設定，改了立刻生效並存起來。等待時間與電腦速度是房間的設定，不在這裡。 */
final class SettingsDialog extends Dialog {

    private final WebSettings settings;
    private final Runnable onResize;

    /** onResize 是字或牌的大小改了之後要做的事。 */
    SettingsDialog(WebSettings settings, Runnable onResize) {
        super(true);
        this.settings = settings;
        this.onResize = onResize;
        card.setClassName("card settings-card");
        card.appendChild(Dom.text("h2", "headline", "環境設定"));

        addSlider("字體大小", 85, 150, 5, settings.fontPercent, value -> {
            settings.fontPercent = value;
            Theme.setFontPercent(value);
            resized();
        });
        addSlider("麻將大小", 100, 150, 10, settings.tilePercent, value -> {
            settings.tilePercent = value;
            Theme.setTilePercent(value);
            resized();
        });
        addMusicRow();
        addSlider("音樂音量", 0, 100, 1, settings.musicVolume, value -> {
            settings.musicVolume = value;
            BackgroundMusic.setVolume(value);
        });
        addCheck("音效", settings.sound, on -> settings.sound = on);
        addSlider("音效音量", 0, 100, 1, settings.soundVolume, value -> {
            settings.soundVolume = value;
            SoundEffects.setVolume(value);
        });
        addCheck("語音", settings.voice, on -> settings.voice = on);
        addSlider("語音音量", 0, 100, 1, settings.voiceVolume, value -> {
            settings.voiceVolume = value;
            VoiceLines.setVolume(value);
        });
        addGenderRow();
        addCheck("動畫", settings.animation, on -> settings.animation = on);
        if (!Browser.canSpeak()) {
            card.appendChild(Dom.text("div", "muted", "這個瀏覽器不支援語音，語音設定沒有作用。"));
        } else {
            card.appendChild(Dom.text("div", "muted", "語音用瀏覽器的語音合成，聲音依裝置而異；沒有中文語音的裝置不會出聲。"));
        }
        card.appendChild(Dom.button("關閉", Theme.BUTTON, this::finish));
    }

    private void resized() {
        settings.save();
        TilePainter.clearCache();
        onResize.run();
    }

    private HTMLElement row(String label, HTMLElement field) {
        HTMLElement row = Dom.el("label", "form-row");
        row.appendChild(Dom.text("span", "", label));
        row.appendChild(field);
        card.appendChild(row);
        return row;
    }

    private void addSlider(String label, int min, int max, int step, int value, IntConsumer onChange) {
        HTMLInputElement slider = (HTMLInputElement) Dom.el("input", "field");
        slider.setType("range");
        slider.setAttribute("min", String.valueOf(min));
        slider.setAttribute("max", String.valueOf(max));
        slider.setAttribute("step", String.valueOf(step));
        slider.setValue(String.valueOf(value));
        slider.addEventListener("input", event -> {
            onChange.accept(Integer.parseInt(slider.getValue()));
            settings.save();
        });
        row(label, slider);
    }

    private interface BooleanConsumer {
        void accept(boolean value);
    }

    private void addCheck(String label, boolean value, BooleanConsumer onChange) {
        HTMLInputElement check = (HTMLInputElement) Dom.el("input", "check");
        check.setType("checkbox");
        check.setChecked(value);
        check.addEventListener("change", event -> {
            onChange.accept(check.isChecked());
            settings.save();
        });
        row(label, check);
    }

    private static void addOption(HTMLSelectElement select, String value, String label, boolean selected) {
        HTMLOptionElement option = (HTMLOptionElement) Dom.text("option", "", label);
        option.setValue(value);
        option.setSelected(selected);
        select.appendChild(option);
    }

    private void addMusicRow() {
        HTMLSelectElement select = (HTMLSelectElement) Dom.el("select", "field");
        for (MusicTrack track : MusicTrack.values()) {
            addOption(select, track.name(), track.label(), track == settings.music);
        }
        select.addEventListener("change", event -> {
            settings.music = MusicTrack.valueOf(select.getValue());
            settings.save();
            BackgroundMusic.select(settings.music);
        });
        row("背景音樂", select);
    }

    private void addGenderRow() {
        HTMLSelectElement select = (HTMLSelectElement) Dom.el("select", "field");
        addOption(select, Gender.FEMALE.name(), "女聲", settings.playerGender == Gender.FEMALE);
        addOption(select, Gender.MALE.name(), "男聲", settings.playerGender == Gender.MALE);
        select.addEventListener("change", event -> {
            settings.playerGender = Gender.valueOf(select.getValue());
            settings.save();
            // 選了喊一聲試聽，和 Swing 版一樣
            if (settings.voice) {
                VoiceLines.play(VoiceLines.Line.PONG, settings.playerGender);
            }
        });
        row("我的語音", select);
    }
}
