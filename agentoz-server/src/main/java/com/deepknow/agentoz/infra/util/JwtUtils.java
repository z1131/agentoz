package com.deepknow.agentoz.infra.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class JwtUtils {

    // 生产环境应从配置读取，至少32字符
    private static final String SECRET_STRING = "DeepKnowAgentOZSecretKeyMustBeVeryLongAndSecure123456";
    private static final Key KEY = Keys.hmacShaKeyFor(SECRET_STRING.getBytes());
    
    // Token 有效期：24小时 (足够覆盖大多数长任务)
    private static final long EXPIRATION_TIME = 24 * 60 * 60 * 1000L;

    /**
     * 生成 Agent 专用 Token
     */
    public String generateToken(String agentId, String conversationId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("aid", agentId);
        claims.put("cid", conversationId);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(agentId)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(KEY, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 验证并解析 Token
     * @return Claims 或 null (如果验证失败)
     */
    public Claims validateToken(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(KEY)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (Exception e) {
            log.warn("Invalid JWT Token: {}", e.getMessage());
            return null;
        }
    }
}
