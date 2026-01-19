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

class IngestFileRequest(BaseModel):
    file_url: str
    metadata: dict = {}

class QueryRequest(BaseModel):
    query: str
    top_k: int = 5

@app.get("/")
def health_check():
    return {"status": "ok"}

@app.post("/ingest/file")
async def ingest_file(request: IngestFileRequest):
    """
    全能文件入库 (支持 PDF, Image, MD)
    """
    try:
        engine.ingest_file(request.file_url, request.metadata)
        return {"status": "success", "message": "File processed and indexed"}
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
