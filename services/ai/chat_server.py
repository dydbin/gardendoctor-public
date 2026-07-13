# app/main.py
from typing import Optional

from fastapi import FastAPI, File, UploadFile, HTTPException, Path, Depends, Body, Query
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy.orm import Session
from io import BytesIO
from PIL import Image
import requests
import os
import ipaddress
import socket
from pathlib import Path as FilePath
from sqlalchemy import text
from urllib.parse import urlparse

# --- 데이터베이스 및 모델 관련 임포트 ---
import database
import models 
from models import (
    DiagnosisResult, ErrorResponse, AnalysisRequest, FeedbackRequest, SuccessResponse, 
    QueryRequest, ChatResponse, ChatSessionResponse, ChatSessionWithMessages,
    ChatRequest, ChatMessage
)
# --- 수정된 진단 함수 임포트 ---
from core.predict import (
    MODEL_CONFIG,
    MODEL_UNAVAILABLE_MESSAGE,
    ROUTER_MODEL_CONFIG,
    classify_crop,
    predict_disease,
)
from core.validation import (
    MODEL_PATH as VALIDATION_MODEL_PATH,
    ValidationModelUnavailableError,
    validate_image_content,
)

# DB 테이블 생성
database.create_db_and_tables()

app = FastAPI(
    title="계층적 작물 질병 진단 API",
    description="이미지를 업로드하면 AI가 작물을 자동 분류한 후, 해당 작물에 특화된 모델로 질병을 진단합니다. 필요하면 legacy crop migration을 위해 crop_name 힌트를 함께 줄 수 있습니다.",
    version="5.0.0"
)

cors_origins = [
    origin.strip()
    for origin in os.getenv("AI_CORS_ORIGINS", "http://localhost").split(",")
    if origin.strip()
]

app.add_middleware(
    CORSMiddleware,
    allow_origins=cors_origins,
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)

def get_db():
    db = database.SessionLocal()
    try:
        yield db
    finally:
        db.close()


def validate_public_http_url(raw_url: str) -> str:
    parsed = urlparse(raw_url)
    if parsed.scheme not in {"http", "https"} or not parsed.hostname:
        raise ValueError("unsupported image URL")

    try:
        addresses = socket.getaddrinfo(
            parsed.hostname,
            parsed.port or (443 if parsed.scheme == "https" else 80),
            type=socket.SOCK_STREAM,
        )
    except socket.gaierror as exc:
        raise ValueError("image host cannot be resolved") from exc

    for address in addresses:
        ip = ipaddress.ip_address(address[4][0])
        if not ip.is_global:
            raise ValueError("non-public image host is not allowed")

    return parsed.geturl()


def download_image(raw_url: str, max_bytes: int = 10 * 1024 * 1024) -> bytes:
    safe_url = validate_public_http_url(raw_url)
    response = requests.get(
        safe_url,
        timeout=(3, 10),
        allow_redirects=False,
        stream=True,
    )
    response.raise_for_status()

    content = bytearray()
    for chunk in response.iter_content(chunk_size=64 * 1024):
        content.extend(chunk)
        if len(content) > max_bytes:
            raise ValueError("image response is too large")
    return bytes(content)


def resolve_crop_name(image_bytes: bytes, requested_crop_name: Optional[str]) -> tuple[Optional[str], Optional[str]]:
    """Use a caller-provided crop hint when present, otherwise fall back to router classification."""
    if requested_crop_name:
        return requested_crop_name.strip().lower(), None

    crop_name, _, error = classify_crop(image_bytes)
    if error:
        return None, error
    if not crop_name:
        return None, "이미지에서 작물을 식별할 수 없습니다."

    return crop_name, None

@app.get("/", summary="서버 상태 확인")
def read_root():
    return {"status": "OK", "message": "계층적 작물 질병 진단 서버가 준비되었습니다."}


@app.get("/health", summary="프로세스 및 기능 준비 상태")
def health():
    try:
        with database.engine.connect() as connection:
            connection.execute(text("SELECT 1"))
        database_ready = True
    except Exception:
        database_ready = False

    diagnosis_files = [
        FilePath(VALIDATION_MODEL_PATH),
        FilePath(ROUTER_MODEL_CONFIG["model_path"]),
        *[FilePath(config["model_path"]) for config in MODEL_CONFIG.values()],
    ]
    diagnosis_ready = all(path.is_file() for path in diagnosis_files)
    vector_index_dir = FilePath(
        os.getenv("AI_VECTOR_INDEX_DIR", "embeddings/faiss_index")
    )
    chat_ready = bool(os.getenv("OPENAI_API_KEY")) and vector_index_dir.is_dir()
    status = "ok" if database_ready and diagnosis_ready and chat_ready else "degraded"

    return {
        "status": status,
        "capabilities": {
            "database": database_ready,
            "diagnosis": diagnosis_ready,
            "chat": chat_ready,
        },
    }

