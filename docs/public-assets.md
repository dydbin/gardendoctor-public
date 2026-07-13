# Public asset policy

## License scope

별도 표기가 없는 소스 코드는 저장소 루트의 MIT License를 따릅니다. 이 허가는 각 기여자가 권리를 보유한 코드에 적용되며, 다음 항목에 대한 사용권이나 상표권을 자동으로 부여하지 않습니다.

- `apps/mobile/assets/icon/`과 `apps/mobile/assets/images/`의 시각 자산
- GardenDoctor·텃밭닥터 이름, 로고와 브랜드 요소
- 출처와 이용 조건이 별도로 표시된 제3자 코드·문서·데이터
- 아래 정책에 따라 Git 이력에서 제외하거나 별도로 배포하는 자산

시각·브랜드·제3자 자산을 재사용하거나 재배포하려면 원 권리자와 해당 자산의 이용 조건을 별도로 확인해야 합니다.

다음 자산은 기본 Git 이력에 포함하지 않습니다.

| Asset | Repository policy | Required before distribution |
| --- | --- | --- |
| `*.pt`, `*.pth` | excluded | owner, source, license, checksum, version |
| 원본 PDF | excluded | redistribution right and attribution |
| 학습·테스트 이미지 | excluded | provenance, consent, license |
| FAISS index | generated locally | source document review and reproducible build |
| SQLite/DB files | runtime only | sanitized fixtures only |
| 연락처가 포함된 농장 원본 Excel | excluded | source license, redistribution terms, privacy review |

승인된 자산을 별도 배포할 때는 다운로드 위치와 SHA-256 체크섬을 문서화하고 컨테이너에는 read-only로 마운트합니다.

공개 Backend는 실제 농장·운영자 정보를 포함하지 않는 `farms-public.tsv` 합성 fixture만 제공합니다. 실제 공공데이터를 다시 포함하려면 출처, 이용 조건, 갱신일과 연락처 공개 필요성을 먼저 문서화해야 합니다.
