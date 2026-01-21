import os
import logging
import fitz  # PyMuPDF
import base64
import requests
from openai import OpenAI
from llama_index.core import (
    VectorStoreIndex,
    StorageContext,
    Settings,
    Document
)
from llama_index.vector_stores.tair import TairVectorStore
from llama_index.embeddings.dashscope import DashScopeEmbedding

logger = logging.getLogger(__name__)

# ============================================================
# 🚀 快速上线硬编码配置区 (少爷请在此修改)
# ============================================================
TAIR_CONFIG = {
    "host": "r-bp1q6kpm7rkdvivl5w.redis.rds.aliyuncs.com",
    "port": 6379,
    "password": "Aa1231231212123", # 格式为 "username:password" 或 "password"
    "db": 0
}

DASHSCOPE_CONFIG = {
    "api_key": "sk-4438e8cfa0494e17b93845b7aa8b0bab"
}

INDEX_NAME = "agentoz_knowledge"
# ============================================================

class OCRProcessor:
    def __init__(self, api_key: str):
        self.client = OpenAI(
            api_key=api_key,
            base_url="https://dashscope.aliyuncs.com/compatible-mode/v1",
        )

    def image_to_text(self, image_data: bytes) -> str:
        base64_image = base64.b64encode(image_data).decode('utf-8')
        try:
            completion = self.client.chat.completions.create(
                model="qwen-vl-ocr-latest",
                messages=[
                    {
                        "role": "user",
                        "content": [
                            {"type": "text", "text": "请提取图中所有文字内容，保持原始段落结构。"},
                            {
                                "type": "image_url",
                                "image_url": {"url": f"data:image/jpeg;base64,{base64_image}"}
                            }
                        ]
                    }
                ]
            )
            return completion.choices[0].message.content
        except Exception as e:
            logger.error(f"OCR failed: {e}")
            return ""

class RAGEngine:
    def __init__(self):
        self.index = None
        self.api_key = DASHSCOPE_CONFIG["api_key"]
        self.ocr = OCRProcessor(self.api_key)
        self._init_settings()
        self._init_storage()

    def _init_settings(self):
        Settings.llm = None
        Settings.embed_model = DashScopeEmbedding(model_name="text-embedding-v1", api_key=self.api_key)
        logger.info("LlamaIndex Settings initialized")

    def _init_storage(self):
        # 构造 Tair 连接 URL
        # 格式: redis://[:password@]host:port/db
        host = TAIR_CONFIG["host"]
        port = TAIR_CONFIG["port"]
        pwd = TAIR_CONFIG["password"]
        db = TAIR_CONFIG["db"]
        
        tair_url = f"redis://:{pwd}@{host}:{port}/{db}"
        
        try:
            logger.info(f"🔌 Connecting to Aliyun Tair: {host}:{port}")
            vector_store = TairVectorStore(
                tair_url=tair_url,
                index_name=INDEX_NAME,
                overwrite=False
            )
            storage_context = StorageContext.from_defaults(vector_store=vector_store)
            self.index = VectorStoreIndex.from_vector_store(vector_store=vector_store, storage_context=storage_context)
            logger.info(f"Tair Vector Store connected.")
        except Exception as e:
            logger.error(f"Tair connection failed: {e}")
            self.index = VectorStoreIndex.from_documents([])

    def _extract_text(self, file_url: str) -> str:
        """从文件 URL 提取文本内容"""
        logger.info(f"Extracting text from: {file_url}")
        response = requests.get(file_url)
        if response.status_code != 200:
            raise Exception(f"Failed to download file: {file_url}")
        
        file_content = response.content
        file_ext = file_url.split('?')[0].split('.')[-1].lower()
        
        full_text = ""
        if file_ext == 'pdf':
            doc = fitz.open(stream=file_content, filetype="pdf")
            for page in doc:
                pix = page.get_pixmap()
                img_bytes = pix.tobytes("jpeg")
                full_text += self.ocr.image_to_text(img_bytes) + "\n\n"
            doc.close()
        elif file_ext in ['jpg', 'jpeg', 'png', 'webp']:
            full_text = self.ocr.image_to_text(file_content)
        else:
            full_text = file_content.decode('utf-8', errors='ignore')
        
        return full_text.strip()

    def parse_file(self, file_url: str) -> str:
        """只解析文件，返回提取的文本，不入库"""
        return self._extract_text(file_url)

    def ingest_file(self, file_url: str, metadata: dict = None):
        """解析文件并入库向量索引"""
        full_text = self._extract_text(file_url)
        
        if not full_text:
            return {"status": "empty", "extracted_text": "", "message": "No text extracted."}

        doc_obj = Document(text=full_text, metadata=metadata or {"source": file_url})
        self.index.insert(doc_obj)
        return {"status": "success", "extracted_text": full_text, "message": "File indexed."}

    def query(self, query_text: str, top_k: int = 5):
        if not self.index: return []
        retriever = self.index.as_retriever(similarity_top_k=top_k)
        nodes = retriever.retrieve(query_text)
        return [{"text": n.node.get_text(), "score": n.score, "metadata": n.node.metadata} for n in nodes]

engine = RAGEngine()
