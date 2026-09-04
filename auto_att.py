import os
import subprocess
import sys
import time
from datetime import date, datetime

import holidays
from playwright.sync_api import sync_playwright
from dotenv import load_dotenv

load_dotenv()

# 대한민국 공휴일 (연도는 실행 시점에 맞춰 자동 확장)
KR_HOLIDAYS = holidays.SouthKorea()


def log(msg):
    """auto_run.log에서 언제 실행된 로그인지 알 수 있도록 시간을 붙여 출력."""
    print(f"[{datetime.now():%Y-%m-%d %H:%M:%S}] {msg}")


def should_run_today(today=None):
    """평일(월~금)이면서 공휴일이 아닐 때만 True."""
    today = today or date.today()
    if today.weekday() >= 5:  # 5=토, 6=일
        return False, "주말"
    if today in KR_HOLIDAYS:
        return False, f"공휴일({KR_HOLIDAYS.get(today)})"
    return True, ""

# ================= 사용자 설정 (.env) =================
USER_NAME = os.getenv("USER_NAME")          # 훈련생 이름
TARGET_EMAIL = os.getenv("TARGET_EMAIL")    # 인증에 사용할 구글 계정
REGION_NAME = os.getenv("REGION_NAME")      # 지역 드롭다운에 보이는 이름 (예: 울산캠퍼스)
CLASS_NAME = os.getenv("CLASS_NAME")        # 반 드롭다운에 보이는 이름 (예: 4반)

_missing = [k for k, v in {
    "USER_NAME": USER_NAME,
    "TARGET_EMAIL": TARGET_EMAIL,
    "REGION_NAME": REGION_NAME,
    "CLASS_NAME": CLASS_NAME,
}.items() if not v]
if _missing:
    print(f"[오류] .env에 다음 값이 필요합니다: {', '.join(_missing)}")
    sys.exit(1)
# =====================================================

TARGET_URL = "https://auth.skala-ai.com/"
CHROME_PATH = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
REMOTE_PORT = 9222
CDP_PROFILE_DIR = os.path.join(os.getcwd(), "chrome_cdp_profile")

MOBILE_USER_AGENT = "Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1"

def open_real_chrome():
    """GCM 및 백그라운드 네트워크/알림 엔진을 차단하여 실행"""
    cmd = [
        CHROME_PATH,
        f"--remote-debugging-port={REMOTE_PORT}",
        f"--user-data-dir={CDP_PROFILE_DIR}",
        "--window-size=430,932",
        "--no-first-run",
        "--no-default-browser-check",
        "--disable-gcm",
        "--disable-notifications",
        "--disable-extensions",
        "--disable-component-update",
        "--disable-background-networking",
        "--disable-sync",
        "--disable-features=OptimizationGuideModelDownloading,MediaRouter,PushMessaging",
        "--log-level=3",
        TARGET_URL
    ]
    devnull = open(os.devnull, "wb")
    return subprocess.Popen(
        cmd,
        stdout=devnull,
        stderr=devnull,
        stdin=devnull,
        start_new_session=True
    )

def setup_mobile_emulation(page, context):
    """모바일 환경 강제 주입"""
    page.add_init_script("""
        Object.defineProperty(navigator, 'userAgent', { get: () => 'Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1' });
        Object.defineProperty(navigator, 'maxTouchPoints', { get: () => 5 });
        Object.defineProperty(window, 'ontouchstart', { get: () => () => {} });
    """)
    cdp = context.new_cdp_session(page)
    cdp.send("Network.setUserAgentOverride", {
        "userAgent": MOBILE_USER_AGENT,
        "platform": "iPhone"
    })
    cdp.send("Emulation.setDeviceMetricsOverride", {
        "width": 393,
        "height": 852,
        "deviceScaleFactor": 3,
        "mobile": True,
        "screenOrientation": {"type": "portraitPrimary", "angle": 0}
    })
    cdp.send("Emulation.setTouchEmulationEnabled", {
        "enabled": True,
        "maxTouchPoints": 5
    })

def connect_with_retry(p, attempts=10, interval=1):
    """크롬이 디버깅 포트를 여는 데 시간이 걸릴 수 있으므로 즉시 실패시키지 않고 재시도."""
    last_err = None
    for _ in range(attempts):
        time.sleep(interval)
        try:
            return p.chromium.connect_over_cdp(f"http://127.0.0.1:{REMOTE_PORT}")
        except Exception as e:
            last_err = e
    raise RuntimeError(f"크롬 디버깅 포트(9222) 연결 실패 ({attempts}초 재시도 후 포기): {last_err}")


