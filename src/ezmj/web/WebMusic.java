package ezmj.web;

/**
 * 背景音樂的選項，名稱與標題和 ezmj 的 MusicTrack 一樣，存在設定裡的也是同一個名稱。
 * 網頁版自己列一份：線上的網站固定編譯 ezmj 的某個 commit（見 workflow 的 EZMJ_COMMIT），
 * 那一版的 MusicTrack 可能還沒有後來加的曲子。AllTests 會對照 MusicTrack，兩邊不能不一樣。
 */
enum WebMusic {
    OFF("關閉", null),
    TEAHOUSE("茶館閒情", null),
    NIGHT_MARKET("夜市熱鬧", null),
    BAMBOO("竹林清風", null),
    MOONLIGHT("月夜琴音", null),
    CLEAR_THE_BOARD("清空牌桌（Clear the Board）", "clear_the_board.mp3"),
    SUNLIGHT("木板暖陽（Sunlight on the Floorboards）", "sunlight_on_the_floorboards.mp3");

    /** 錄好的曲子放在網站的這個資料夾，和 Swing 版 ezmj/ui/music 的檔案一樣。 */
    static final String RECORDING_DIR = "music/";
    /**
     * 錄好的曲子混音得比較大聲，比這個大聲就壓下來，和合成的曲子差不多大聲，同一個「音樂音量」聽起來才一致。
     * 和 Swing 版的 RecordedMusic.TARGET_RMS_DB 一樣。
     */
    static final double TARGET_RMS_DB = -17;

    private final String label;
    private final String recording;

    WebMusic(String label, String recording) {
        this.label = label;
        this.recording = recording;
    }

    String label() {
        return label;
    }

    /** 錄好的曲子的 MP3 檔名；照樂譜即時合成的曲子是 null。 */
    String recording() {
        return recording;
    }

    boolean isRecorded() {
        return recording != null;
    }

    /** 照樂譜合成的曲子，不含關閉。 */
    boolean isSynthesized() {
        return this != OFF && !isRecorded();
    }

    /**
     * 錄好的曲子在網站上的位置。不帶版號：曲子很少換，每次改版都重新下載 4 MB 不划算，
     * GitHub Pages 只讓瀏覽器快取 10 分鐘，換了也很快拿到新的。
     */
    String recordingUrl() {
        return RECORDING_DIR + recording;
    }

    /**
     * 錄好的曲子播放時要乘的倍率，rmsDb 是整首的 RMS：比 TARGET_RMS_DB 大聲就整首等比例壓到剛好，
     * 比較小聲的不放大，免得破音。和 Swing 版的 RecordedMusic.level 一樣。
     */
    static double levelGain(double rmsDb) {
        return rmsDb > TARGET_RMS_DB ? Math.pow(10, (TARGET_RMS_DB - rmsDb) / 20) : 1;
    }
}
