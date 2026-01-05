package com.deepknow.platform.common;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * JSON 工具类
 */
public class JsonUtils {
    private static final Logger log = LoggerFactory.getLogger(JsonUtils.class);


    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 对象转 JSON 字符串
     */
    public static String toJson(Object obj) {
        try {
            return OBJECT_MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("JSON 序列化失败", e);
            return "{}";
        }
    }

    /**
     * JSON 字符串转对象
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return OBJECT_MAPPER.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            log.error("JSON 反序列化失败", e);
            return null;
        }
    }
}
