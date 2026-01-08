package com.deepknow.agentoz.infra.converter.grpc;

import com.deepknow.agentoz.dto.MessageDTO;
import com.deepknow.agentoz.infra.adapter.grpc.*;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 历史消息转换器
 *
 * <p>负责将业务层的MessageDTO转换为Codex-Agent的HistoryItem (Proto)。</p>
 *
 * <h3>🔄 转换映射</h3>
 * <pre>
 * MessageDTO              →  HistoryItem (oneof)
 *   ├─ role = "user"      →    MessageItem (role="user", content=...)
 *   ├─ role = "assistant" →    MessageItem (role="assistant", content=...)
 *   └─ content            →    ContentItem (text=...)
 * </pre>
 *
 * <h3>📦 支持的消息类型</h3>
 * <ul>
 *   <li>普通消息 - MessageItem (role + text content)</li>
 *   <li>函数调用 - FunctionCallItem (暂未实现)</li>
 *   <li>函数返回 - FunctionCallOutputItem (暂未实现)</li>
 * </ul>
 *
 * @see MessageDTO
 * @see HistoryItem
 */
@Slf4j
public class HistoryProtoConverter {

    /**
     * 将单个MessageDTO转换为HistoryItem (Proto)
     *
     * @param dto 消息DTO
     * @return HistoryItem实例
     */
    public static HistoryItem toHistoryItem(MessageDTO dto) {
        if (dto == null) {
            log.warn("MessageDTO 为 null,返回空 HistoryItem");
            return HistoryItem.getDefaultInstance();
        }

        // 1. 构建ContentItem (目前只支持文本)
        ContentItem contentItem = ContentItem.newBuilder()
                .setText(dto.getContent())
                .build();

        // 2. 构建MessageItem
        MessageItem messageItem = MessageItem.newBuilder()
                .setRole(dto.getRole()) // "user" | "assistant" | "system"
                .addContent(contentItem)
                .build();

        // 3. 包装为HistoryItem (oneof类型)
        HistoryItem historyItem = HistoryItem.newBuilder()
                .setMessage(messageItem)
                .build();

        log.debug("MessageDTO 转换为 HistoryItem: role={}, contentLength={}",
                dto.getRole(), dto.getContent() != null ? dto.getContent().length() : 0);

        return historyItem;
    }

    /**
     * 批量转换MessageDTO列表
     *
     * @param dtos MessageDTO列表
     * @return HistoryItem列表
     */
    public static List<HistoryItem> toHistoryItemList(List<MessageDTO> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            log.debug("MessageDTO列表为空,返回空列表");
            return List.of();
        }

        return dtos.stream()
                .map(HistoryProtoConverter::toHistoryItem)
                .collect(Collectors.toList());
    }

    /**
     * 将HistoryItem (Proto) 转换回MessageDTO
     *
     * <p>用于将Codex-Agent返回的响应转换为业务层DTO。</p>
     *
     * @param historyItem Proto HistoryItem
     * @return MessageDTO
     */
    public static MessageDTO toMessageDTO(HistoryItem historyItem) {
        if (historyItem == null || !historyItem.hasMessage()) {
            log.warn("HistoryItem 为空或不是Message类型");
            return MessageDTO.builder()
                    .role("system")
                    .content("")
                    .build();
        }

        MessageItem messageItem = historyItem.getMessage();

        // 提取第一个ContentItem (简化处理,假设只有文本)
        String content = "";
        if (messageItem.getContentCount() > 0) {
            ContentItem item = messageItem.getContent(0);
            content = item.getText();
        }

        return MessageDTO.builder()
                .role(messageItem.getRole())
                .content(content)
                .build();
    }

    /**
     * 批量转换HistoryItem列表为MessageDTO列表
     *
     * @param historyItems Proto HistoryItem列表
     * @return MessageDTO列表
     */
    public static List<MessageDTO> toMessageDTOList(List<HistoryItem> historyItems) {
        if (historyItems == null || historyItems.isEmpty()) {
            return List.of();
        }

        return historyItems.stream()
                .map(HistoryProtoConverter::toMessageDTO)
                .collect(Collectors.toList());
    }
}