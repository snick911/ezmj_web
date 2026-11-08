package ezmj.web;

/**
 * 存在瀏覽器裡的上次名字，牌局中還有 token、房號與是不是專家模式，牌局結束就清掉，對應 Swing 版的 OnlineProfile。
 * token 等於這個座位的身分，所以網頁不執行別人的內容：見 index.html 的 CSP 與 Dom 的說明。
 * 專家模式只在網頁這邊，服務器不知道，所以要記著，接回牌局時才照樣打。
 */
record WebProfile(String name, String token, String room, boolean expert) {

    private static final String NAME_KEY = "ezmj.name";
    private static final String TOKEN_KEY = "ezmj.token";
    private static final String ROOM_KEY = "ezmj.room";
    private static final String EXPERT_KEY = "ezmj.expert";

    static WebProfile load() {
        String name = Browser.load(NAME_KEY);
        return new WebProfile(name == null ? "" : name, Browser.load(TOKEN_KEY), Browser.load(ROOM_KEY),
                "true".equals(Browser.load(EXPERT_KEY)));
    }

    void save() {
        Browser.save(NAME_KEY, name);
        if (canResume()) {
            Browser.save(TOKEN_KEY, token);
            Browser.save(ROOM_KEY, room);
            Browser.save(EXPERT_KEY, String.valueOf(expert));
        } else {
            Browser.forget(TOKEN_KEY);
            Browser.forget(ROOM_KEY);
            Browser.forget(EXPERT_KEY);
        }
    }

    boolean canResume() {
        return token != null && room != null;
    }

    WebProfile withName(String newName) {
        return new WebProfile(newName, token, room, expert);
    }

    WebProfile withGame(String newToken, String newRoom, boolean newExpert) {
        return new WebProfile(name, newToken, newRoom, newExpert);
    }

    WebProfile withoutGame() {
        return new WebProfile(name, null, null, false);
    }
}
