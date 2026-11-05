# EZMJ Web 台灣十六張麻將網頁版

[EZMJ](https://github.com/snick911/ezmj) 的連線對戰網頁版。整個客戶端都用 Java 寫，由 [TeaVM](https://teavm.org/) 編成 JavaScript，部署成靜態網站。需要 JDK 17 以上才能建置，Maven 用專案附的 wrapper，不用另外裝；瀏覽器端不需要 Java。

規則全部在 [EZMJ Server](https://github.com/snick911/ezmjService) 上跑，網頁只送決定、只顯示服務器給的局面。跟電腦打就是一人房間，由服務器補三家電腦。

**對應的畫面**：Swing 版 `ezmj` 0.9.0。之後改 Swing 的畫面或動畫，這裡也要跟著改，再更新這個版號，才查得出哪些修改還沒跟上。

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

`test/ezmj/web/AllTests.java` 不依賴框架，直接執行 `main`，只測不碰瀏覽器的部分：訊息格式、協定版本、樂譜、骰子的點數。

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
| `OnlinePanel`、`LobbyBrowser`、`RoomPanel` | 大廳與房間列表、房間 | 同名 |
| `ResultDialog`、`FinalDialog`、`HistoryDialog`、`HelpDialog`、`SettingsDialog`、`Dialog` | 各種視窗 | 同名 |
| `SoundEffects`、`BackgroundMusic`、`MusicScores`、`VoiceLines` | 音效、音樂、語音 | 同名 |
| `WebSettings`、`WebProfile` | 存在瀏覽器裡的設定與上次的牌局 | `AppSettings`、`OnlineProfile` |
| `Browser`、`Dom`、`AudioOut`、`GridTable`、`TileStrip` | 瀏覽器的小工具 | |

直接用 `ezmj` 原始碼的部分：`ezmj.model`、`ezmj.online` 的 `OnlineGame`、`RoomInfo`、`RoomSettings`、`RoomListing`、`Json`，`ezmj.engine` 的 `RoundView`、`Claim`、畫牌牆用的 `WallRing` 等，以及說明用的 `ezmj.rules.ScoreGuide`、`Scorer`。TeaVM 只編譯用得到的類別，`OnlineClient` 裡的執行緒與 Socket 不會進到網頁，只有 `PROTOCOL` 這個常數會。所以**協定版本改了，重新編譯就跟上**，但新訊息的處理還是要連網頁一起看。

## 連線

服務器固定是 `wss://ezmj-server.onrender.com/ws`，常數在 `ServerAddress.RENDER`，畫面上不能改。免費主機閒置後會睡著，連線超過 3 秒就顯示正在喚醒。

- **斷線重連**：最多試 3 分鐘，這段時間座位由電腦代打。分頁回到前景時馬上再試。關掉網頁的話，重新打開按「接回牌局」。
- **背景分頁**：瀏覽器會放慢背景分頁的計時器，可能趕不上服務器 60 秒的限制。收到訊息時也會順便檢查該不該送 ping，牌局進行中服務器一直有訊息，就不靠計時器。真的斷了就靠重連。
- **版本不合**：網頁和服務器分開部署，瀏覽器可能還留著舊版。服務器回 `protocolMismatch` 時會請玩家重新整理。
- **房間列表**：看得到大廳時另外開一條不登入的連線，每 5 秒送 `listRooms`，換到房間或牌桌就關掉。列表沒變就不重畫，選取與連點兩下才不會被打斷。服務器還不認得 `listRooms` 時，只在列表那裡顯示說明，開房與用房號加入照樣能用。
- **跟電腦打**：一人房間一律是私人的，打完回到房間時別人不會從大廳加進來。
- **聊天**：連線的牌桌都有，跟電腦打的一人房間也有，走牌局同一條連線的 `chat`。「紀錄」一律預設收起來，空出來的地方給聊天，收合按鈕是 CSS 畫的箭頭。服務器不補送斷線期間的訊息。說太快、太長或舊服務器不能聊天，都只顯示在聊天區。
- **擲骰子與牌牆**：照 Swing 版，規則與時間見 `ezmj` README 的「擲骰子與牌牆」。服務器沒有送骰子，用 `OnlineGame.dice` 從房號、莊家、圈風、局風與連莊數算，和同桌的 Swing 版一樣；`AllTests` 比對一組固定的點數，算法改了兩邊要一起改。骰子加發牌約 2.4 秒，要在 `roundStart` 的 `dealMs`（3 秒）之內。
- **報聽**：打出牌後服務器送 `canListen` 才出現「聽牌」按鈕，按了送 `declareListen`。`askTurn` 的 `readyDiscards` 網頁不再用。

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
| 手機 | 版面自動調整，見下面 |

## 手機

螢幕寬度 900 以下或高度 560 以下就換成手機版面，條件在 `web/style.css` 與 `Browser.isCompact`，兩邊要一致。

- 側欄收進「選單」，牌桌上的名字旁邊標出籌碼。
- 橫向時高度最缺，牌桌用滿整個畫面，版面和直向不一樣，條件是 `style.css` 的橫向那段與 `Browser.isLandscapeCompact`：
  - 動作列整個蓋在牌桌上，本身點不到：提示在自己手牌左上方，吃碰槓胡聽在右下、手牌上面，選單與全螢幕浮在右上角。位置由 `TableCanvas.syncOverlay` 算好寫在 CSS 變數 `--hint-*` 與 `--act-*`。網頁的 CSP 不准用 `style` 屬性，要用 `style.setProperty`。
  - 名字、籌碼、莊與聽不在中央方框四邊，和花牌排成一條擺在各家旁邊，見 `nameStrip`：自己的在手牌左上方，對家的在對家手牌下面靠左，左家的再下面一條，右家的在右上角。輪到的一家名字亮起。
  - 中央只剩圈風、張數與牌牆，中心往上挪到對家與自己手牌的中間。棄牌上下兩家每排 12 張、左右兩家每排 4 張，左右兩家一排的長度不超過棄牌離中心的距離，才不會撞到上下兩家，見 `WIDE_BOX_HALF`。
  - 手牌排滿整個寬度，但牌高最多佔畫面高度的 19%。
- 牌桌的尺寸在 `TableCanvas`：`unit` 讓小螢幕的牌比照比例縮放時大，`discardColumns` 在短邊排不下三排棄牌時讓每排多放幾張。大視窗和 Swing 版一樣。
- 直向放不下一排夠大的牌時，手牌分成兩排，見 `twoLineHand`。位置還是當成一長排來算，由 `RowLayout` 折到第二排。
- 「全螢幕」按鈕切換整頁全螢幕，直向與橫向都有，再按一次或按手機的返回鍵離開。不會自己轉向，要橫向就解除旋轉鎖定再轉手機。iPhone 的 Safari 只有影片能全螢幕，所以沒有這顆按鈕。
- 手指點牌是點一下抬起、再點一次才打出，滑鼠還是點一下就打出。
- 測試時用瀏覽器開發工具的手機模式，但那不是真的手機：iOS 的 Safari、網址列伸縮與切到背景要用實機看。

## 字型

`web/fonts/ezmj-kai.woff2` 是 [霞鶩文楷 TC](https://github.com/lxgw/LxgwWenkaiTC) 的子集，只有牌面用到的 24 個字，授權是 SIL Open Font License，全文在 `web/fonts/OFL.txt`。牌面要加新的字就要重新產生。

## 授權

Copyright © 2026 snick911

雙重授權，依用途選一種：

- **[AGPL-3.0](LICENSE)**（`AGPL-3.0-only`）：任何人都可以使用、修改、散布，包括商業用途，但修改過的版本要用同樣的授權公開完整原始碼。修改後架成網站或服務給別人用，也要讓使用者拿得到原始碼。
- **商業授權**：要用在不公開原始碼的產品或服務，請到 [GitHub Issues](https://github.com/snick911/ezmj_web/issues) 開一則標題寫「商業授權」的 issue，另外談條件。

[`ezmj`](https://github.com/snick911/ezmj) 與 [`ezmjService`](https://github.com/snick911/ezmjService) 是同樣的授權。網頁會把 `ezmj` 的程式碼一起編進去，所以兩邊的條款都適用。

大廳下方的版號旁有著作權、授權與原始碼的連結，「說明」的「關於」有完整的聲明，內容在 `HelpDialog.showAbout`。AGPL 規定原版的畫面有顯示這些，改過的版本也要顯示，不要拿掉。

下面這些不是這個專案寫的，保留原本的授權：

| 部分 | 授權 |
|---|---|
| `web/fonts/ezmj-kai.woff2` | SIL Open Font License 1.1，全文在 `web/fonts/OFL.txt` |
| TeaVM 編進 `ezmj.js` 的執行環境 | Apache License 2.0 |
| `mvnw`、`mvnw.cmd`、`.mvn/` | Apache License 2.0，見各檔案開頭 |

要送 PR 請先看 [CONTRIBUTING.md](CONTRIBUTING.md)。
