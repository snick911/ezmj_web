package ezmj.web;

/**
 * 存在瀏覽器裡的上次名字，牌局中還有 token 與房號，牌局結束就清掉，對應 Swing 版的 OnlineProfile。
 * token 等於這個座位的身分，所以網頁不執行別人的內容：見 index.html 的 CSP 與 Dom 的說明。
 */
record WebProfile(String name, String token, String room) {

    private static final String NAME_KEY = "ezmj.name";
    private static final String TOKEN_KEY = "ezmj.token";
    private static final String ROOM_KEY = "ezmj.room";

    static WebProfile load() {
        String name = Browser.load(NAME_KEY);
        return new WebProfile(name == null ? "" : name, Browser.load(TOKEN_KEY), Browser.load(ROOM_KEY));
    }

    void save() {
        Browser.save(NAME_KEY, name);
        if (canResume()) {
            Browser.save(TOKEN_KEY, token);
            Browser.save(ROOM_KEY, room);
        } else {
            Browser.forget(TOKEN_KEY);
            Browser.forget(ROOM_KEY);
        }
    }

    boolean canResume() {
        return token != null && room != null;
    }

    WebProfile withName(String newName) {
        return new WebProfile(newName, token, room);
    }

    WebProfile withGame(String newToken, String newRoom) {
        return new WebProfile(name, newToken, newRoom);
    }

    WebProfile withoutGame() {
        return new WebProfile(name, null, null);
    }
}
