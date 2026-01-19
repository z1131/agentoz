# AgentOZ RAG Service

基于 **LlamaIndex** 构建的知识增强服务（Retrieval-Augmented Generation）。
为 AgentOZ 生态提供私有知识库的索引和检索能力。

## 功能特性

*   **Ingest**: 支持从 OSS 读取文档，进行切片和向量化。
*   **Retrieve**: 提供语义检索接口，返回 Top-K 相关片段。
*   **Vector Store**: 支持 Redis / PGVector (TBD)。

## 快速开始

### 本地开发

1. 进入目录：
   ```bash
   cd agentoz/agentoz-rag
   ```

2. 安装依赖：
   ```bash
   pip install -r requirements.txt
   ```

3. 启动服务：
   ```bash
   uvicorn app.main:app --reload --host 0.0.0.0 --port 18000
   ```

### Docker 部署

```bash
docker build -t agentoz-rag .
docker run -p 18000:18000 agentoz-rag
```

## API 文档

启动后访问: `http://localhost:18000/docs`
