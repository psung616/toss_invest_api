"use strict";

// WEB_SECURITY WS-UI: 외부 리소스 없음, 서버에서 받은 값은 textContent 로만 넣는다, 브라우저 저장소에 데이터를 두지 않는다.

function setText(id, text) {
  document.getElementById(id).textContent = text;
}

function setBadge(id, text, tone) {
  const el = document.getElementById(id);
  el.textContent = text;
  el.className = "badge " + tone;
}

async function loadStatus() {
  let response;
  try {
    response = await fetch("/api/status", {
      credentials: "same-origin",
      headers: { Accept: "application/json" },
    });
  } catch (e) {
    setText("message", "서버에 연결할 수 없습니다. 콘솔 창에서 web 모드가 실행 중인지 확인하세요.");
    return;
  }
  if (response.status === 401) {
    setText("message", "접속이 만료됐습니다. 콘솔 창에서 Enter 를 눌러 새 접속 주소를 여세요.");
    return;
  }
  if (!response.ok) {
    setText("message", "상태를 불러오지 못했습니다 (HTTP " + response.status + ").");
    return;
  }
  const status = await response.json();
  setText("message", "실행 중");
  setText("version", String(status.version));
  setBadge("keys", status.keysConfigured ? "키 설정됨" : "키 없음 - 아래에서 입력", status.keysConfigured ? "ok" : "warn");
  setBadge("orders", status.ordersEnabled ? "주문 기능 켜짐" : "주문 기능 꺼짐", status.ordersEnabled ? "warn" : "ok");
  showKeyForm(status);
  setBadge("dryrun", status.dryRun ? "DRY_RUN (실제 주문 안 나감)" : "실주문 모드", status.dryRun ? "ok" : "danger");
}

// ---- API 키 입력 (RC-003, WEB_SECURITY WS-KEY): 값은 화면에 다시 쓰지 않고, 보낸 뒤 입력칸을 바로 비운다.

function showKeyForm(status) {
  const form = document.getElementById("key-form");
  if (status.keysFromEnvironment) {
    setText("keys-title", "API 키");
    setText("keys-help", "환경변수(TOSSINVEST_CLIENT_ID·TOSSINVEST_CLIENT_SECRET)의 키를 쓰는 중입니다. 화면에서는 바꿀 수 없습니다.");
    form.hidden = true;
    return;
  }
  form.hidden = false;
  setText("keys-title", status.keysConfigured ? "API 키 교체" : "API 키 입력");
}

async function saveKeys(event) {
  event.preventDefault();
  const idInput = document.getElementById("client-id");
  const secretInput = document.getElementById("client-secret");
  const button = document.getElementById("key-submit");
  const body = JSON.stringify({ clientId: idInput.value, clientSecret: secretInput.value });
  idInput.value = "";
  secretInput.value = "";
  button.disabled = true;
  setText("key-result", "저장하고 연결을 확인하는 중…");
  try {
    const response = await fetch("/api/keys", {
      method: "POST",
      credentials: "same-origin",
      headers: {
        "Content-Type": "application/json",
        "Accept": "application/json",
        "X-Tossinvest-Local": "1",
      },
      body: body,
    });
    if (response.status === 401) {
      setText("key-result", "접속이 만료됐습니다. 콘솔 창에서 Enter 를 눌러 새 접속 주소를 연 뒤 다시 입력하세요.");
      return;
    }
    let data = null;
    try {
      data = await response.json();
    } catch (e) {
      data = null;
    }
    if (data && typeof data.message === "string") {
      setText("key-result", data.message);
    } else if (data && data.error && typeof data.error.message === "string") {
      setText("key-result", data.error.message);
    } else {
      setText("key-result", "저장하지 못했습니다 (HTTP " + response.status + ").");
    }
  } catch (e) {
    setText("key-result", "서버에 연결할 수 없습니다. 콘솔 창에서 web 모드가 실행 중인지 확인하세요.");
  } finally {
    button.disabled = false;
    loadStatus();
  }
}

// ---- 조회 (P9-5): GET /api/... 만 쓴다. 값은 createElement + textContent 로만 넣는다.

const SESSION_EXPIRED = "접속이 만료됐습니다. 콘솔 창에서 Enter 를 눌러 새 접속 주소를 여세요.";

