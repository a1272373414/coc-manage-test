package com.tencent.wxcloudrun.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;

/**
 * JWT 工具类：生成与解析登录令牌。
 */
@Component
public class JwtUtil {

	@Value("${coc.jwt.secret}")
	private String secret;

	@Value("${coc.jwt.expiration-minutes:1440}")
	private long expirationMinutes;

	/** 由配置密钥派生出固定 256 位的 HMAC 密钥，避免密钥长度不足导致的异常。 */
	private SecretKey getKey() {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] keyBytes = digest.digest(secret.getBytes(StandardCharsets.UTF_8));
			return new SecretKeySpec(keyBytes, "HmacSHA256");
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 not available", e);
		}
	}

	public String generateToken(AuthUser user) {
		Date now = new Date();
		Date exp = new Date(now.getTime() + expirationMinutes * 60_000L);
		return Jwts.builder()
			.setSubject(user.getUsername())
			.claim("userId", user.getUserId())
			.claim("groupNo", user.getGroupNo())
			.claim("superAdmin", user.isSuperAdmin())
			.claim("roleCodes", user.getRoleCodes())
			.claim("permissions", new ArrayList<>(user.getPermissions()))
			.setIssuedAt(now)
			.setExpiration(exp)
			.signWith(getKey(), SignatureAlgorithm.HS256)
			.compact();
	}

	@SuppressWarnings("unchecked")
	public AuthUser parseToken(String token) {
		Claims claims = Jwts.parserBuilder().setSigningKey(getKey()).build().parseClaimsJws(token).getBody();
		AuthUser user = new AuthUser();
		Object userIdObj = claims.get("userId");
		user.setUserId(userIdObj == null ? null : ((Number) userIdObj).longValue());
		user.setUsername(claims.getSubject());
		user.setGroupNo(claims.get("groupNo", String.class));
		user.setSuperAdmin(Boolean.TRUE.equals(claims.get("superAdmin", Boolean.class)));
		List<String> roleCodes = (List<String>) claims.get("roleCodes", List.class);
		if (roleCodes != null) {
			user.setRoleCodes(roleCodes);
		}
		List<String> perms = (List<String>) claims.get("permissions", List.class);
		if (perms != null) {
			user.setPermissions(new HashSet<>(perms));
		}
		return user;
	}

}
