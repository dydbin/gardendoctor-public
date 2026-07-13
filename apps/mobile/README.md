# GardenDoctor Mobile

Flutter 3.32.0 기반 Android/iOS client입니다. Mobile은 Docker Compose의 장기 실행 서비스가 아니며 기기 또는 emulator에서 실행합니다.

## Local configuration

`infra/.env`가 유일한 설정 원본입니다. 모노레포 root에서 다음 명령을 실행하면 Mobile에 허용된 build-time key만 ignored `infra/generated/mobile/app.local.json`으로 변환됩니다.

```bash
cp infra/.env.example infra/.env
make app-config
make app-run
```

변환 허용 목록은 `infra/scripts/render-mobile-config.py`에 고정되어 있습니다. `infra/scripts/mobile/flutter.sh`가 Android manifest에 필요한 Kakao key 한 개만 Gradle 프로세스 환경으로 전달합니다. Backend DB, JWT, OAuth, AWS secret은 앱 설정에 포함되지 않습니다. Android debug build는 local HTTP emulator 연결을 허용하지만 release build는 HTTPS API를 사용해야 합니다.

## Verification

```bash
make app-get
make app-generate
make app-check
make app-build
```

CI의 공개 debug APK는 `infra/config/mobile/public.json`을 사용합니다.