@app.post(
    "/diagnose",
    summary="이미지 자동 진단 (작물 분류 ➡️ 질병 진단)",
    description="""
    사용자가 이미지 파일을 업로드하면, 시스템이 2단계로 분석합니다:
    1.  **작물 분류**: AI가 먼저 이미지의 작물(토마토, 고추 등)을 자동으로 식별합니다.
    2.  **질병 진단**: 식별된 작물에 특화된 AI 모델을 사용하여 질병을 진단합니다.

    선택적으로 `crop_name`을 넘기면 자동 작물 분류를 건너뛰고 지정한 작물의 specialist 모델을 직접 사용합니다.
    """,
    response_model=DiagnosisResult
)
async def diagnose_image_hierarchical(
    file: UploadFile = File(..., description="진단할 작물 이미지 파일"),
    crop_name: Optional[str] = Query(
        None,
        description="선택적으로 지정하는 작물 이름. 제공되면 자동 작물 분류를 건너뛰고 해당 작물의 specialist 모델을 직접 사용합니다."
    ),
    db: Session = Depends(get_db)
):
    if not file.content_type.startswith("image/"):
        raise HTTPException(status_code=400, detail="이미지 파일만 업로드할 수 있습니다.")

    image_bytes = await file.read()
    
    try:
        is_valid, message = validate_image_content(image_bytes)
    except ValidationModelUnavailableError as exc:
        raise HTTPException(status_code=503, detail=str(exc)) from exc
    if not is_valid:
        raise HTTPException(status_code=400, detail=f"{message} 분석할 수 있는 다른 사진을 업로드해주세요.")
        
    resolved_crop_name, error = resolve_crop_name(image_bytes, crop_name)
    if error:
        status_code = (
            503
            if error == MODEL_UNAVAILABLE_MESSAGE
            else (400 if crop_name else 500)
        )
        raise HTTPException(status_code=status_code, detail=error)

    # --- 2단계: 질병 진단 ---
    disease_info, confidence, error = predict_disease(resolved_crop_name, image_bytes)
    if error:
        status_code = 503 if error == MODEL_UNAVAILABLE_MESSAGE else 400
        raise HTTPException(status_code=status_code, detail=error)

    # --- 진단 결과 DB 저장 ---
    new_diagnosis = database.Diagnosis(
        filename=file.filename,
        crop_name=resolved_crop_name,
        predicted_disease=disease_info["name"],
        summary=disease_info["summary"],   
        solution=disease_info["solution"],
        confidence=round(confidence, 2)
    )
    db.add(new_diagnosis)
    db.commit()
    db.refresh(new_diagnosis)

    return DiagnosisResult(
        diagnosis_id=new_diagnosis.id,
        filename=file.filename,
        confidence=round(confidence, 2),
        disease_info=models.DiseaseInfo(**disease_info)
    )

