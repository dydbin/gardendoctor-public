# GardenDoctor AI service

FastAPI 기반 작물 진단·챗 서비스입니다. 공개 모노레포에는 모델 가중치, 원본 PDF, FAISS 인덱스, 실제 API 키가 포함되지 않습니다.

## Run from the monorepo root

```bash
cp infra/.env.example infra/.env
make -C infra ai-up
curl http://localhost:8000/health
```

필수 자산이 없으면 서비스는 `degraded` 상태로 기동합니다. `/`, `/docs`, `/health`는 사용할 수 있고 준비되지 않은 진단·챗 요청은 503을 반환합니다.

## Local Python run

Python 3.11 환경을 사용합니다.

```bash
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
cd ../..
make -C infra ai-run
```

환경 변수 이름은 monorepo root의 `infra/.env.example`을 참고하세요. 실제 값은 `infra/.env`에만 두고 커밋하지 않습니다. AI 소스는 설정 파일 위치를 알지 않으며 `infra/scripts/ai/run.sh` 또는 Compose가 필요한 프로세스 환경을 주입합니다.

## Optional capabilities

- 진단: 승인된 모델 파일을 `infra/.env`의 `AI_MODEL_HOST_DIR`에 로컬 배치
- 챗: `OPENAI_API_KEY`와 `AI_EMBEDDINGS_HOST_DIR/faiss_index`의 로컬 FAISS 인덱스 제공
- 외부 검색: `TAVILY_API_KEY` 제공

모델과 문서의 공개·배포 전에는 모노레포의 `docs/public-assets.md` 기준에 따라 권리와 체크섬을 검토해야 합니다.