/** 조회 API 호출. 실패하면 화면에 보여 줄 한국어 문장을 담은 Error 를 던진다. */
async function getJson(path) {
  let response;
  try {
    response = await fetch(path, { credentials: "same-origin", headers: { Accept: "application/json" } });
  } catch (e) {
    throw new Error("서버에 연결할 수 없습니다. 콘솔 창에서 web 모드가 실행 중인지 확인하세요.");
  }
  if (response.status === 401) {
    throw new Error(SESSION_EXPIRED);
  }
  let data = null;
  try {
    data = await response.json();
  } catch (e) {
    data = null;
  }
  if (!response.ok) {
    if (data && data.error && typeof data.error.message === "string") {
      throw new Error(data.error.message);
    }
    throw new Error("조회하지 못했습니다 (HTTP " + response.status + ").");
  }
  return data;
}

/** "1234567.50" → "1,234,567.50". 숫자로 바꾸지 않고 문자열로만 다뤄 소수 오차가 없다. */
function formatDecimal(value) {
  if (typeof value !== "string" || value === "") {
    return "-";
  }
  const negative = value.startsWith("-");
  const body = negative ? value.slice(1) : value;
  const [integer, fraction] = body.split(".");
  const grouped = integer.replace(/\B(?=(\d{3})+(?!\d))/g, ",");
  return (negative ? "-" : "") + grouped + (fraction === undefined ? "" : "." + fraction);
}

function formatTime(value) {
  if (typeof value !== "string" || value === "") {
    return "-";
  }
  return value.replace("T", " ").replace(/(\.\d+)?([+-]\d{2}:\d{2}|Z)$/, "");
}

function clearChildren(el) {
  while (el.firstChild) {
    el.removeChild(el.firstChild);
  }
}

function fillTable(tableId, rows) {
  const table = document.getElementById(tableId);
  const body = table.querySelector("tbody");
  clearChildren(body);
  for (const cells of rows) {
    const tr = document.createElement("tr");
    for (const cell of cells) {
      const td = document.createElement("td");
      td.textContent = cell.text;
      if (cell.num) {
        td.className = "num";
      }
      tr.appendChild(td);
    }
    body.appendChild(tr);
  }
  table.hidden = rows.length === 0;
}

async function searchStocks(event) {
  event.preventDefault();
  const query = document.getElementById("search-query").value.trim();
  const market = document.getElementById("search-market").value;
  const list = document.getElementById("search-results");
  clearChildren(list);
  if (query === "") {
    setText("search-message", "검색어를 입력하세요.");
    return;
  }
  setText("search-message", "검색 중… (처음에는 종목 목록을 받느라 몇 초 걸릴 수 있습니다)");
  const params = new URLSearchParams({ query: query });
  if (market !== "") {
    params.set("market", market);
  }
  try {
    const data = await getJson("/api/stocks/search?" + params.toString());
    if (data.results.length === 0) {
      setText("search-message", "검색 결과가 없습니다.");
      return;
    }
    setText("search-message", "종목을 누르면 현재가를 조회합니다.");
    for (const item of data.results) {
      const li = document.createElement("li");
      const button = document.createElement("button");
      button.type = "button";
      button.className = "link";
      button.textContent = item.symbol + " " + item.name + " (" + item.market + (item.commonShare ? "" : ", 우선주") + ")";
      button.addEventListener("click", () => {
        document.getElementById("quote-symbol").value = item.symbol;
        loadQuote();
      });
      li.appendChild(button);
      list.appendChild(li);
    }
  } catch (e) {
    setText("search-message", e.message);
  }
}

async function loadQuote(event) {
  if (event) {
    event.preventDefault();
  }
  const symbol = document.getElementById("quote-symbol").value.trim();
  if (symbol === "") {
    setText("quote-message", "종목 코드를 입력하세요.");
    fillTable("quote-table", []);
    return;
  }
  setText("quote-message", "조회 중…");
  try {
    const data = await getJson("/api/quote?" + new URLSearchParams({ symbol: symbol }).toString());
    fillTable(
      "quote-table",
      data.quotes.map((q) => [
        { text: q.symbol },
        { text: formatDecimal(q.lastPrice), num: true },
        { text: q.currency },
        { text: formatTime(q.timestamp) },
      ])
    );
    setText("quote-message", data.quotes.length === 0 ? "시세가 없습니다." : "");
  } catch (e) {
    fillTable("quote-table", []);
    setText("quote-message", e.message);
  }
}

async function loadBuyingPower(currency) {
  setText("bp-result", "조회 중…");
  try {
    const data = await getJson("/api/buying-power?" + new URLSearchParams({ currency: currency }).toString());
    setText("bp-result", formatDecimal(data.cash) + " " + data.currency);
  } catch (e) {
    setText("bp-result", e.message);
  }
}

/**
 * 소수 비율 문자열 → 백분율 문자열, 소수 2자리 반올림(HALF_UP). "0.20505" → "20.51%". BigInt 로만 계산해 부동소수점을 쓰지 않는다.
 * signed 면 양수에 "+" 를 붙인다.
 */
