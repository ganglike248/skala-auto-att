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

### 1) 저장소 클론

터미널을 열고 프로젝트를 받을 위치(예: 홈 폴더)로 이동한 뒤 저장소를 클론합니다.
경로는 자유이며, 이후 단계는 모두 클론된 `skala-auto-att` 폴더 안에서 실행하는 것을 기준으로 합니다.

```bash
git clone https://github.com/ganglike248/skala-auto-att.git
cd skala-auto-att

```

### 2) 필수 라이브러리 설치

```bash
python3 -m pip install -r requirements.txt
playwright install chromium

```

> `pip3` 대신 `python3 -m pip`을 사용합니다. 6번의 `launchd`는 이 `python3`와 동일한 인터프리터로
> 스크립트를 실행하므로, 설치와 실행의 파이썬이 일치해야 `holidays` 등 모듈을 찾을 수 있습니다.

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
REGION_NAME=울산캠퍼스
CLASS_NAME=4반
```

* `REGION_NAME` / `CLASS_NAME`: 출결 사이트의 **지역·반 드롭다운에 보이는 텍스트를 공백까지 그대로** 적습니다.
  본인 캠퍼스/반에 맞게 반드시 수정하세요.
  * `REGION_NAME` 예시 (2026년 기준): `판교캠퍼스4F`, `판교캠퍼스5F`, `광주캠퍼스`, `울산캠퍼스`
  * `CLASS_NAME` 예시: `1반`, `2반`, `3반`, `4반` (지역마다 목록이 다르며, 지역 선택 후 로드됩니다)
* 네 값(`USER_NAME`, `TARGET_EMAIL`, `REGION_NAME`, `CLASS_NAME`) 중 하나라도 비어 있으면
  스크립트가 실행 즉시 오류 메시지를 출력하고 종료합니다.

> **주의**: `.env` 파일은 개인정보를 담고 있으므로 `.gitignore`에 의해 커밋에서 제외됩니다.

---

## 3. 스크립트 (`auto_att.py`)

저장소의 `auto_att.py`가 `.env` 값을 읽어 동작합니다. 캠퍼스·반·이름·계정이 모두 `.env`로
분리돼 있으므로 **코드는 수정할 필요가 없습니다.** (실행 요일은 평일, 공휴일은 자동 제외)

---

## 4. 최초 1회 Google 계정 인증 세션 등록

Google의 자동화 봇 감지 차단을 방지하기 위해, 전용 프로필 디렉토리에 **최초 1회 수동 로그인**을 진행해야 합니다.

1. 열려 있는 크롬 창을 모두 종료합니다 (`Cmd + Q`).
2. 터미널에서 **프로젝트 폴더로 이동한 뒤** 전용 프로필로 크롬을 실행합니다:
```bash
cd skala-auto-att   # git clone 한 위치
"/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" --user-data-dir="$PWD/chrome_cdp_profile"

```


3. 열린 창에서 [Google 로그인 페이지](https://accounts.google.com)로 이동하여 대상 구글 계정(`TARGET_EMAIL`)으로 로그인을 완료합니다.
4. 로그인이 완료되면 해당 크롬 창을 닫습니다. (세션이 `chrome_cdp_profile` 폴더에 영구 보관됩니다.)

---

## 5. 수동 동작 테스트

프로젝트 폴더 안에서 직접 실행하여 정상 작동하는지 확인합니다.

```bash
python3 auto_att.py

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

**프로젝트 폴더 안에서** 아래 명령어를 그대로 복사하여 터미널에서 실행합니다.
`PYTHON_BIN`은 1번 방식으로, `PROJECT_DIR`은 현재 폴더 경로로 자동 확인됩니다.

```bash
PYTHON_BIN=$(python3 -c 'import sys; print(sys.executable)')
PROJECT_DIR=$(pwd)

cat << EOF > ~/Library/LaunchAgents/com.skala.auto-att.plist
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>com.skala.auto-att</string>
    <key>WorkingDirectory</key>
    <string>$PROJECT_DIR</string>
    <key>ProgramArguments</key>
    <array>
        <string>$PYTHON_BIN</string>
        <string>-u</string>
        <string>$PROJECT_DIR/auto_att.py</string>
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
    <string>$PROJECT_DIR/auto_run.log</string>
    <key>StandardErrorPath</key>
    <string>$PROJECT_DIR/auto_run.log</string>
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
cat auto_run.log   # 프로젝트 폴더 안에서

```

---

## 7. 유지 관리 및 제어 가이드

### 실행 로그 모니터링

```bash
tail -f auto_run.log   # 프로젝트 폴더 안에서 (또는 등록 시 사용한 절대 경로)

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