# EZMJ Web 台灣十六張麻將網頁版

[EZMJ](https://github.com/snick911/ezmj) 的連線對戰網頁版。整個客戶端都用 Java 寫，由 [TeaVM](https://teavm.org/) 編成 JavaScript，部署成靜態網站。需要 JDK 17 以上才能建置，Maven 用專案附的 wrapper，不用另外裝；瀏覽器端不需要 Java。

規則全部在 [EZMJ Server](https://github.com/snick911/ezmjService) 上跑，網頁只送決定、只顯示服務器給的局面。跟電腦打就是一人房間，由服務器補三家電腦。

**對應的畫面**：Swing 版 `ezmj` 0.6.0。之後改 Swing 的畫面或動畫，這裡也要跟著改，再更新這個版號，才查得出哪些修改還沒跟上。

## 建置與打包

牌、局面翻譯與規則說明直接編譯 `ezmj` 專案的原始碼（不含 Swing 畫面），所以要把它 clone 在隔壁的資料夾，和 `ezmjService` 一樣。

```
git clone https://github.com/snick911/ezmj.git ..\ezmj
powershell -ExecutionPolicy Bypass -File build.ps1
```

腳本會編譯、執行測試（失敗就中止），再把成品放到 `dist\`：

| 檔案 | 說明 |
|---|---|
| `ezmj-web-<版號>\` | 靜態網站，上傳這個資料夾的內容就可以 |
| `ezmj-web-<版號>.zip` | 同樣的內容壓縮起來 |

版號只改 `pom.xml` 一處，網頁畫面上的版號與靜態檔的快取參數都從這裡來。

### 打包流程

每次打包前依序做：

1. 記下修改內容：寫進 `CHANGELOG.md`，把「未發布」改成 `## <新版號> - <日期>`，再開一個新的「未發布」。
2. 版號增加：改 `pom.xml`，規則和 `ezmj` 一樣。動了前面的碼，後面的碼歸零。
3. 執行 `build.ps1`。

`CHANGELOG.md` 沒有這個版號的段落，或 `dist\` 已經有這個版號的成品時，腳本會直接停止。

## 開發

```
mvnw.cmd -q process-classes
java tools/DevServer.java 8080 target/site
```

另外開一台本機服務器，用 `?server=` 連過去，不要碰到線上的：

```
java -jar ..\ezmjService\dist\ezmj-server-<版號>.jar 7788
```

然後開 <http://localhost:8080/?server=ws://localhost:7788/ws>。只有網頁開在 `localhost` 或 `127.0.0.1` 時才看 `server` 參數。

## 測試

`test/ezmj/web/AllTests.java` 不依賴框架，直接執行 `main`，只測不碰瀏覽器的部分：訊息格式、協定版本、樂譜。

畫面與連線要在瀏覽器裡實際打。`tools/autoplay.js` 只操作畫面上的按鈕與牌，和真人一樣，會打完一整場並把結果留在 `window.__auto`：

- 預設開多人桌，第二位真人直接走 WebSocket。`window.__autoOptions` 可以換成一人房間與不同的籌碼。
- 把這段貼到瀏覽器主控台。分頁要在前景，背景分頁不會更新畫面，也就點不到牌。

## 程式結構

全部在 `ezmj.web` 套件。類別名字盡量和 Swing 版一樣，改畫面時才找得到對應的地方。

| 類別 | 職責 | 對應的 Swing 類別 |
|---|---|---|
| `Main` | 進入點，切換畫面 | `MainFrame` |
| `WebClient`、`Messages` | 連線、ping、重連；組訊息 | `OnlineClient` |
| `OnlineController` | 把服務器的訊息演到畫面上 | 同名 |
| `TablePresenter` | 動畫、音效、語音、動作列與選牌 | 同名 |
| `TablePanel`、`TableCanvas` | 牌桌主畫面；用 Canvas 畫的牌桌與動畫 | 同名 |
| `TilePainter`、`TileArt` | 牌面與花牌圖案 | 同名 |
| `Gfx` | Canvas 的小包裝，自己記座標轉換 | Java2D |
| `Theme` | 顏色與字型，HTML 的樣式在 `web/style.css` | 同名 |
| `OnlinePanel`、`RoomPanel` | 大廳、房間 | 同名 |
| `ResultDialog`、`FinalDialog`、`HistoryDialog`、`HelpDialog`、`SettingsDialog`、`Dialog` | 各種視窗 | 同名 |
| `SoundEffects`、`BackgroundMusic`、`MusicScores`、`VoiceLines` | 音效、音樂、語音 | 同名 |
| `WebSettings`、`WebProfile` | 存在瀏覽器裡的設定與上次的牌局 | `AppSettings`、`OnlineProfile` |
| `Browser`、`Dom`、`AudioOut`、`GridTable`、`TileStrip` | 瀏覽器的小工具 | |

直接用 `ezmj` 原始碼的部分：`ezmj.model`、`ezmj.online` 的 `OnlineGame`、`RoomInfo`、`RoomSettings`、`Json`，`ezmj.engine` 的 `RoundView`、`Claim` 等，以及說明用的 `ezmj.rules.ScoreGuide`、`Scorer`。TeaVM 只編譯用得到的類別，`OnlineClient` 裡的執行緒與 Socket 不會進到網頁，只有 `PROTOCOL` 這個常數會。所以**協定版本改了，重新編譯就跟上**，但新訊息的處理還是要連網頁一起看。

## 連線

服務器固定是 `wss://ezmj-server.onrender.com/ws`，常數在 `ServerAddress.RENDER`，畫面上不能改。免費主機閒置後會睡著，連線超過 3 秒就顯示正在喚醒。

- **斷線重連**：最多試 3 分鐘，這段時間座位由電腦代打。分頁回到前景時馬上再試。關掉網頁的話，重新打開按「接回牌局」。
- **背景分頁**：瀏覽器會放慢背景分頁的計時器，可能趕不上服務器 60 秒的限制。收到訊息時也會順便檢查該不該送 ping，牌局進行中服務器一直有訊息，就不靠計時器。真的斷了就靠重連。
- **版本不合**：網頁和服務器分開部署，瀏覽器可能還留著舊版。服務器回 `protocolMismatch` 時會請玩家重新整理。

## 資安

token 等於座位的身分，存在瀏覽器的 `localStorage`，牌局結束就清掉。所以：

- 文字一律用 `innerText`，不要用 `innerHTML`，玩家的名字來自網路。
- `index.html` 的 CSP 只准執行網站自己的程式，也只准連服務器與本機。
- `?server=` 只有網頁開在本機時才有效，不然別人可以用一個連結把 token 騙走。

## 部署

網址是 <https://snick911.github.io/ezmj_web/>。push 到 `main` 就由 `.github/workflows/pages.yml` 建置、跑測試並部署到 GitHub Pages，失敗的話舊版本繼續運作。repo 的 Settings → Pages → Source 要選 GitHub Actions。

- 引擎固定在 workflow 的 `EZMJ_COMMIT`，更新 `ezmj` 不會觸發部署。要換引擎就改這個值再 push，新的 commit 要先 push 到 GitHub。
- 部署網頁不會中斷牌局，服務器不受影響。玩家的瀏覽器如果還開著舊版，服務器改協定時會提示重新整理。

不要放進 `ezmjService`：每次部署服務器都會中斷進行中的牌局，閒置時也會讓玩家對著空白頁等。服務器沒有檢查 Origin，網頁不必和服務器同源。

靜態檔的網址帶著版號，改版後瀏覽器會拿到新的。`index.html` 本身要設定成不快取或很短的快取。

## 和 Swing 版的差異

| 項目 | 網頁版 |
|---|---|
| 單機模式 | 用一人房間代替，服務器要在線上。不能不限時（最長每步 300 秒），不能存檔，沒有電腦代打 |
| 統計、成就、榮譽榜 | 沒有 |
| 牌面的字 | 優先用系統的楷書，和 Swing 一樣；沒有時用 `web/fonts/ezmj-kai.woff2`，字形略有差異 |
| 語音 | Web Speech API，聲音依裝置而異，沒有中文語音的裝置不出聲 |
| 音樂 | 取樣率 22 kHz，瀏覽器只有一條執行緒，每次只合成一小段 |
| 視窗大小 | 跟著瀏覽器縮放，沒有解析度選項 |
| 手機 | 還沒有調整版面，直向放不下 |

## 字型

`web/fonts/ezmj-kai.woff2` 是 [霞鶩文楷 TC](https://github.com/lxgw/LxgwWenkaiTC) 的子集，只有牌面用到的 24 個字，授權是 SIL Open Font License，全文在 `web/fonts/OFL.txt`。牌面要加新的字就要重新產生。
