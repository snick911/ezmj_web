# EZMJ Web 台灣十六張麻將網頁版

[EZMJ](https://github.com/snick911/ezmj) 的連線對戰網頁版。整個客戶端都用 Java 寫，由 [TeaVM](https://teavm.org/) 編成 JavaScript，部署成靜態網站。需要 JDK 17 以上才能建置，Maven 用專案附的 wrapper，不用另外裝；瀏覽器端不需要 Java。

規則全部在 [EZMJ Server](https://github.com/snick911/ezmjService) 上跑，網頁只送決定、只顯示服務器給的局面。跟電腦打就是一人房間，由服務器補三家電腦。

**對應的畫面**：Swing 版 `ezmj` 0.9.1。之後改 Swing 的畫面或動畫，這裡也要跟著改，再更新這個版號，才查得出哪些修改還沒跟上。

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

`test/ezmj/web/AllTests.java` 不依賴框架，直接執行 `main`，只測不碰瀏覽器的部分：訊息格式、協定版本、樂譜、曲目和 Swing 版的 `MusicTrack` 一樣、錄好的曲子有放進網站、錄好的曲子壓到多大聲、司儀的話、骰子的點數、PDF 的結構、金額的千分位、榮譽榜的解析、大廳列表的設定與補位的公告文字。

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
| `HonorRollDialog`、`HonorRollMessage` | 連線牌局的榮譽榜：視窗；讀服務器的 `honorRoll` | `HelpDialog` 的榮譽榜頁，欄位一樣 |
| `ShareDialog`、`GameReport`、`PdfWriter` | 牌局結束後的結果圖與輸贏歷程 PDF | 網頁版才有 |
| `WaitingOverlay` | 等服務器回應時蓋住畫面的提示 | 網頁版才有 |
| `SoundEffects`、`BackgroundMusic`、`MusicScores`、`VoiceLines` | 音效、音樂、語音與司儀 | 同名，錄好的曲子是 `RecordedMusic` |
| `VoiceTalk`、`VoiceRecorder`、`VoicePlayer` | 玩家的語音訊息：按住說話、麥克風錄音、播放，見「玩家語音」；左下角的按鈕在 `TablePanel` | `VoiceTalk`、`VoicePlayer` 同名，錄音在 `VoiceTalk` 裡；按鈕是 `TalkButton` |
| `WebMusic` | 背景音樂的選項，見下面的「背景音樂」 | `model.MusicTrack` |
| `WebSettings`、`WebProfile` | 存在瀏覽器裡的設定與上次的牌局 | `AppSettings`、`OnlineProfile` |
| `Browser`、`Dom`、`AudioOut`、`GridTable`、`TileStrip` | 瀏覽器的小工具 | |

直接用 `ezmj` 原始碼的部分：`ezmj.model`、`ezmj.online` 的 `OnlineGame`、`RoomInfo`、`RoomSettings`、`RoomListing`、`Json`，`ezmj.engine` 的 `RoundView`、`Claim`、畫牌牆用的 `WallRing` 等，以及說明用的 `ezmj.rules.ScoreGuide`、`Scorer`。TeaVM 只編譯用得到的類別，`OnlineClient` 裡的執行緒與 Socket 不會進到網頁，只有 `PROTOCOL` 這個常數會。所以**協定版本改了，重新編譯就跟上**，但新訊息的處理還是要連網頁一起看。

## 連線

服務器固定是 `wss://ezmj-server.onrender.com/ws`，常數在 `ServerAddress.RENDER`，畫面上不能改。免費主機閒置後會睡著，連線超過 3 秒就顯示正在喚醒。

- **第一次連線**：按開房間、跟電腦打、加入或接回牌局之後，到收到 `welcome` 之前連不上就照斷線重連的間隔一直重試，畫面維持「正在喚醒服務器」，2 分鐘（`WebClient.FIRST_CONNECT_FOR_MS`）還連不上才說連不上服務器。服務器在 Render 重新部署的一分多鐘裡會直接拒絕連線，從睡眠醒來時偶爾也會，以前試一次就放棄。只看大廳列表與榮譽榜的連線不重試，列表每 5 秒會再要。和 Swing 版的 `OnlineClient` 不同。
- **斷線重連**：最多試 3 分鐘，這段時間座位由電腦代打。分頁回到前景時馬上再試。關掉網頁的話，重新打開按「接回牌局」。
- **背景分頁**：瀏覽器會放慢背景分頁的計時器，可能趕不上服務器 60 秒的限制。收到訊息時也會順便檢查該不該送 ping，牌局進行中服務器一直有訊息，就不靠計時器。真的斷了就靠重連。
- **版本不合**：網頁和服務器分開部署，瀏覽器可能還留著舊版。服務器回 `protocolMismatch` 時會請玩家重新整理。
- **房間列表**：看得到大廳時另外開一條不登入的連線，每 5 秒送 `listRooms`，換到房間或牌桌就關掉。列表沒變就不重畫，選取與連點兩下才不會被打斷。服務器還不認得 `listRooms` 時，只在列表那裡顯示說明，開房與用房號加入照樣能用。
- **榮譽榜**：大廳的「榮譽榜」每次打開都另外開一條不登入的連線送 `honorRoll`，收到就關掉，視窗收掉時還沒回應的也關掉。資料是服務器記的連線牌局，和 Swing 版選單裡本機單機牌局的榮譽榜是分開的。解析放在 `HonorRollMessage`，不碰瀏覽器，`AllTests` 直接測；放在視窗類別裡的話，JVM 載入時會要 TeaVM 的 DOM 類別。贏錢金額照底與每台分榜，選了「贏錢金額」時分頁下面多一個「底台」選單，打開時先看最近有新紀錄的那組（同一天的看底台大的），和 Swing 版一樣。分組讀服務器的 `winningsByStakes`；0.10.0 以前的服務器沒有，就把不分底台的 `winnings` 照底台分開。分組由網頁版自己做（`HonorRollMessage.StakesBoard`），不用 `ezmj` 新的 `HonorRoll.Stakes`，所以比較舊的 `ezmj`（例如以前鎖定的 `9348070`）照樣編得過。
- **在線人數**：`rooms` 的 `online`（連線數，看大廳的也算）與 `playingRooms`（進行中的牌局，私人房與一人房間也算）顯示在列表上面。`ezmjService` 0.4.0 和更早的版本沒有這兩個欄位，這一行就藏起來。
- **等服務器**：開房間、加入、接回與開局送出後，整個畫面蓋上 `WaitingOverlay`，到換成房間或牌桌、出錯或斷線才收。跟電腦打是開好房間就接著開局，中間不收，畫面不會閃一下房間。
- **跟電腦打**：一人房間一律是私人的，打完回到房間時別人不會從大廳加進來。
- **名字與按鈕**：大廳的按鈕與設定視窗的送出只在正在連線時不能按，名字沒填、太長、有不雅字眼、房號不完整、沒選房間或底台太大，按下去才用 `Dialog.message` 跳出提醒，按確定把游標放回要改的欄位（`OnlinePanel.nameAccepted`）。不雅字眼用 `ezmj` 的 `model.BadWords`，和 Swing 版同一份，房間名稱與聊天也用它。
- **開房間的設定**：首頁只有名字、房間列表與按鈕。按「跟電腦打」或「開房間」才在 `OnlinePanel.openSetup` 跳出設定，視窗每次都放同一份欄位，選過的值留到重新整理網頁為止。每個設定的說明在「說明」的「建立牌局」（`HelpDialog.showSetup`），設定改了那裡也要改。
- **聊天**：連線的牌桌都有，跟電腦打的一人房間也有，走牌局同一條連線的 `chat`。「紀錄」一律預設收起來，空出來的地方給聊天，收合按鈕是 CSS 畫的箭頭。服務器不補送斷線期間的訊息。說太快、太長或舊服務器不能聊天，顯示在聊天區，牌桌上自己那一側也跳一下。有不雅字眼的不送出，字留在輸入框；收到別人的由 `OnlineGame.parseChat` 把不雅字眼換成＊。`ezmjService` 0.9.0 起服務器也擋，回 `badChat`。
  - 牌桌上的泡泡蓋在畫布上，位置由 `TableCanvas.syncChatAnchors` 寫在 CSS 變數 `--chat-x0` 到 `--chat-y3`，和 `--hint-*` 一樣用 `style.setProperty`。
  - 在牌桌上按 Enter 跳出輸入框。按鍵聽整頁的，牌桌換掉之後第一次按鍵時拿掉；游標在輸入框、按鈕或連結上、有 `.backdrop` 或 `.waiting` 蓋著，或輸入法還在選字時不搶。
- **玩家語音**：牌桌左下角的麥克風按鈕，按住說話、放開就自動廣播的語音訊息，要 `ezmjService` 0.14.0，見下面的「玩家語音」。
- **擲骰子與牌牆**：照 Swing 版，規則與時間見 `ezmj` README 的「擲骰子與牌牆」。牌牆在服務器上照真的牌桌疊好、擲骰子開門，服務器在 `roundStart` 與 `state` 送 `dice`，`OnlineGame.dice` 用它，畫面上的開門處就是服務器真的拿牌的地方，和同桌的 Swing 版一樣。0.12.0 和更早的服務器沒有送，才從房號、莊家、圈風、局風與連莊數算；`AllTests` 兩種都比對，算法改了要和 `ezmj` 的 `OnlineTest` 一起改。骰子加發牌約 2.4 秒，要在 `roundStart` 的 `dealMs`（3 秒）之內。
- **聽牌**（程式與協定叫報聽，畫面上一律寫「聽牌」，紀錄裡 `OnlineGame.describe` 的「報聽」也換掉）：照 Swing 版的單機牌局。
  - 跟電腦打與多人的房間都在打牌之前聽牌：`askTurn` 的 `readyDiscards` 有東西時，動作列多一個半透明的「聽牌」（平常 50% 不透明，滑鼠移上去才不透明，不閃）。按了打出去會聽牌的牌加上金框、其他蓋起來，提示列出打哪張聽哪些牌；點一張就送 `listen`（打出並報聽），「取消」回到一般打牌。沒按就打出去的，服務器照樣送 `canListen`，網頁不理。
  - 專家模式打完牌才問：服務器不給 `readyDiscards`，打出牌後送 `canListen` 才出現「聽牌」按鈕，按了送 `declareListen`。這個機會一直到下一次摸牌、吃碰槓或這局結束，所以別家可能在你出牌時聽牌；服務器聽牌後只補送一份 `state`、不會再問一次，`OnlineController.stillAsked` 讓這種局面不收掉自己的等待。
- **專家模式**：照 Swing 版的單機專家模式（`ezmj` 0.14.0），跟電腦打與開房間的設定視窗都有這個勾選。是房間設定，要 `ezmjService` 0.7.0：`createRoom` 帶 `expert`，房間的每個人都照 `room` 的 `options.expert` 打。
  - 勾選框只寫「開啟」，房間畫面只留「專家」標籤；有哪些不一樣（`HelpDialog.EXPERT_SUMMARY`）只寫在說明的「簡介」與「建立牌局」。
  - 每次最多等 10 秒（`OnlinePanel.EXPERT_MAX_WAIT_SECONDS`，和 Swing 版的 `GameConfig.EXPERT_MAX_WAIT_SECONDS`、服務器的 `RoomOptions` 一樣）：勾了就把等待秒數的範圍改小，比較長的改成 10 並記著，取消勾選時還原（`OnlinePanel.syncWaitLimit`）；勾著又填了比較長的，送出時提醒。服務器也會把比較長的改成 10。
  - 服務器在專家模式的房間不給 `readyDiscards`、不接受 `listen`；網頁照樣處理 `canListen`，聽牌中只寫「【聽牌中】」；紀錄只留最新一則，展開的箭頭藏起來；牌河每家只畫最後一張，放在第一格，被吃碰槓胡走就不畫；中央不寫剩幾張。
  - 牌河要知道最後一張有沒有被拿走：`TableCanvas.noteEvent` 在每次打牌時記下張數，有沒有動畫都會收到，之後張數變了就不畫。重連或接回時還沒記到，各家打出下一張才看得到。
  - 藏資訊是客戶端做的，所以 `joinRoom` 一律帶 `supports: ["expert"]`，沒帶的客戶端（現在的 Swing 版）服務器不讓它加入專家模式的房間。
  - `RoomInfo`、`RoomListing` 是 `ezmj` 的，還沒有這個欄位，所以 `OnlineController.roomExpert` 與 `LobbyBrowser.roomFlags` 直接看訊息。大廳列表的「專家」欄打勾、房間畫面在名稱旁邊標「專家」。
  - 舊服務器（0.6.0 和更早）不認得，`options` 裡沒有 `expert`：這時只有自己開的一人房間照自己勾的打，存在 `WebProfile` 的 `ezmj.expert`，接回牌局時也看它。
- **隨時補位**：開房間的設定才有（跟電腦打是私人的一人房間，沒有人會來補）。是房間設定，要 `ezmjService` 0.10.0：`createRoom` 帶 `substitute`，流程見服務器 README 的「隨時補位」。
  - `joinRoom` 與 `listRooms` 都帶 `supports` 的 `"substitute"`，服務器才讓我們中途加入、列表才有牌局中的房間。`RoomListing` 沒有 `playing` 與 `options.substitute`，`LobbyBrowser.roomFlags` 和專家模式一起直接看訊息，大廳列表的「隨時補位」欄打勾、牌局中的在名稱後面標「牌局中」。從列表加入牌局中的房間先用 `Dialog.confirm` 問一次（`OnlinePanel.SUBSTITUTE_CONFIRM`）。
  - 收到 `substituteWaiting` 就用可以取消的等待畫面蓋著（`Host.showSubstituteWaiting`），這時還沒有 `room`。取消就關掉連線，服務器發現斷線就讓出座位，所以中途斷線接回來時 token 已經失效，`loginRejected` 說座位讓出來了。之後的 `room` 不管是牌局中（上場）還是 `lobby`（整場先打完）都收掉等待的畫面。
  - 收到 `substituted` 就在牌桌上方跳公告（`TablePanel.announce`，DOM，在聊天泡泡那一層，點擊穿過去），那一家旁邊跳「補位」（`TableCanvas.showSeatBanner`），紀錄與聊天區（`appendSystemLine`）各留一行。文字在 `Substitution`，不碰瀏覽器，`AllTests` 直接測。上場的人之後就是一般的玩家。
- **連莊**：計分表莊家的圓圈「莊」右邊寫紅色粗體的連莊次數，滑鼠停著寫「莊家，連N拉N」；中央方框在「剩餘 N 張」下面用紅色粗體寫「連N拉N」，三行一起往上挪，手機橫向的方框小，排得比較緊。Swing 版單機在擲骰子前演 1.5 秒的「連莊」大字，網頁的牌局都在服務器上，`dealMs` 只有 3 秒，和 Swing 版連線一樣先不演，但司儀照樣說「連莊，連N拉N」（`TablePresenter.announceStreak`，收到 `roundStart` 之後的第一份局面時），見下面的「語音與司儀」。

## 背景音樂

和 Swing 版一樣六首，都循環播放，音量照「音樂音量」。選項在 `WebMusic`，名稱與標題和 `ezmj` 的 `model.MusicTrack` 一樣，存在設定裡的名稱也通用。網頁版自己列一份，不直接用 `MusicTrack`：workflow 的 `EZMJ_COMMIT` 固定在某個 commit，那一版的 `MusicTrack` 可能還沒有後來加的曲子，網頁版照樣要編得過、要有這些曲子。`AllTests` 對照 `MusicTrack`：它有的每一首網頁版都要有、標題一樣；它有 `recording()` 的話（用反射找，舊版沒有也編得過）檔名也要一樣。

- **合成的四首**：樂譜在 `MusicScores`，由 `BackgroundMusic` 即時合成，見「和 Swing 版的差異」。
- **錄好的兩首**「清空牌桌（Clear the Board）」「木板暖陽（Sunlight on the Floorboards）」：和 Swing 版 `src/ezmj/ui/music` 一樣的 MP3（192 kbps、約 3 分鐘，各約 4.3 MB），複製一份放在 `web/music`，`pom.xml` 照原樣複製不做取代。選到才用 `fetch` 下載，交給瀏覽器的 `decodeAudioData` 解碼，所以 CSP 的 `connect-src` 有 `'self'`；沒選的人不用下載，網站本身只多兩個檔案。網址不帶版號，改版不用重新下載，換曲子時改檔名。解碼後整首放進記憶體（48 kHz 立體聲約 70 MB），只留正在播的那首，換掉就放掉。
- 錄好的曲子混音得比較大聲，解碼後算整首的 RMS，比 -17 dBFS 大聲就壓到 -17 dBFS（`WebMusic.levelGain`，和 Swing 版的 `RecordedMusic.level` 一樣），小聲的不放大。這兩首是 -14.3 dBFS，壓 2.7 dB。
- 環境設定的選單在錄好的曲子前面畫一條線（`select` 裡的 `hr`，舊瀏覽器不畫），下面一行寫選了才下載。

## 語音與司儀

喊牌與司儀都用 Web Speech API，聲音看裝置有哪些中文語音，見 `Browser.utterance`。

- **喊牌**：女聲挑台灣的第一個女聲，男聲挑名字像男聲的（例如 Zhiwei），沒有男聲就壓低音調。新的牌名打斷還沒喊完的上一張，牌局節奏快，喊慢了會跟不上。
- **司儀**：照 Swing 版，是喊牌以外的另一個女聲，流局時說「流局」，連莊的局開始時說「連莊，連X拉X」，連一到連二十，再多只說「連莊」（`VoiceLines.streakText`）。念得慢 5%（rate 0.95），不調音高、不加驚嘆號；語音關掉時不說，音量跟著語音。聲音照 Swing 版的順序先找 Yating，再找 Edge 的 HsiaoChen、HsiaoYu，再來是 Hanhan，都要和喊牌的女聲不同；再沒有就用任何台灣女聲、任何中文女聲（粵語除外），都沒有才用喊牌的女聲。Windows 上的 Chrome 與 Edge 有 Hanhan、Yating、Zhiwei 時，喊牌是 Hanhan、司儀是 Yating，和 Swing 版一樣。
- **排隊**（`SpeechQueue`，`AllTests` 直接測）：自己排隊，一次只交一句給瀏覽器，念完才交下一句。
  - 吃碰槓胡、自摸、聽牌與司儀（一定要念的話）彼此不打斷，一句念完才念下一句，和 Swing 版一樣。
  - 牌名只喊最新的一張：還沒喊完又有新的牌名就打斷它。一定要念的話還在排或在念時先等著，只留最新的一張，念完才喊；又有一定要念的話進來，等著的牌名就不喊了。所以「聽牌」不會被下一家打牌的牌名切掉。
  - 和 Swing 版不同的兩點：瀏覽器念一句連前後的空白要一秒多，不能像 Swing 版修掉空白，全部排隊會越落越後面。所以一定要念的話會打斷正在喊的牌名（牌就擺在桌上，打牌後馬上聽牌時只聽到「聽牌」）；吃碰槓排了 1.5 秒（`CALL_STALE_MS`）還沒輪到就不念了。聽牌、胡、自摸與司儀一定念到。
  - 以「快」的速度自動打牌、一半機率吃碰（`tools/autoplay.js`）量過：只排隊不丟的話，事件到開始念的時間中位數 0.73 秒，最慢 4.3 秒。
  - 瀏覽器偶爾不通知說完了：一句念了 6 秒（`MAX_SPEAK_MS`）還沒說完，下一次要念時就停掉它往下念，等著的牌名過時了不喊。
  - 0.18.0 以前每一句都 `cancel()` 掉上一句，「聽牌」一喊出來就被下一家打牌的牌名切掉（速度「中」的電腦 0.75 秒就打下一張，「聽牌」要念將近 1 秒，手機的語音起步更慢），聽起來像沒念。

## 玩家語音

牌桌上按住說話、放開就自動廣播，經過服務器轉給同桌的真人，和 Swing 版一樣。不做即時通話：要 WebRTC 與付費的中繼伺服器，Swing 版也做不到。協定見服務器 README 的「語音」。

- **什麼時候有**：`welcome` 的 `features` 有 `voice`（`ezmjService` 0.14.0 起）的連線牌局都有按鈕，跟電腦打的一人房間也有，看得到這個功能。房間裡沒有別的真人連著線（`VoiceClip.anyoneToHear`：開打後斷線或離開的座位照樣算真人，但服務器轉不到，所以不算）時按鈕變淡（`.lonely`），按下去只在自己這一側說「桌上沒有別的真人玩家，語音沒有人聽得到」，不開麥克風。有人補位上場、斷線、接回或離開時服務器都會送 `room`，收到就重新看一次。`createRoom` 與 `joinRoom` 的 `supports` 帶 `voice`，服務器才把別人的語音轉過來。
- **按鈕**（`.talk-button`）：牌桌左下角一顆圓的按鈕，白色的麥克風兩邊各兩道金色的波紋，看得出放開就廣播出去。圖示是程式裡寫死的 SVG（`TablePanel.talkIcon`，用 `Dom.svg` 的 `createElementNS` 建，不用 `innerHTML`），Swing 版的 `TalkButton` 照同樣的座標畫。電腦版在動作列左邊那欄、上下置中；手機直向貼在動作列左下角、全螢幕的下面；橫向在自己手牌左上方、提示的左邊，提示往右讓出它的寬度。有按鈕時動作列左右兩欄至少留它的寬度（`.has-talk`），吃碰槓胡照樣置中，放不下就往下折。按下就 `setPointerCapture`，在按鈕上放開送出、移到外面放開取消；`touch-action: none`，不讓瀏覽器拿去捲動或跳出長按選單。用 Tab 移過來的話按住空白鍵或 Enter 也可以。滑鼠提示與報讀寫「按住說話，放開就自動廣播給同桌的牌友」。
- **錄音中與廣播出去**：錄音中按鈕變紅、外框一明一暗、波紋一閃一閃，牌桌上自己那一側亮出「錄音中 N 秒，放開就廣播」（`.talk-indicator`）。服務器回覆收到時按鈕往外擴散兩圈金色的波紋（`.broadcast`，連著送從頭再播，`Browser.reflow`），自己這一側的泡泡寫「〔語音 1.7 秒〕已廣播給牌友」。系統設定減少動態時都不動。
- **錄音**（`VoiceRecorder`）：`getUserMedia` 打開回音消除、降噪與自動增益，牌桌的音樂與喊牌不會錄進去；`ScriptProcessorNode` 一段一段收，接到音量 0 的 `GainNode` 才會動，喇叭不出聲。每次錄完就把麥克風的 track 停掉，分頁上不會一直亮著正在用麥克風。`ScriptProcessorNode` 不建議用了，但每個瀏覽器都還支援；換成 `AudioWorklet` 要另外放一個 JS 檔，CSP 的 `script-src 'self'` 允許。CSP 不管麥克風，GitHub Pages 是 https 才拿得到。
- **送出**（`VoiceTalk`）：不到 0.3 秒、移到外面放開都不送；錄滿 10 秒自動送出。按下去之前先看自己的頻率限制（每 30 秒 3 則，和服務器一樣），擋下來就不開麥克風。壓縮用 `ezmj` 的 `VoiceClip`：降到 8 kHz、太小聲的放大，再壓成 IMA ADPCM，一秒 4 KB，和 Swing 版一樣，不用瀏覽器的錄音格式。斷線中不送，說一聲。
- **自己送的**：服務器回一則沒有聲音、只有長度的 `voice`，聊天區的那一行用送出時留著的那份重播。`badVoice`、`voiceTooFast` 拿掉最早那份。斷線時全部丟掉，回覆對不上了。
- **播放**（`VoicePlayer`）：排在 `AudioContext` 的時間上，一則播完才播下一則，背景分頁的計時器變慢也不會亂；播的時候背景音樂壓到 0.3 倍（`BackgroundMusic.duck`）。自動播放時後面已經排了 20 秒以上就不播。按重播先停掉正在播與排著的。8 kHz 的 `AudioBuffer` 建不起來（舊的 Safari 只收 22.05 kHz 以上）就拉長三倍用 24 kHz。
- **畫面**：聊天區一行「名字：〔語音 3.2 秒〕」，後面「重播」，別人的再一顆「靜音」，同一位的每一行一起換字；牌桌上那一家旁邊跳泡泡。靜音照畫面座位加名字記，靜音的人不自動播、不跳泡泡，重播照樣能聽。
- **環境設定**：「玩家語音」（自動播放，預設開啟）與「玩家語音音量」，存在 `ezmj.settings` 的 `playerVoice`、`playerVoiceVolume`，和喊牌的「語音」分開。
- **測試**：瀏覽器的麥克風在自動測試裡用不了，在主控台把 `navigator.mediaDevices.getUserMedia` 換成回傳 `OscillatorNode` 接 `MediaStreamDestination` 的 stream，按鈕用 `PointerEvent` 按，兩個分頁開在不同的 port（各自的 `localStorage`）。

## 資安

token 等於座位的身分，存在瀏覽器的 `localStorage`，牌局結束就清掉。所以：

- 文字一律用 `innerText`，不要用 `innerHTML`，玩家的名字來自網路。
- `index.html` 的 CSP 只准執行網站自己的程式，也只准連服務器、本機與網站自己（`connect-src 'self'`，下載錄好的背景音樂）。GitHub Pages 是靜態網站，連回自己拿不走 token。
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
| 統計、成就 | 沒有 |
| 榮譽榜 | 連線牌局的，由服務器記錄，在大廳的「榮譽榜」。Swing 版選單的榮譽榜是本機單機牌局的 |
| 結果圖與輸贏 PDF | 網頁版才有，在牌局結束的視窗。PDF 每頁是一張圖，字不能選取 |
| 牌桌上的聊天泡泡、Enter 聊天 | 網頁版才有 |
| 玩家語音 | 一樣，聊天區那一行後面直接有「重播」「靜音」；Swing 是點那一行跳出選單。牌桌上的泡泡與錄音中的提示網頁版才有 |
| 牌桌上的名字 | 比 Swing 版大：中央方框四邊 17，Swing 是 16。都是正的，左右兩家直寫；英文字母與數字直寫時排得比中文字緊一點，Swing 一樣高 |
| 按鈕的金框 | 用 Tab 移過去才畫，由 `Browser.trackTabFocus` 標上 `data-tab-focus`，不用 `:focus-visible`（按過鍵盤後程式放的焦點也會畫）。Swing 看焦點是不是 Tab 移過來的 |
| 有火焰時的提示 | 電腦版和 Swing 一樣移到按鈕左邊；手機的動作列窄，照舊在按鈕上面 |
| 牌面的字 | 優先用系統的楷書，和 Swing 一樣；沒有時用 `web/fonts/ezmj-kai.woff2`，字形略有差異 |
| 語音 | Web Speech API，聲音依裝置而異，沒有中文語音的裝置不出聲。沒有語音風格與「X索」的喊法。司儀的聲音由瀏覽器挑，不能先調成一樣大聲 |
| 音樂 | 合成的曲子取樣率 22 kHz，瀏覽器只有一條執行緒，每次只合成一小段。錄好的曲子選到才下載，由瀏覽器解 MP3，不用轉檔，哪個平台都能播 |
| 視窗大小 | 跟著瀏覽器縮放，沒有解析度選項 |
| 手機 | 版面自動調整，見下面 |

## 手機

螢幕寬度 900 以下或高度 560 以下就換成手機版面，條件在 `web/style.css` 與 `Browser.isCompact`，兩邊要一致。

- 側欄收進「選單」，牌桌上的名字旁邊標出籌碼。直向時左右兩家的籌碼跟著名字直寫，只排在中央方框兩個轉角中間，才不會和上下兩家的字疊住。
- 橫向時高度最缺，牌桌用滿整個畫面，版面和直向不一樣，條件是 `style.css` 的橫向那段與 `Browser.isLandscapeCompact`：
  - 動作列整個蓋在牌桌上，本身點不到：提示在自己手牌左上方，吃碰槓胡聽在右下、手牌上面，選單與全螢幕浮在右上角。位置由 `TableCanvas.syncOverlay` 算好寫在 CSS 變數 `--hint-*` 與 `--act-*`。網頁的 CSP 不准用 `style` 屬性，要用 `style.setProperty`。
  - 名字、籌碼、莊與聽不在中央方框四邊，和花牌排成一條擺在各家旁邊，見 `nameStrip`：自己的在手牌左上方，對家的在對家手牌下面靠左，左家的再下面一條，右家的在右上角。輪到的一家名字亮起。
  - 中央只剩圈風、張數與牌牆，中心往上挪到對家與自己手牌的中間。棄牌上下兩家每排 12 張、左右兩家每排 4 張，左右兩家一排的長度不超過棄牌離中心的距離，才不會撞到上下兩家，見 `WIDE_BOX_HALF`。
  - 手牌排滿整個寬度，但牌高最多佔畫面高度的 19%。
- 牌桌的尺寸在 `TableCanvas`：`unit` 讓小螢幕的牌比照比例縮放時大，`discardColumns` 在短邊排不下三排棄牌時讓每排多放幾張。大視窗和 Swing 版一樣。
- 直向放不下一排夠大的牌時，手牌分成兩排，見 `twoLineHand`。位置還是當成一長排來算，由 `RowLayout` 折到第二排。
- 「全螢幕」按鈕切換整頁全螢幕，直向與橫向都有，再按一次或按手機的返回鍵離開。不會自己轉向，要橫向就解除旋轉鎖定再轉手機。iPhone 的 Safari 只有影片能全螢幕，所以沒有這顆按鈕。
- 手機進入牌局時自動先切全螢幕（`Browser.fullscreenOnPhone`）：跟電腦打的「開始牌局」、房間裡的「開始牌局」、加入（含補位）與接回牌局。瀏覽器只准在點按的當下切，所以在這些按鈕的處理裡直接呼叫，不等牌桌出來；加入的人在房間等房主開局時沒有自己按的按鈕，所以加入時就切。開房間時不切，房主多半還要切到別的 App 傳房號。手機的條件是手指操作（`pointer: coarse`）加上手機版面，電腦開窄視窗不會切；已經是全螢幕、不支援或被拒絕就維持原樣。離開全螢幕後同一局不會再自己切回去。
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

大廳下方的版號旁有著作權與授權，「說明」的「關於」有完整的聲明，內容在 `HelpDialog.showAbout`。AGPL 規定原版的畫面有顯示這些，改過的版本也要顯示，不要拿掉。授權條款連到 gnu.org；畫面上不放原始碼的網址，這是作者的選擇，不是 AGPL 的要求。

下面這些不是這個專案寫的，保留原本的授權：

| 部分 | 授權 |
|---|---|
| `web/fonts/ezmj-kai.woff2` | SIL Open Font License 1.1，全文在 `web/fonts/OFL.txt` |
| TeaVM 編進 `ezmj.js` 的執行環境 | Apache License 2.0 |
| `mvnw`、`mvnw.cmd`、`.mvn/` | Apache License 2.0，見各檔案開頭 |

要送 PR 請先看 [CONTRIBUTING.md](CONTRIBUTING.md)。
