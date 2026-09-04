# SKALA 자동 출결 (Android)

앱을 열면 SKALA 출결 사이트에 접속해서 이름·지역·반을 자동으로 입력하고 "다음" 버튼까지 자동으로 누릅니다.
그 다음 구글 로그인 화면은 (구글이 앱 내장 브라우저에서의 로그인을 막기 때문에) Chrome 커스텀 탭으로 넘어가고,
계정 선택과 마지막 출석 체크 버튼은 사람이 직접 누릅니다.

정해진 시간에 저절로 켜지는 자동 실행은 아니고, **앱을 여는 순간부터** 동작합니다.

[skala-auto-att](https://github.com/ganglike248/skala-auto-att) (macOS/launchd 버전)의 안드로이드용 자매 프로젝트입니다.

---

## 빌드

```bash
./gradlew assembleDebug
```

결과물: `app/build/outputs/apk/debug/app-debug.apk`

## 설치 (사이드로드)

스토어에 올리지 않고 APK 파일을 폰에 직접 옮겨서 설치합니다.

```bash
# 폰이 USB로 연결되어 있고 개발자 옵션 > USB 디버깅이 켜져 있을 때
adb install app/build/outputs/apk/debug/app-debug.apk
```

또는 APK 파일을 카카오톡/이메일/드라이브 등으로 폰에 옮긴 뒤 파일을 열어 설치해도 됩니다.
(첫 설치 시 "출처를 알 수 없는 앱" 허용이 필요할 수 있습니다.)

## 설정

앱을 처음 열면 설정 화면이 뜹니다. 맥 버전의 `.env`에 해당하는 값을 입력합니다.

| 항목 | 설명 |
|---|---|
| 이름 | 훈련생 이름 |
| 구글 이메일 | 출결 인증에 쓸 구글 계정 |
| 지역 | 드롭다운에 보이는 텍스트 그대로 (예: `울산캠퍼스`) |
| 반 | 드롭다운에 보이는 텍스트 그대로 (예: `4반`) |

우측 상단 톱니바퀴 아이콘으로 언제든 다시 수정할 수 있습니다.

## 구조

- `MainActivity.kt` — WebView로 SKALA 사이트 접속, 자동입력 스크립트 주입, 구글 로그인은 커스텀 탭으로 위임
- `AutofillScript.kt` — `auto_att.py`의 Playwright 로직을 그대로 옮긴 JavaScript (이름 입력 → 지역 선택 → 반 선택 → "다음" 클릭)
- `SettingsActivity.kt` / `Prefs.kt` — 설정값 입력 화면과 저장소 (SharedPreferences)
