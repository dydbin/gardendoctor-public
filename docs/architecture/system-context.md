# System context

공개 모노레포는 모바일 앱, Backend API, AI 서비스의 코드와 로컬 검증 경계를 함께 관리합니다.

```text
Flutter mobile
    |
    v
Spring Boot Backend ----> MySQL
    |                    Redis
    |                    FCM/S3/OAuth (local default disabled or placeholder)
    v
FastAPI AI service -----> model artifacts and runtime data volume
```

## Runtime boundaries

- Mobile은 Backend HTTP 계약만 소비하며 MySQL, Redis, AI에 직접 접근하지 않습니다.
- Backend가 인증·권한, 영속성, Outbox, AI 호출 경계를 소유합니다.
- AI 서비스는 모델·벡터 인덱스가 없으면 degraded mode로 기동합니다.
- MySQL/Redis/AI service DNS는 Compose 내부에서만 사용하며 host 노출은 `127.0.0.1`로 제한합니다.
- Firebase, OAuth, AWS credential과 모델 artifact는 모노레포에 포함하지 않습니다.

## Import boundary

Backend는 별도 저장소 commit `88aad81`의 tracked snapshot입니다. 공개 안전을 위해 과거 Backend Git history와 ignored local files는 가져오지 않았습니다.
