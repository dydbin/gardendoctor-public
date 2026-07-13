# app/core/validation.py
from ultralytics import YOLO
from PIL import Image
import io
import os
from functools import lru_cache
from pathlib import Path

MODEL_PATH = Path(os.getenv("AI_MODEL_DIR", "ai_models")) / "validation_yolo_ver3.pt"


class ValidationModelUnavailableError(RuntimeError):
    """Raised when the separately distributed validation model is absent."""


@lru_cache(maxsize=1)
def get_validation_model():
    if not MODEL_PATH.is_file():
        raise ValidationModelUnavailableError(
            "AI 이미지 검증 모델이 준비되지 않았습니다."
        )
    try:
        return YOLO(str(MODEL_PATH))
    except Exception as exc:
        raise ValidationModelUnavailableError(
            "AI 이미지 검증 모델을 불러올 수 없습니다."
        ) from exc

# ... (이하 동일)
RELEVANT_OBJECT_NAMES = [
    'crop_part'
]

def validate_image_content(image_bytes: bytes) -> tuple[bool, str]:
    try:
        model = get_validation_model()
        image = Image.open(io.BytesIO(image_bytes))
        results = model(image, conf=0.1, verbose=False)
        
        detected_names = {model.names[int(c)] for r in results for c in r.boxes.cls}

        for name in detected_names:
            if name in RELEVANT_OBJECT_NAMES:
                print(f"✅ 유효성 검사 성공: 객체 '{name}' 감지.")
                return True, f"'{name}' 객체가 감지되어 분석을 진행합니다."

        print("❌ 유효성 검사 실패: 이미지에서 관련 객체를 찾을 수 없습니다.")
        return False, "이미지에서 작물 관련 객체(식물, 잎, 과일 등)를 찾을 수 없습니다."

    except ValidationModelUnavailableError:
        raise
    except Exception:
        return False, "이미지 유효성 검사 중 오류가 발생했습니다."
