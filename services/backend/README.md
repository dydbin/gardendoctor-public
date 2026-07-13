# GardenDoctor Backend

Spring Boot 3.3, Java 17, MySQL 8.4, Redis 기반 API 서비스입니다. 이 디렉터리는 별도 Backend 저장소 commit `88aad81`의 공개 안전 snapshot이며 기존 Git history와 로컬 비밀 파일은 포함하지 않습니다.

## Verification

모노레포 root에서 실행합니다.

```bash
make backend-check
make public-check
```

Backend 디렉터리에서 직접 실행할 수도 있습니다.

```bash
GRADLE_USER_HOME=/tmp/gd-gradle ./gradlew test portfolioDiagnostics --no-daemon
```

## Local runtime

전체 로컬 stack은 `infra/compose.yaml`이 MySQL, Redis, AI와 함께 실행합니다.

```bash
cd ../..
cp infra/.env.example infra/.env
make backend-up
make backend-smoke
```

Backend runtime 설정의 단일 원본은 `infra/config/backend/application.properties`입니다. Gradle sourceSet이 이 파일을 직접 읽으므로 서비스 디렉터리에 `application.properties`를 복사하지 않습니다. Host에서 실행할 때도 infra 명령을 사용합니다.

```bash
make backend-run
```

실제 OAuth, AWS, Kakao, Firebase 값은 `infra/.env` 또는 secret manager에만 둡니다. Backend 하위에는 별도 `.env`, application runtime 설정, Dockerfile, Compose, 운영 스크립트를 만들지 않습니다. IntelliJ Run Configuration은 `infra/.env`와 동일한 환경변수 계약을 사용해야 합니다.

관측성과 부하 테스트 설정·스크립트도 모두 `infra/`에 있으며 `infra/compose.yaml`의 profile을 사용합니다.

```bash
make observability-up
make loadtest-smoke
```

기본 Compose는 Firebase와 FCM worker를 비활성화합니다. 실제 FCM을 확인할 때만 저장소 밖 service-account JSON 경로를 `infra/.env`의 `FIREBASE_SERVICE_ACCOUNT_HOST_PATH`에 지정하고 `make stack-up-firebase`를 사용합니다.

## Data policy

- 로컬 Compose의 `ddl-auto=update`는 개발 편의를 위한 값이며 운영 migration 전략이 아닙니다.
- Firebase와 외부 API worker는 공개 기본 환경에서 비활성화합니다.
- 별도 Backend 저장소에서 ignored 처리하던 `docs/`, `.env`, application runtime properties, Firebase service account는 이 snapshot에 포함하지 않았습니다.
