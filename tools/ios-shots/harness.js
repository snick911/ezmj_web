// iPhone 模擬器截圖用，只放進 run.sh 做的 index.html 副本，不會上線。
// 自己走過大廳、跟電腦打的牌桌、「全螢幕」的說明與橫向的牌桌，每一步把量到的大小 POST 到 /report，
// 等 run.sh 截好圖（GET /ack/<步驟> 回 200）才往下走。要轉橫向時報 rotate-please，由 run.sh 轉。
(function () {
  'use strict';

  // 不出聲：整份設定要寫齊，缺一個就讀不懂、退回預設。ezmj.js 在後面才載入，所以這裡寫的會被讀到。
  localStorage.setItem('ezmj.settings', JSON.stringify({
    music: 'OFF', playerGender: 'MALE', musicVolume: 0, soundVolume: 0, voiceVolume: 0, sound: false, voice: false,
    playerVoiceVolume: 0, animation: true, playerVoice: true, fontPercent: 100, tilePercent: 100
  }));
  localStorage.setItem('ezmj.name', 'iPhone');
  localStorage.removeItem('ezmj.token');
  localStorage.removeItem('ezmj.room');

  function sleep(ms) {
    return new Promise(function (resolve) { setTimeout(resolve, ms); });
  }

  async function waitFor(what, test, timeoutMs) {
    var deadline = Date.now() + timeoutMs;
    while (Date.now() < deadline) {
      var found = test();
      if (found) {
        return found;
      }
      await sleep(200);
    }
    throw new Error('等太久沒有 ' + what);
  }

  function button(text) {
    var all = document.querySelectorAll('button');
    for (var i = 0; i < all.length; i++) {
      if (all[i].innerText.trim() === text) {
        return all[i];
      }
    }
    return null;
  }

  function box(selector) {
    var e = document.querySelector(selector);
    if (!e) {
      return null;
    }
    var r = e.getBoundingClientRect();
    return {x: Math.round(r.left), y: Math.round(r.top), w: Math.round(r.width), h: Math.round(r.height),
      font: getComputedStyle(e).fontSize};
  }

  /** 讓開瀏海與橫條的寬度：一個看不見的元素照 env() 留白，量它的 padding。CSP 准用 style.setProperty。 */
  function safeArea() {
    var probe = document.createElement('div');
    ['top', 'right', 'bottom', 'left'].forEach(function (side) {
      probe.style.setProperty('padding-' + side, 'env(safe-area-inset-' + side + ')');
    });
    probe.style.setProperty('position', 'fixed');
    probe.style.setProperty('visibility', 'hidden');
    document.body.appendChild(probe);
    var cs = getComputedStyle(probe);
    var result = [cs.paddingTop, cs.paddingRight, cs.paddingBottom, cs.paddingLeft].join(' ');
    probe.remove();
    return result;
  }

  function metrics() {
    return {
      inner: innerWidth + 'x' + innerHeight,
      dpr: devicePixelRatio,
      rootFont: getComputedStyle(document.documentElement).fontSize,
      bodyFont: getComputedStyle(document.body).fontSize,
      safe: safeArea(),
      landscape: matchMedia('(orientation: landscape)').matches,
      standalone: !!navigator.standalone,
      fullscreenApi: !!(document.fullscreenEnabled || document.webkitFullscreenEnabled),
      userAgent: navigator.userAgent,
      boxes: {
        canvas: box('.table-canvas canvas'),
        hint: box('.hint'),
        menu: box('.menu-button'),
        fullscreen: box('.fullscreen-button'),
        talk: box('.talk-button'),
        actionBar: box('.action-bar'),
        lobbyCard: box('.lobby-card')
      }
    };
  }

  async function report(step, extra) {
    var body = {step: step, at: new Date().toISOString(), metrics: metrics()};
    if (extra) {
      body.extra = extra;
    }
    await fetch('/report', {method: 'POST', body: JSON.stringify(body)});
  }

  /** 報告這一步，等 run.sh 截好圖。 */
  async function shot(step) {
    await report(step);
    var deadline = Date.now() + 120000;
    while (Date.now() < deadline) {
      var response = await fetch('/ack/' + step, {cache: 'no-store'});
      if (response.ok) {
        return;
      }
      await sleep(300);
    }
    throw new Error('等太久沒有截好 ' + step);
  }

  async function run() {
    await waitFor('大廳', function () { return button('跟電腦打'); }, 60000);
    // 房間列表與在線人數要一下子
    await sleep(2500);
    await shot('lobby-portrait');

    button('跟電腦打').click();
    var start = await waitFor('開始牌局', function () { return button('開始牌局'); }, 10000);
    start.click();
    await waitFor('牌桌', function () { return document.querySelector('.table-screen canvas'); }, 60000);
    // 擲骰子與發牌的動畫
    await sleep(4000);
    await shot('table-portrait');

    // iPhone 不能全螢幕，按鈕跳出加入主畫面的說明；能全螢幕的瀏覽器（例如在電腦上試跑）就不按
    var fullscreen = document.querySelector('.fullscreen-button');
    if (fullscreen && !(document.fullscreenEnabled || document.webkitFullscreenEnabled)) {
      fullscreen.click();
      await waitFor('全螢幕的說明', function () { return document.querySelector('.backdrop'); }, 5000);
      await sleep(500);
      await shot('tip-portrait');
      var ok = button('確定');
      if (ok) {
        ok.click();
      }
    } else {
      await report('no-fullscreen-button');
    }

    await report('rotate-please');
    await waitFor('轉成橫向', function () { return matchMedia('(orientation: landscape)').matches; }, 60000);
    await sleep(2500);
    await shot('table-landscape');

    var menu = document.querySelector('.menu-button');
    if (menu) {
      menu.click();
      await sleep(1200);
      await shot('menu-landscape');
    }
    await report('done');
  }

  window.addEventListener('load', function () {
    run().catch(function (e) {
      report('error', {message: String(e && e.message || e)});
    });
  });
})();