def get_browser_and_page(p):
    """전용 프로필 크롬이 이미 떠 있으면 그대로 재사용해 새 탭만 하나 열고,
    없으면 새로 띄웁니다. 어느 경우든 기존에 열려 있던 다른 탭
    (예: 아직 출석 체크를 누르지 않은 이전 실행 결과)은 절대 건드리지 않습니다.
    또한 사용자의 평소 크롬(다른 프로필)과는 완전히 분리되어 있어 영향이 없습니다."""
    try:
        browser = p.chromium.connect_over_cdp(f"http://127.0.0.1:{REMOTE_PORT}")
        page = browser.contexts[0].new_page()
        page.goto(TARGET_URL)
        return browser, page
    except Exception:
        pass

    open_real_chrome()
    browser = connect_with_retry(p)
    context = browser.contexts[0]
    page = context.pages[0] if context.pages else context.new_page()
    return browser, page


def run_test():
    ok, reason = should_run_today()
    if not ok:
        log(f"[스킵] 오늘은 {reason}이므로 출결 인증을 실행하지 않습니다.")
        sys.exit(0)

    try:
        with sync_playwright() as p:
            browser, page = get_browser_and_page(p)
            context = browser.contexts[0]
            context.on("page", lambda new_p: setup_mobile_emulation(new_p, context))

            log("[1] 사이트 접속 후 모바일 환경으로 전환...")
            setup_mobile_emulation(page, context)
            page.reload(wait_until="networkidle")

            # 1. 이름 입력
            log(f"[2] 이름 '{USER_NAME}' 입력")
            name_input = page.locator("input.auth-input[placeholder='훈련생 이름 입력']")
            name_input.fill(USER_NAME)

            # 2. 지역 선택
            log(f"[3] 지역: '{REGION_NAME}' 선택")
            region_select = page.locator(".auth-field").filter(has_text="지역").locator("select")
            region_select.select_option(label=REGION_NAME)

            # 3. 반 (클래스) 선택
            log(f"[4] 반 목록 로딩 대기... (선택 대상: '{CLASS_NAME}')")
            class_select = page.locator(".auth-field").filter(has_text="반 (클래스)").locator("select")
            page.wait_for_function("() => !document.querySelectorAll('.auth-select')[1].disabled")
            page.wait_for_function(
                "name => [...document.querySelectorAll('.auth-select')[1].options].some(o => o.text.trim() === name)",
                arg=CLASS_NAME,
            )
            class_select.select_option(label=CLASS_NAME)
            log(f"[4-1] 반 선택 완료 ('{CLASS_NAME}')")

            # 4. 다음 버튼 클릭
            log("[5] '다음' 버튼 클릭")
            next_button = page.locator("button.auth-btn")
            page.wait_for_function("() => !document.querySelector('button.auth-btn').disabled")
            next_button.click()

            # 5. 구글 계정 화면 처리
            log("[6] 구글 계정 선택 대기...")
            try:
                account_btn = page.locator(f"div[data-email='{TARGET_EMAIL}'], :text('{TARGET_EMAIL}')").first
                account_btn.wait_for(state="visible", timeout=7000)
                account_btn.click()
                log(f"[6-1] '{TARGET_EMAIL}' 계정 선택 완료")
            except Exception:
                log("[안내] 계정 선택 UI를 자동으로 찾지 못했습니다.")

            # 6. 최종 화면 리다이렉트 후 모바일 갱신
            log("[7] 인증 후 최종 화면 로딩 대기 및 모바일 갱신...")
            time.sleep(3)
            current_page = context.pages[-1]
            setup_mobile_emulation(current_page, context)
            current_page.reload(wait_until="networkidle")

            log("[완료] 동작이 정상 종료되었습니다. 지정된 시간 이후 출석 체크 버튼을 직접 눌러주세요.")
    except Exception as e:
        log(f"[오류] 실행 중 문제가 발생하여 중단했습니다: {e}")

    # 창은 자동으로 닫지 않습니다. 이후 출석 체크는 사람이 직접 눌러야 하므로,
    # 성공하든 실패하든 화면은 그대로 남겨 둡니다.
    sys.exit(0)

if __name__ == "__main__":
    run_test()