@app.post(
    "/diagnose-by-url",
    summary="이미지 URL 자동 진단 (작물 분류 ➡️ 질병 진단)",
    description="이미지 URL을 전송하면, 시스템이 2단계로 분석하여 결과를 DB에 저장합니다. 필요하면 `crop_name`으로 legacy crop 힌트를 줄 수 있습니다.",
    response_model=DiagnosisResult
)
async def diagnose_by_url_hierarchical(
    request: AnalysisRequest,
    db: Session = Depends(get_db)
):
    try:
        image_bytes = download_image(request.image_url)
        Image.open(BytesIO(image_bytes)).verify()
    except Exception as exc:
        raise HTTPException(
            status_code=400,
            detail="이미지 URL을 안전하게 처리할 수 없습니다.",
        ) from exc

    try:
        is_valid, message = validate_image_content(image_bytes)
    except ValidationModelUnavailableError as exc:
        raise HTTPException(status_code=503, detail=str(exc)) from exc
    if not is_valid:
        raise HTTPException(status_code=400, detail=f"{message} 분석할 수 있는 다른 사진을 업로드해주세요.")

    resolved_crop_name, error = resolve_crop_name(image_bytes, request.crop_name)
    if error:
        status_code = (
            503
            if error == MODEL_UNAVAILABLE_MESSAGE
            else (400 if request.crop_name else 500)
        )
        raise HTTPException(status_code=status_code, detail=error)
        
    # --- 2단계: 질병 진단 ---
    disease_info, confidence, error = predict_disease(resolved_crop_name, image_bytes)
    if error:
        status_code = 503 if error == MODEL_UNAVAILABLE_MESSAGE else 400
        raise HTTPException(status_code=status_code, detail=error)
        
    # --- 진단 결과 DB 저장 ---
    filename_from_url = request.image_url.split("/")[-1]
    new_diagnosis = database.Diagnosis(
        filename=filename_from_url,
        crop_name=resolved_crop_name,
        predicted_disease=disease_info["name"],
        summary=disease_info["summary"],   
        solution=disease_info["solution"],
        confidence=round(confidence, 2)
    )
    db.add(new_diagnosis)
    db.commit()
    db.refresh(new_diagnosis)

    return DiagnosisResult(
        diagnosis_id=new_diagnosis.id,
        filename=filename_from_url,
        confidence=round(confidence, 2),
        disease_info=models.DiseaseInfo(**disease_info)
    )

@app.post(
    "/diagnoses/{diagnosis_id}/feedback",
    summary="진단 결과에 대한 피드백 제출",
    description="진단 ID를 통해 특정 진단 결과에 대한 사용자의 만족/불만족 피드백을 저장합니다.",
    response_model=SuccessResponse
)
async def create_feedback(
    diagnosis_id: int = Path(..., description="피드백을 남길 진단 기록의 ID"),
    feedback_data: FeedbackRequest = Body(...),
    db: Session = Depends(get_db)
):
    diagnosis = db.query(database.Diagnosis).filter(database.Diagnosis.id == diagnosis_id).first()
    if not diagnosis:
        raise HTTPException(status_code=404, detail="해당 ID의 진단 기록을 찾을 수 없습니다.")

    existing_feedback = db.query(database.Feedback).filter(database.Feedback.diagnosis_id == diagnosis_id).first()
    if existing_feedback:
        raise HTTPException(status_code=400, detail="이미 해당 진단에 대한 피드백이 존재합니다.")
    
    new_feedback = database.Feedback(
        diagnosis_id=diagnosis_id,
        is_satisfied=feedback_data.is_satisfied,
        comment=feedback_data.comment
    )
    db.add(new_feedback)
    db.commit()

    return SuccessResponse(message="소중한 피드백 감사합니다! AI 모델 개선에 큰 도움이 됩니다.")

# --- 챗봇 세션 관리 API ---
@app.get(
        "/api/chat/sessions", 
        summary="챗봇 대화(세션) 목록 조회",
        description="모든 챗봇 대화 목록을 조회합니다.",
        response_model=list[ChatSessionResponse]
        )
async def get_chat_sessions(
    db: Session = Depends(get_db)
    ):
    """모든 챗봇 세션 목록을 조회합니다."""
    sessions = db.query(database.ChatSession).order_by(database.ChatSession.updated_at.desc()).all()
    
    result = []
    for session in sessions:
        # 첫 번째 사용자 메시지를 제목으로 사용
        first_message = db.query(database.ChatMessage).filter(
            database.ChatMessage.session_id == session.id,
            database.ChatMessage.role == "user"
        ).order_by(database.ChatMessage.timestamp.asc()).first()
        
        # 메시지 개수 카운트
        message_count = db.query(database.ChatMessage).filter(
            database.ChatMessage.session_id == session.id
        ).count()
        
        # 첫 메시지 가져오기
        first_message_query = first_message.query if first_message else None
        
        # 챗봇 세션 응답 객체 생성
        result.append(ChatSessionResponse(
            id=session.id,
            query=first_message_query,
            created_at=session.created_at.isoformat(),
            updated_at=session.updated_at.isoformat(),
            message_count=message_count
        ))
    
    return result

@app.get(
        "/api/chat/sessions/{session_id}", 
        summary="특정 챗봇 대화방(세션) 조회",
        description="특정 챗봇 대화방의 모든 메시지를 조회합니다.",
        response_model=ChatSessionWithMessages)
