# SKALA 자동 출결 인증 프로그램 (macOS 전용)

macOS 환경에서 Google Chrome 및 Playwright를 활용하여 지정된 시간에 모바일 뷰로 SKALA 출결 인증 사이트에 접속하고, 폼 작성 및 Google 계정 선택까지 자동으로 완료하는 스크립트입니다.

---

## 사전 요구 사항

* macOS 환경
* Google Chrome 브라우저 설치
* Python 3.10 이상

---

⚠️ 이 스크립트가 여는 크롬 창은 chrome_cdp_profile/ 전용 프로필입니다. 기존 아이디가 로그인 되어 있더라도 평소 쓰는 크롬과 완전히 분리되어 있어 북마크·비밀번호가 없는 게 정상이며, 기존 크롬 데이터에는 전혀 영향을 주지 않습니다.
-> 이 현상 발생 시, dock에 있는 모든 chrome을 종료한 뒤 다시 실행해주세요.

## 1. 초기 환경 구축

### 1) 프로젝트 디렉토리 생성 및 이동

터미널을 열고 프로젝트를 둘 폴더를 생성한 뒤 이동합니다.

```bash
git clone https://github.com/ganglike248/skala-auto-att.git
cd ~/workspace/skala-auto-att

```

### 2) 필수 라이브러리 설치

```bash
pip3 install -r requirements.txt
playwright install chromium

```

---

## 2. 환경 변수 설정 (`.env`)

저장소에 포함된 `.env.example`을 복사하여 `.env` 파일을 만들고 본인 정보로 채웁니다.

```bash
cp .env.example .env

```

`.env` 파일 내용:

```ini
USER_NAME=본인이름
TARGET_EMAIL=your_email@gmail.com
```

> **주의**: `.env` 파일은 개인정보를 담고 있으므로 `.gitignore`에 의해 커밋에서 제외됩니다.

---

## 3. 스크립트 (`auto_att.py`)

저장소의 `auto_att.py`가 `.env` 값을 읽어 동작합니다.

---

## 4. 최초 1회 Google 계정 인증 세션 등록

Google의 자동화 봇 감지 차단을 방지하기 위해, 전용 프로필 디렉토리에 **최초 1회 수동 로그인**을 진행해야 합니다.

1. 열려 있는 크롬 창을 모두 종료합니다 (`Cmd + Q`).
2. 터미널에서 전용 프로필로 크롬을 실행합니다:
```bash
"/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" --user-data-dir="$HOME/workspace/skala-auto-att/chrome_cdp_profile"

```


