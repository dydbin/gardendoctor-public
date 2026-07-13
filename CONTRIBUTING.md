# Contributing

변경은 가능한 한 하나의 모듈에 집중하고 공개 안전 검사를 먼저 통과시켜 주세요.

```bash
make public-check
make app-check       # mobile 변경 시
make backend-check   # Backend 변경 시
make ai-syntax       # AI 변경 시
make stack-smoke     # 서비스 계약 또는 Compose 변경 시
```

모듈 간 API 계약이 바뀌면 Backend endpoint, Mobile consumer, AI payload를 같은 변경에서 함께 확인합니다. 의존성 추가, 외부 서비스 연결, 배포 변경은 사전에 소유자 검토가 필요합니다.

실제 `.env`, OAuth/AWS/Firebase credential, 모델·데이터 artifact와 생성된 application properties는 커밋하지 않습니다.
