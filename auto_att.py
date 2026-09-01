import os
import subprocess
import sys
import time
from datetime import date

import holidays
from playwright.sync_api import sync_playwright
from dotenv import load_dotenv

load_dotenv()

# 대한민국 공휴일 (연도는 실행 시점에 맞춰 자동 확장)
KR_HOLIDAYS = holidays.SouthKorea()


def should_run_today(today=None):
    """평일(월~금)이면서 공휴일이 아닐 때만 True."""
    today = today or date.today()
    if today.weekday() >= 5:  # 5=토, 6=일
        return False, "주말"
    if today in KR_HOLIDAYS:
        return False, f"공휴일({KR_HOLIDAYS.get(today)})"
    return True, ""

# ================= 사용자 설정 =================
USER_NAME = os.getenv("USER_NAME") # .env 파일에 설정된 사용자 이름
TARGET_EMAIL = os.getenv("TARGET_EMAIL")
# ===============================================

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
        "about:blank"
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

def run_test():
    ok, reason = should_run_today()
    if not ok:
        print(f"[스킵] 오늘은 {reason}이므로 출결 인증을 실행하지 않습니다.")
        sys.exit(0)

    chrome_proc = open_real_chrome()
    time.sleep(2)

    with sync_playwright() as p:
        browser = p.chromium.connect_over_cdp(f"http://127.0.0.1:{REMOTE_PORT}")
        context = browser.contexts[0]
        
        context.on("page", lambda new_p: setup_mobile_emulation(new_p, context))
        page = context.pages[0] if context.pages else context.new_page()
        setup_mobile_emulation(page, context)

        print("[1] 모바일 환경으로 사이트 접속...")
        page.goto(TARGET_URL)
        page.reload(wait_until="networkidle")

        # 1. 이름 입력
        print(f"[2] 이름 '{USER_NAME}' 입력")
        name_input = page.locator("input.auth-input[placeholder='훈련생 이름 입력']")
        name_input.fill(USER_NAME)

        # 2. 지역 선택: 울산캠퍼스
        print("[3] 지역: '울산캠퍼스' 선택")
        region_select = page.locator(".auth-field").filter(has_text="지역").locator("select")
        region_select.select_option(value="US")

        # 3. 반 (클래스) 선택: 4반 (5번째 항목)
        print("[4] 반 목록 로딩 대기...")
        class_select = page.locator(".auth-field").filter(has_text="반 (클래스)").locator("select")
        page.wait_for_function("() => !document.querySelectorAll('.auth-select')[1].disabled")
        page.wait_for_function("() => document.querySelectorAll('.auth-select')[1].options.length >= 5")

        class_options = class_select.locator("option")
        target_val = class_options.nth(4).get_attribute("value")
        class_select.select_option(value=target_val)
        print(f"[4-1] 반 선택 완료 (선택 값: {target_val})")

        # 4. 다음 버튼 클릭
        print("[5] '다음' 버튼 클릭")
        next_button = page.locator("button.auth-btn")
        page.wait_for_function("() => !document.querySelector('button.auth-btn').disabled")
        next_button.click()

        # 5. 구글 계정 화면 처리
        print("[6] 구글 계정 선택 대기...")
        try:
            account_btn = page.locator(f"div[data-email='{TARGET_EMAIL}'], :text('{TARGET_EMAIL}')").first
            account_btn.wait_for(state="visible", timeout=7000)
            account_btn.click()
            print(f"[6-1] '{TARGET_EMAIL}' 계정 선택 완료")
        except Exception:
            print("[안내] 계정 선택 UI를 자동으로 찾지 못했습니다.")

        # 6. 최종 화면 리다이렉트 후 모바일 갱신
        print("[7] 인증 후 최종 화면 로딩 대기 및 모바일 갱신...")
        time.sleep(3)
        current_page = context.pages[-1]
        setup_mobile_emulation(current_page, context)
        current_page.reload(wait_until="networkidle")

        print("[완료] 동작이 정상 종료되었습니다.")

    # # 크롬 및 자식 프로세스 그룹 정리
    # try:
    #     os.killpg(os.getpgid(chrome_proc.pid), signal.SIGKILL)
    # except Exception:
    #     pass

    sys.exit(0)

if __name__ == "__main__":
    run_test()