3. 열린 창에서 [Google 로그인 페이지](https://accounts.google.com)로 이동하여 대상 구글 계정(`TARGET_EMAIL`)으로 로그인을 완료합니다.
4. 로그인이 완료되면 해당 크롬 창을 닫습니다. (세션이 `chrome_cdp_profile` 폴더에 영구 보관됩니다.)

---

## 5. 수동 동작 테스트

터미널에서 직접 실행하여 정상 작동하는지 확인합니다.

```bash
python3 ~/workspace/skala-auto-att/auto_att.py

```

---

## 6. macOS 자동 실행 스케줄러 등록 (`launchd`)

**평일(월~금) 17:49 (오후 5시 49분)** 정각에 자동 실행되도록 macOS 표준 백그라운드 서비스로 등록합니다.
시간 설정은 아래 서비스 등록 스크립트에서 수정합니다.

> **주말·공휴일 처리**: 아래 plist는 `Weekday` 키로 월~금에만 트리거됩니다. 대한민국 공휴일(대체공휴일 포함)은
> `auto_att.py`가 `holidays` 라이브러리로 직접 판별하여, 공휴일이면 브라우저를 띄우지 않고 즉시 종료합니다.
> (`launchctl kickstart` 같은 수동 실행에도 동일하게 적용됩니다.)

### 1) 파이썬 실행 바이너리 경로 확인

```bash
python3 -c 'import sys; print(sys.executable)'

```

*(예: `/opt/homebrew/opt/python@3.11/bin/python3.11`)*

> **주의**: `which python3`는 사용하지 마세요. zsh에서 `python3`가 alias로 등록돼 있으면
> 경로가 아니라 `python3: aliased to ...` 같은 문자열이 반환되어 plist에 잘못 들어가고,
> launchd가 실행에 실패(`EX_CONFIG`, exit 78)하면서 `launchctl start`가 아무 반응 없이 끝납니다.

### 2) 서비스 등록 스크립트 실행

아래 명령어를 그대로 복사하여 터미널에서 실행합니다. `PYTHON_BIN`은 1번 방식으로 자동 확인됩니다.

```bash
PYTHON_BIN=$(python3 -c 'import sys; print(sys.executable)')

cat << EOF > ~/Library/LaunchAgents/com.skala.auto-att.plist
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>com.skala.auto-att</string>
    <key>WorkingDirectory</key>
    <string>$HOME/workspace/skala-auto-att</string>
    <key>ProgramArguments</key>
    <array>
        <string>$PYTHON_BIN</string>
        <string>-u</string>
        <string>$HOME/workspace/skala-auto-att/auto_att.py</string>
    </array>
    <key>EnvironmentVariables</key>
    <dict>
        <key>PYTHONUNBUFFERED</key>
        <string>1</string>
    </dict>
    <key>StartCalendarInterval</key>
    <array>
        <dict>
            <key>Weekday</key><integer>1</integer>
            <key>Hour</key><integer>17</integer>
            <key>Minute</key><integer>49</integer>
        </dict>
        <dict>
            <key>Weekday</key><integer>2</integer>
            <key>Hour</key><integer>17</integer>
            <key>Minute</key><integer>49</integer>
        </dict>
        <dict>
            <key>Weekday</key><integer>3</integer>
            <key>Hour</key><integer>17</integer>
            <key>Minute</key><integer>49</integer>
        </dict>
        <dict>
            <key>Weekday</key><integer>4</integer>
            <key>Hour</key><integer>17</integer>
            <key>Minute</key><integer>49</integer>
        </dict>
        <dict>
            <key>Weekday</key><integer>5</integer>
            <key>Hour</key><integer>17</integer>
            <key>Minute</key><integer>49</integer>
        </dict>
    </array>
    <key>StandardOutPath</key>
    <string>$HOME/workspace/skala-auto-att/auto_run.log</string>
    <key>StandardErrorPath</key>
    <string>$HOME/workspace/skala-auto-att/auto_run.log</string>
</dict>
</plist>
EOF

launchctl bootout gui/$(id -u)/com.skala.auto-att 2>/dev/null
launchctl bootstrap gui/$(id -u) ~/Library/LaunchAgents/com.skala.auto-att.plist

```

> `-u` 옵션과 `PYTHONUNBUFFERED=1`은 `print()` 출력이 버퍼링되지 않고 즉시
> `auto_run.log`에 기록되도록 합니다. (없으면 실행이 끝날 때까지 로그가 비어 보입니다.)
>
> 구형 `launchctl unload/load` 대신 최신 macOS 표준인 `bootout/bootstrap`을 사용합니다.

### 3) 등록 즉시 수동 트리거 테스트

스케줄 시간과 무관하게 백그라운드 서비스를 즉시 1회 실행하여 연동 상태를 확인합니다.

```bash
launchctl kickstart -k gui/$(id -u)/com.skala.auto-att

```

실행 결과와 종료 코드는 다음으로 확인합니다.

```bash
launchctl print gui/$(id -u)/com.skala.auto-att | grep -E "state =|last exit code"
cat ~/workspace/skala-auto-att/auto_run.log

```

---

## 7. 유지 관리 및 제어 가이드

### 실행 로그 모니터링

```bash
tail -f ~/workspace/skala-auto-att/auto_run.log

```

### 실행 시간 변경

`~/Library/LaunchAgents/com.skala.auto-att.plist` 파일의 시간 값을 수정한 후 서비스를 재적용합니다.

```bash
# 1. 파일 열기
nano ~/Library/LaunchAgents/com.skala.auto-att.plist

# 2. 5개 <dict> 각각의 <key>Hour</key>, <key>Minute</key> 값 수정 후 Ctrl+O(저장), Ctrl+X(종료)
#    (요일을 늘리거나 줄이려면 <dict> 블록을 추가/삭제하고 Weekday 값을 조정 — 일=0, 월=1 … 금=5, 토=6)

# 3. 서비스 재적용
launchctl bootout gui/$(id -u)/com.skala.auto-att
launchctl bootstrap gui/$(id -u) ~/Library/LaunchAgents/com.skala.auto-att.plist

```

### 자동 실행 일시 중단

```bash
launchctl bootout gui/$(id -u)/com.skala.auto-att

```

*(다시 활성화하려면 `launchctl bootstrap gui/$(id -u) ~/Library/LaunchAgents/com.skala.auto-att.plist` 실행)*

### 서비스 완전 삭제

```bash
launchctl bootout gui/$(id -u)/com.skala.auto-att
rm ~/Library/LaunchAgents/com.skala.auto-att.plist

```