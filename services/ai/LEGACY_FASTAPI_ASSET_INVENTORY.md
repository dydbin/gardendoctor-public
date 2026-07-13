# Legacy FastAPI Asset Inventory

## Purpose
- Track which assets were migrated from `gardendoctor-FastAPI` into `gardendoctor_ai-server`.
- Make `gardendoctor-FastAPI` retirement auditable before the old repo is removed from the active workflow.

## Migrated In This Slice

### Models
- `ai_models/best_pumpkin_classifier_811.pth`
- `ai_models/best_k_melon_classifier_811.pth`

### Test data
- `test_data/pumpkin/`
- `test_data/k_melon/`

### Runtime/config support
- `core/predict.py`
  - added specialist model entries for `pumpkin` and `k_melon`
- `core/config.py`
  - added disease metadata for `pumpkin` and `k_melon`
- `models.py`
  - added optional `crop_name` to `AnalysisRequest`
- `chat_server.py`
  - added optional crop override path for legacy crops

## Not Migrated In This Slice
- `app/ai_models/new_tomato_classifier_ver3.pth`
  - candidate only; needs benchmark before replacing current tomato model
- `app/ai_models/yolov8n.pt`
  - not migrated because active runtime already uses `validation_yolo_ver3.pt`
- training notebooks under `app/ai_models/*.ipynb`
  - not runtime-critical
- legacy server entrypoints, DB files, and README/run scripts
  - excluded from active runtime migration

## Known Limitation
- Automatic crop routing still only classifies the crops supported by the current router model.
- `pumpkin` and `k_melon` are enabled through an optional `crop_name` override path until a wider router model exists.

## Next Verification
- Confirm syntax:
  - `python -m py_compile chat_server.py models.py core/predict.py core/config.py`
- Confirm retained assets exist:
  - `find ai_models -maxdepth 1 -type f | sort`
  - `find test_data -maxdepth 2 -type d | sort`