async def get_chat_session(
    session_id: int, 
    db: Session = Depends(get_db)
    ):
    """특정 챗봇 세션과 모든 메시지를 조회합니다."""
    session = db.query(database.ChatSession).filter(database.ChatSession.id == session_id).first()
    if not session:
        raise HTTPException(status_code=404, detail="세션을 찾을 수 없습니다.")
    
    # 해당 세션의 모든 메시지 조회
    messages = db.query(database.ChatMessage).filter(
        database.ChatMessage.session_id == session_id
    ).order_by(database.ChatMessage.timestamp.asc()).all()
    
    message_list = [
        ChatMessage(
            id=msg.id,
            role=msg.role,
            query=msg.query,
            timestamp=msg.timestamp.isoformat()
        ) for msg in messages
    ]
    
    return ChatSessionWithMessages(
        id=session.id,
        created_at=session.created_at.isoformat(),
        updated_at=session.updated_at.isoformat(),
        messages=message_list
    )

@app.delete(
        "/api/chat/sessions/{session_id}", 
        summary="챗봇 대화방(세션) 삭제",
        description="특정 챗봇 대화방을 삭제합니다. 해당 세션과 관련된 모든 메시지도 함께 삭제됩니다.",
        response_model=SuccessResponse)
async def delete_chat_session(
    session_id: int, 
    db: Session = Depends(get_db)):
    """챗봇 세션을 삭제합니다."""
    session = db.query(database.ChatSession).filter(database.ChatSession.id == session_id).first()
    if not session:
        raise HTTPException(status_code=404, detail="세션을 찾을 수 없습니다.")
    
    db.delete(session)
    db.commit()
    
    return SuccessResponse(message="세션이 성공적으로 삭제되었습니다.")

@app.post(
        "/api/chat", 
        summary="챗봇과 대화하기",
        description="챗봇과 대화하며 세션에 메시지를 저장합니다.",
        response_model=ChatSessionWithMessages)
async def chat_with_session(
    request: ChatRequest,
    db: Session = Depends(get_db)
):
    """챗봇과 대화하며 세션에 메시지를 저장합니다."""
    
    # 기존 세션이 있으면 사용, 없으면 새로 생성
    if request.session_id:
        session = db.query(database.ChatSession).filter(database.ChatSession.id == request.session_id).first()
        if not session:
            raise HTTPException(status_code=404, detail="세션을 찾을 수 없습니다.")
    else:
        # 새 세션 생성
        session = database.ChatSession()
        db.add(session)
        db.commit()
        db.refresh(session)
    
    # 사용자 메시지 저장
    user_message = database.ChatMessage(
        session_id=session.id,
        role="user",
        query=request.query
    )
    db.add(user_message)
    
    # AI 응답 생성
    try:
        # 기존 대화 히스토리 가져오기
        previous_messages = db.query(database.ChatMessage).filter(
            database.ChatMessage.session_id == session.id
        ).order_by(database.ChatMessage.timestamp.asc()).all()
        
        # 대화 히스토리 포함하여 AI 에이전트 실행
        conversation_context = ""
        for msg in previous_messages:
            if msg.role == "user":
                conversation_context += f"사용자: {msg.query}\n"
            else:
                conversation_context += f"AI: {msg.query}\n"
        
        # 현재 질문과 함께 컨텍스트 전달
        full_query = f"이전 대화:\n{conversation_context}\n\n현재 질문: {request.query}"
        from langgraph_agent_react import run_agent

        ai_response = await run_agent(full_query)
        
        # AI 응답 메시지 저장
        assistant_message = database.ChatMessage(
            session_id=session.id,
            role="assistant",
            query=ai_response
        )
        db.add(assistant_message)
        
        # 세션 업데이트 시간 갱신
        session.updated_at = database.func.now()
        
        db.commit()
        
        # 전체 대화 히스토리 반환
        all_messages = db.query(database.ChatMessage).filter(
            database.ChatMessage.session_id == session.id
        ).order_by(database.ChatMessage.timestamp.asc()).all()
        
        message_list = [
            ChatMessage(
                id=msg.id,
                role=msg.role,
                query=msg.query,
                timestamp=msg.timestamp.isoformat()
            ) for msg in all_messages
        ]
        
        return ChatSessionWithMessages(
            id=session.id,
            created_at=session.created_at.isoformat(),
            updated_at=session.updated_at.isoformat(),
            messages=message_list
        )
        
    except Exception as exc:
        db.rollback()
        raise HTTPException(
            status_code=503,
            detail="AI 챗 기능이 현재 준비되지 않았습니다.",
        ) from exc
