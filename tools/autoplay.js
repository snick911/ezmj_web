// 自動打完一場，用來驗證網頁版。只操作畫面上的按鈕與牌，和真人一樣，不呼叫程式內部。
// 用法：開 http://localhost:8080/?server=ws://localhost:7788/ws，把這段貼到瀏覽器主控台，
//       再看 window.__auto。second 是 true 時另外用 WebSocket 加入一個真人玩家，測多人桌。
// 不要對線上的服務器跑。
(function (options) {
  const S = window.__auto = {
    rounds: 0, clicks: {}, errors: [], hints: new Set(), results: [], final: null, done: false,
    started: Date.now(), second: { messages: 0, discards: 0, wins: 0, ready: 0, errors: [] },
  };
  window.addEventListener('error', (e) => S.errors.push(String(e.message)));

  const buttons = (selector) => [...document.querySelectorAll(selector)];
  const find = (selector, prefix) => buttons(selector).find((b) => b.innerText.startsWith(prefix) && !b.disabled);
  const press = (button, name) => {
    S.clicks[name] = (S.clicks[name] || 0) + 1;
    button.click();
  };

  function fillName() {
    const name = document.querySelector('.name-row input');
    name.value = '自動玩家';
    name.dispatchEvent(new Event('input'));
  }

  /** 按了開房間或跟電腦打之後跳出來的設定：籌碼、底、每台，電腦速度選快，再按確定。 */
  function fillSetup() {
    const selects = document.querySelectorAll('.setup-form select');
    selects[0].value = String(options.chips);
    selects[1].value = String(options.base);
    selects[2].value = String(options.tai);
    selects[selects.length - 1].value = 'FAST';
    for (const select of selects) {
      select.dispatchEvent(new Event('change'));
    }
    press(find('.setup-card button', options.second ? '開房間' : '開始牌局'), 'setup');
  }

  /** 從右到左點過去，點到能打的牌為止。之後的點擊因為已經不在等待，不會有作用。 */
  function clickHand() {
    const canvas = document.querySelector('.table-canvas canvas');
    if (!canvas) {
      return;
    }
    const r = canvas.getBoundingClientRect();
    // 手機的牌比較矮，靠近底部才點得到
    for (const y of [r.bottom - 22, r.bottom - 40]) {
      for (let x = r.right - 4; x > r.left; x -= 8) {
        for (const type of ['mousemove', 'mousedown']) {
          canvas.dispatchEvent(new MouseEvent(type, { clientX: x, clientY: y, bubbles: true }));
        }
      }
    }
  }

  /** 第二位真人，只送決定，不看畫面。 */
  function joinSecond(url, code) {
    const socket = new WebSocket(url);
    const send = (message) => socket.send(JSON.stringify(message));
    let hand = [];
    let drawn = null;
    socket.onopen = () => send({ type: 'hello', protocol: 1, name: '第二玩家' });
    socket.onmessage = (event) => {
      S.second.messages++;
      const m = JSON.parse(event.data);
      if (m.type === 'welcome') {
        send({ type: 'joinRoom', code });
      } else if (m.type === 'state') {
        hand = m.hand;
        drawn = m.drawn;
      } else if (m.type === 'askTurn') {
        if (m.canSelfWin) {
          S.second.wins++;
          send({ type: 'selfWin', waitId: m.waitId });
        } else {
          S.second.discards++;
          send({ type: 'discard', waitId: m.waitId, tile: drawn || hand[hand.length - 1] });
        }
      } else if (m.type === 'askClaim') {
        const win = m.options.findIndex((o) => o.kind === 'WIN');
        if (win >= 0) {
          S.second.wins++;
          send({ type: 'claim', waitId: m.waitId, option: win });
        } else {
          send({ type: 'pass', waitId: m.waitId });
        }
      } else if (m.type === 'roundEnd') {
        S.second.ready++;
        send({ type: 'ready' });
      } else if (m.type === 'error') {
        S.second.errors.push(m.code);
      }
    };
    window.setInterval(() => {
      if (socket.readyState === 1) {
        send({ type: 'ping' });
      }
    }, 20000);
    S.second.socket = socket;
  }

  let phase = 'form';
  fillName();
  press(find('.start-buttons button', options.second ? '開房間' : '跟電腦打'), 'start');
  fillSetup();
  phase = options.second ? 'room' : 'playing';

  const timer = window.setInterval(() => {
    if (phase === 'room') {
      // 標題是房間名稱，房號在下一行；舊服務器沒有名稱時標題就是房號
      const title = [...document.querySelectorAll('.room-card .code-line, .room-heading .title')]
        .map((element) => element.textContent).find((text) => text.startsWith('房號 '));
      if (title) {
        const code = title.replace('房號 ', '');
        joinSecond(new URLSearchParams(location.search).get('server'), code);
        phase = 'waiting-second';
      }
      return;
    }
    if (phase === 'waiting-second') {
      if (buttons('.room-players td').some((td) => td.innerText === '第二玩家')) {
        press(find('button', '開始牌局'), 'start');
        phase = 'playing';
      }
      return;
    }
    if (window.__autoHold) {
      return;
    }
    const finalButton = find('.card button', '回到房間');
    if (finalButton) {
      S.final = document.querySelector('.card .grid').innerText;
      S.finalHeadline = document.querySelector('.card .headline').innerText;
      press(finalButton, 'final');
      S.done = true;
      S.ms = Date.now() - S.started;
      window.clearInterval(timer);
      return;
    }
    const next = find('.result-card button', '下一局') || find('.result-card button', '查看最終排名');
    if (next) {
      S.rounds++;
      S.results.push(document.querySelector('.result-card .headline').innerText);
      S.logErrors = S.logErrors || [];
      for (const line of (document.querySelector('.log')?.innerText || '').split('\n')) {
        if (line.includes('不能') || line.includes('沒有這個')) {
          S.logErrors.push(line);
        }
      }
      press(next, next.innerText.startsWith('下一局') ? 'next' : 'toFinal');
      return;
    }
    if (document.querySelector('.backdrop')) {
      return;
    }
    const hint = document.querySelector('.hint');
    if (!hint) {
      return;
    }
    const text = hint.innerText;
    const actions = buttons('.action-buttons button');
    // 打出牌後聽牌了才有這顆，閒著或被問要不要宣告時都可能出現，按一半的機會
    const declare = actions.find((b) => b.innerText === '聽牌');
    if (declare && !S.listenSkipped) {
      if (Math.random() < .5) {
        press(declare, '報聽');
        return;
      }
      S.listenSkipped = true;
    }
    if (!declare) {
      S.listenSkipped = false;
    }
    const win = actions.find((b) => b.innerText === '自摸' || b.innerText === '胡');
    if (win) {
      press(win, win.innerText);
      return;
    }
    const pass = actions.find((b) => b.innerText === '過');
    if (pass) {
      const claims = actions.filter((b) => b !== pass);
      if (claims.length > 0 && Math.random() < .5) {
        const choice = claims[Math.floor(Math.random() * claims.length)];
        press(choice, choice.innerText);
      } else {
        press(pass, '過');
      }
      return;
    }
    if (text.includes('選要') || text.includes('再選一張')) {
      S.clicks['選牌'] = (S.clicks['選牌'] || 0) + 1;
      clickHand();
      return;
    }
    if (text.includes('輪到你了') || text.includes('要打一張牌出去')) {
      const kong = actions.find((b) => b.innerText === '槓');
      if (kong && Math.random() < .5) {
        press(kong, '槓');
        return;
      }
      S.clicks['打牌'] = (S.clicks['打牌'] || 0) + 1;
      clickHand();
    }
  }, 120);
})(window.__autoOptions || { chips: 500000, base: 50, tai: 10, second: true });
