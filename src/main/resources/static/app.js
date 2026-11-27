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

document.addEventListener("DOMContentLoaded", () => {
  document.getElementById("key-form").addEventListener("submit", saveKeys);
  loadStatus();
});
