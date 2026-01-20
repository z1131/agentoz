from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
import os
import logging
from dotenv import load_dotenv
from app.core import engine

load_dotenv()
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(title="AgentOZ RAG Service")

class ParseFileRequest(BaseModel):
    file_url: str

class IngestFileRequest(BaseModel):
    file_url: str
    metadata: dict = {}

class QueryRequest(BaseModel):
    query: str
    top_k: int = 5

@app.get("/")
def health_check():
    return {"status": "ok"}

@app.post("/parse")
async def parse_file(request: ParseFileRequest):
    """
    只解析文件，返回提取的文本，不入库向量索引
    """
    try:
        text = engine.parse_file(request.file_url)
        return {"status": "success", "extracted_text": text}
    except Exception as e:
        logger.error(f"Parse failed: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/ingest/file")
async def ingest_file(request: IngestFileRequest):
    """
    解析文件并入库向量索引 (支持 PDF, Image, MD)
    返回提取的文本 + 入库状态
    """
    try:
        result = engine.ingest_file(request.file_url, request.metadata)
        return result
    except Exception as e:
        logger.error(f"Ingest failed: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/query")
async def query_knowledge(request: QueryRequest):
    try:
        results = engine.query(request.query, request.top_k)
        return {"query": request.query, "results": results}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
