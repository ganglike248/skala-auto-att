# SKALA 자동 출결 인증 프로그램 (macOS 전용)

지정된 시간에 Chrome을 자동 실행해 이름·지역·반 입력과 구글 계정 인증까지 자동으로 진행하는 스크립트입니다.

요구 사항: macOS · Google Chrome · Python 3.10+

---

## 설치 (최초 1회)

```bash
# 1. 클론
git clone https://github.com/ganglike248/skala-auto-att.git
cd skala-auto-att

# 2. 라이브러리 설치
python3 -m pip install -r requirements.txt
playwright install chromium

# 3. 환경 변수 설정
cp .env.example .env
```

`.env`를 열어 본인 정보로 채웁니다. (`REGION_NAME`·`CLASS_NAME`은 출결 사이트 드롭다운 텍스트 그대로)

```ini
USER_NAME=본인이름
TARGET_EMAIL=your_email@gmail.com
REGION_NAME=울산캠퍼스
CLASS_NAME=4반
```

```bash
# 4. Google 계정 최초 로그인 (봇 감지 방지용, 최초 1회만)
"/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" --user-data-dir="$PWD/chrome_cdp_profile"
```

열린 창에서 [accounts.google.com](https://accounts.google.com)으로 이동해 `TARGET_EMAIL` 계정으로 로그인 후 창을 닫습니다.
> 이 창은 전용 프로필이라 북마크·비밀번호가 비어 있는 게 정상입니다.

```bash
# 5. 수동 실행 테스트
python3 auto_att.py
```

```bash
# 6. 자동 실행 등록 (launchd, 평일 17:49)
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
        <dict><key>Weekday</key><integer>1</integer><key>Hour</key><integer>17</integer><key>Minute</key><integer>49</integer></dict>
        <dict><key>Weekday</key><integer>2</integer><key>Hour</key><integer>17</integer><key>Minute</key><integer>49</integer></dict>
        <dict><key>Weekday</key><integer>3</integer><key>Hour</key><integer>17</integer><key>Minute</key><integer>49</integer></dict>
        <dict><key>Weekday</key><integer>4</integer><key>Hour</key><integer>17</integer><key>Minute</key><integer>49</integer></dict>
        <dict><key>Weekday</key><integer>5</integer><key>Hour</key><integer>17</integer><key>Minute</key><integer>49</integer></dict>
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
launchctl kickstart -k gui/$(id -u)/com.skala.auto-att
```

여기까지 완료하면 끝입니다. 평일 17:49에 자동 실행되며, 공휴일에는 자동으로 건너뜁니다.
자동 입력이 끝나도 크롬 창은 닫히지 않습니다 — 출석 체크 버튼은 지정된 시간 이후 직접 눌러야합니다. 이 창은 평소 쓰는 크롬과 완전히 분리된 전용 프로필이라, 실행 시간에 다른 크롬 창을 쓰고 있어도 서로 영향이 없습니다.

---

## 데스크탑 바로가기 (선택)

더블클릭으로 수동 실행하고 싶다면:

```bash
osacompile -o ~/Desktop/"SKALA 출결 실행.app" <(echo "tell application \"Terminal\"
    activate
    do script \"cd '$(pwd)' && python3 auto_att.py\"
end tell")
```

---

## 문제 해결

| 상황 | 명령 |
| --- | --- |
| 로그 확인 | `tail -f auto_run.log` |
| 지금 바로 실행해보기 | `launchctl kickstart -k gui/$(id -u)/com.skala.auto-att` |
| 자동 실행 중단 | `launchctl bootout gui/$(id -u)/com.skala.auto-att` |
| 자동 실행 재개 | `launchctl bootstrap gui/$(id -u) ~/Library/LaunchAgents/com.skala.auto-att.plist` |
| 완전 삭제 | 위 중단 명령 + `rm ~/Library/LaunchAgents/com.skala.auto-att.plist` |
| 실행 시간 변경 | 아래 [실행 시간 변경](#실행-시간-변경) 참고 |
| 빈 화면에서 멈춤 / 가끔 실행 안 됨 | 아래 [빈 화면에서 멈추거나 가끔 실행이 안 되는 경우](#빈-화면에서-멈추거나-가끔-실행이-안-되는-경우) 참고 |

**자동 실행이 안 될 때**: `which python3` 결과를 쓰지 마세요 (zsh alias면 잘못된 값이 들어갑니다). `python3 -c 'import sys; print(sys.executable)'`로 확인한 실제 경로로 6단계를 다시 실행하세요.

### 실행 시간 변경

**1. 진입** — 터미널에서 아래 명령으로 등록 파일을 엽니다.

```bash
nano ~/Library/LaunchAgents/com.skala.auto-att.plist
```

**2. 수정** — 파일 안에 요일별로 5개 `<dict>` 블록이 있습니다 (Weekday 1=월 ~ 5=금). 각 블록의 `Hour`(0~23시)와 `Minute`(0~59분) 숫자를 원하는 시간으로 바꿉니다. 5개 블록 모두 같은 시간으로 맞춰야 매일 동일하게 동작합니다.

```xml
<dict><key>Weekday</key><integer>1</integer><key>Hour</key><integer>17</integer><key>Minute</key><integer>49</integer></dict>
```

위에서 `17`(시), `49`(분) 부분만 5개 블록 전부 동일하게 수정하면 됩니다. (예: 오후 6시 30분 → `Hour`를 `18`, `Minute`을 `30`)

**3. 저장하고 나가기** — nano 하단 안내를 따라 순서대로 입력합니다.

- `Ctrl + O` → 저장 (Write Out), 이어서 `Enter`로 파일명 확인
- `Ctrl + X` → nano 종료

**4. 적용** — 수정한 파일을 실제 서비스에 다시 등록합니다 (기존 스케줄을 내리고 새로 올림).

```bash
launchctl bootout gui/$(id -u)/com.skala.auto-att
launchctl bootstrap gui/$(id -u) ~/Library/LaunchAgents/com.skala.auto-att.plist
```

적용됐는지는 `launchctl print gui/$(id -u)/com.skala.auto-att`로 확인하거나, 바뀐 시간까지 기다렸다가 `auto_run.log`에 새 기록이 남는지 보면 됩니다.

### 빈 화면에서 멈추거나 가끔 실행이 안 되는 경우

**원인**: 크롬을 띄운 뒤 2초만 기다리고 바로 연결을 시도했는데, 시스템이 느릴 때(잠자기 직후 등) 2초 안에 크롬이 준비되지 않으면 연결에 실패해 스크립트가 그대로 죽고, 방금 뜬 빈 화면(`about:blank`)만 남는 경우가 있었습니다. 대부분 그 시간에 맥이 잠자기 상태였기 때문입니다 (화면 잠금은 괜찮지만, 맥 자체가 잠들면 `launchd` 스케줄이 씹힐 수 있습니다). 출결 시간 전후로는 맥을 깨워 두거나, 시스템 설정 > 잠금화면/배터리에서 자동 잠자기를 꺼두세요. `tail -f auto_run.log`로 그 날짜 기록 자체가 없는지, 있는데 `[완료]`가 안 찍혔는지 확인하면 어느 쪽 문제인지 구분할 수 있습니다.