function formatRatio(value, signed) {
  if (typeof value !== "string" || !/^-?\d+(\.\d+)?$/.test(value)) {
    return "-";
  }
  const negative = value.startsWith("-");
  const [integer, fraction = ""] = (negative ? value.slice(1) : value).split(".");
  const scaled = BigInt(integer + fraction); // 값 × 10^fraction.length
  const scale = 10n ** BigInt(fraction.length);
  const hundredths = (scaled * 10000n * 2n + scale) / (scale * 2n); // 백분율 × 100, 반올림
  const whole = hundredths / 100n;
  const cents = (hundredths % 100n).toString().padStart(2, "0");
  const zero = hundredths === 0n;
  const sign = negative && !zero ? "-" : signed && !negative && !zero ? "+" : "";
  return sign + whole.toString() + "." + cents + "%";
}

function formatSigned(value) {
  const text = formatDecimal(value);
  return typeof value === "string" && /^\d/.test(value) && /[1-9]/.test(value) ? "+" + text : text;
}

async function loadPortfolio() {
  setText("portfolio-summary", "조회 중…");
  setText("portfolio-note", "");
  try {
    const p = await getJson("/api/portfolio");
    if (p.holdings.length === 0) {
      setText("portfolio-summary", "보유 종목이 없습니다.");
      fillTable("portfolio-table", []);
      return;
    }
    if (p.totalValueKrw !== null && p.totalValueKrw !== undefined) {
      setText(
        "portfolio-summary",
        "평가 " + formatDecimal(p.totalValueKrw) + " KRW · 손익 " + formatSigned(p.totalProfitKrw) + " KRW ("
          + formatRatio(p.profitRate, true) + ")"
      );
    } else {
      setText(
        "portfolio-summary",
        "평가 " + formatDecimal(p.krw.value) + " KRW + " + formatDecimal(p.usd.value) + " USD (" + formatRatio(p.profitRate, true) + ")"
      );
    }
    let note = "오늘 " + formatRatio(p.dailyProfitRate, true) + ".";
    if (p.usd) {
      note += p.combinedUnavailable
        ? " 환율을 받지 못해 원화 합산·비중은 생략했습니다."
        : " 해외 종목은 표준 환율 1 USD = " + formatDecimal(p.usdKrwRate) + " KRW 로 원화 합산했습니다(실제 거래 환율과 다를 수 있음).";
    }
    setText("portfolio-note", note);
    fillTable(
      "portfolio-table",
      p.holdings.map((h) => [
        { text: h.symbol + " " + h.name },
        { text: formatDecimal(h.quantity), num: true },
        { text: formatDecimal(h.value) + " " + h.currency, num: true },
        { text: formatSigned(h.profit) + " " + h.currency, num: true },
        { text: formatRatio(h.profitRate, true), num: true },
        { text: formatRatio(h.weight, false), num: true },
      ])
    );
  } catch (e) {
    fillTable("portfolio-table", []);
    setText("portfolio-summary", e.message);
  }
}

const SIDE = { BUY: "매수", SELL: "매도" };

async function loadOrders(status) {
  setText("orders-message", "조회 중…");
  try {
    const data = await getJson("/api/orders?" + new URLSearchParams({ status: status }).toString());
    fillTable(
      "orders-table",
      data.orders.map((o) => [
        { text: o.symbol },
        { text: SIDE[o.side] || o.side },
        { text: o.orderType },
        { text: formatDecimal(o.quantity), num: true },
        { text: formatDecimal(o.price), num: true },
        { text: o.currency },
        { text: o.status },
        { text: formatTime(o.orderedAt) },
      ])
    );
    const label = status === "OPEN" ? "미체결" : "완료";
    setText("orders-message", data.orders.length === 0 ? label + " 주문이 없습니다." : label + " 주문 " + data.orders.length + "건");
  } catch (e) {
    fillTable("orders-table", []);
    setText("orders-message", e.message);
  }
}

document.addEventListener("DOMContentLoaded", () => {
  document.getElementById("key-form").addEventListener("submit", saveKeys);
  document.getElementById("search-form").addEventListener("submit", searchStocks);
  document.getElementById("quote-form").addEventListener("submit", loadQuote);
  document.getElementById("portfolio-load").addEventListener("click", loadPortfolio);
  for (const button of document.querySelectorAll("[data-currency]")) {
    button.addEventListener("click", () => loadBuyingPower(button.dataset.currency));
  }
  for (const button of document.querySelectorAll("[data-order-status]")) {
    button.addEventListener("click", () => loadOrders(button.dataset.orderStatus));
  }
  loadStatus();
});
