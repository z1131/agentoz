package com.deepknow.agentoz.mcp.tool;

import com.deepknow.agentoz.starter.annotation.AgentParam;
import com.deepknow.agentoz.starter.annotation.AgentTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * 知识库检索增强工具 (RAG)
 *
 * <p>连接 Python RAG 服务，为 Agent 提供私有知识库的存取能力。</p>
 * <p>Python 服务地址配置: agentoz.rag.url (默认 http://localhost:8000)</p>
 */
@Slf4j
@Component
public class RAGTool {

    @Value("${agentoz.rag.url:http://localhost:8000}")
    private String ragServiceUrl;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public RAGTool() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 将文件入库（从 OSS 链接）
     *
     * @param fileUrl 文件的 OSS 下载链接
     * @return 执行结果
     */
    @AgentTool(
        name = "ingest_knowledge_file",
        description = "将知识文件（PDF, 图片, Markdown）存入向量知识库。输入必须是可公开访问或带签名的 OSS URL。"
    )
    public String ingestKnowledgeFile(
        @AgentParam(name = "fileUrl", value = "文件的下载链接 (URL)") String fileUrl
    ) {
        if (fileUrl == null || fileUrl.isEmpty()) {
            return "Error: fileUrl cannot be empty.";
        }

        try {
            // 构造请求体
            Map<String, Object> payload = Map.of(
                "file_url", fileUrl,
                "metadata", Map.of("source", "agent_tool")
            );
            String jsonBody = objectMapper.writeValueAsString(payload);

            // 发送请求
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ragServiceUrl + "/ingest/file"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .timeout(Duration.ofMinutes(5)) // OCR 可能比较慢
                .build();

            log.info("📤 RAG Ingest: url={}", fileUrl);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return "知识入库成功。该文件已被索引，您现在可以检索其中的内容了。";
            } else {
                return "入库失败: " + response.body();
            }

        } catch (Exception e) {
            log.error("RAG Ingest Failed", e);
            return "执行出错: " + e.getMessage();
        }
    }

    /**
     * 检索知识库
     *
     * @param query 用户的问题或关键词
     * @return 相关的文本片段
     */
    @AgentTool(
        name = "search_knowledge",
        description = "从向量知识库中搜索相关内容。当需要查询特定文档细节、历史资料或私有数据时使用。"
    )
    public String searchKnowledge(
        @AgentParam(name = "query", value = "检索关键词或问题") String query
    ) {
        if (query == null || query.isEmpty()) {
            return "Error: query cannot be empty.";
        }

        try {
            // 构造请求体
            Map<String, Object> payload = Map.of(
                "query", query,
                "top_k", 5
            );
            String jsonBody = objectMapper.writeValueAsString(payload);

            // 发送请求
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ragServiceUrl + "/query"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .timeout(Duration.ofSeconds(30))
                .build();

            log.info("🔍 RAG Search: query={}", query);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode results = root.path("results");
                
                if (results.isEmpty()) {
                    return "未找到相关内容。";
                }

                StringBuilder sb = new StringBuilder("检索结果 (Top 5):\n\n");
                int idx = 1;
                for (JsonNode item : results) {
                    double score = item.path("score").asDouble();
                    String text = item.path("text").asText();
                    // 过滤低相关度结果 (可选)
                    if (score < 0.6) continue;
                    
                    sb.append(String.format("[%d] (匹配度: %.2f)\n%s\n\n", idx++, score, text));
                }
                
                if (idx == 1) return "找到了一些内容，但相关度都较低。建议优化提问方式。";
                
                return sb.toString();
            } else {
                return "检索失败: " + response.body();
            }

        } catch (Exception e) {
            log.error("RAG Search Failed", e);
            return "执行出错: " + e.getMessage();
        }
    }
}
