package com.tianji.common.utils;

/**
 * @ClassName SignUtils
 * @author wusongsong
 * @since 2022/7/3 11:25
 * @version 1.0.0
 **/

import cn.hutool.core.util.HexUtil;
import com.tianji.common.exceptions.CommonException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.util.UriUtils;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 签名工具类
 * <p>
 * 使用accesskey和secretkey进行签名调用第三方
 */
@Slf4j
public class SignUtils {

    private static final String TOKEN_VERSION = "v1";

    private static final List<String> ALLOWED_METHODS = Arrays.asList("GET", "POST", "PUT", "DELETE", "HEAD");

    /**
     * 生成token，用于调用支付系统的模块或外部系统调用
     * 签名（Signature）加密前格式：|v1-{AK}-{ExpireTime}|{SK}|{UrlPath}|{Method}|{QueryParam}|{body}|
     * 加密后token格式 ： v1-{AK}-{ExpireTime}-{Signature}
     *
     * @param urlPath    访问uri
     * @param method     访问方法
     * @param queryParam 链接请求参数
     * @param body       请求body
     * @param expireTime 过期时间 单位ms
     * @param accessKey  加密accesskey，公开
     * @param secretKey  加密串，保密
     * @return 访问token
     */
    public static String generateToken(String urlPath, String method, String queryParam,
                                       Object body, long expireTime, String accessKey, String secretKey) {
        log.debug("generateToken: urlPath={}, method={}, expireTime={}", urlPath, method, expireTime);
        if (StringUtils.isEmpty(accessKey) || StringUtils.isEmpty(secretKey)) {
            log.error("invalid AK or SK");
            throw new CommonException("Invalid AK or SK");
        }

        if (StringUtils.isEmpty(urlPath)) {
            log.error("urlPath can't be empty!");
            throw new CommonException("Empty url path");
        }

        if (!ALLOWED_METHODS.contains(method)) {
            log.error("{} isn't an allowed method", method);
            throw new CommonException("invalid request method");
        }

        String token = "";
        try {
            // |v1-{AK}-{ExpireTime}|{SK}|
            StringBuilder sbSign = new StringBuilder(String.format("|%s-%s-%d|%s|", TOKEN_VERSION,
                    accessKey, expireTime, secretKey));

            // {UrlPath}|
            sbSign.append(UriUtils.decode(urlPath, RequestUtils.UTF8_ENC)).append("|");

            // {Method}|
            sbSign.append(method.toUpperCase()).append("|");

            // {QueryParam}|
            sbSign.append(RequestUtils.toSortQueryParams(queryParam));
            sbSign.append("|");

            // {body}|
            if (!ObjectUtils.isEmpty(body)) {
                if (body instanceof String) {
                    sbSign.append(body);
                } else {
                    sbSign.append(JsonUtils.toJsonStr(body));
                }
            }
            sbSign.append("|");
            log.debug("signature contents: {}", sbSign.toString());

            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            sha256.reset();
            sha256.update(sbSign.toString().getBytes("UTF-8"));

            //  v1-{AK}-{ExpireTime}-{Signature}
            token = String.format("%s-%s-%s-%s", TOKEN_VERSION, accessKey, expireTime,
                    new String(HexUtil.encodeHex(sha256.digest())));
            log.debug("token generated: version={} accessKey={} expireTime={}", TOKEN_VERSION, accessKey, expireTime);
        } catch (UnsupportedEncodingException e) {
            log.error("failed to decode url or query path");
            throw new RuntimeException("Bad encoded url path or query string");
        } catch (NoSuchAlgorithmException e) {
            log.error("MD5 algorithm not available", e);
        }

        return token;
    }

    /**
     * 校验token
     *
     * @param token      来自请求方法的token
     * @param uri        请求uri
     * @param method     请求方法
     * @param queryParam 请求参数
     * @param body       请求体
     * @param accessKey  加密accesskey，公开
     * @param secretKey  加密串，保密
     * @return 校验结果
     */
    public static boolean verifyToken(String token, String uri, String method,
                                      String queryParam, Object body, String accessKey, String secretKey) {
        String tokenHash = token != null && token.length() > 8 ? token.substring(0, 8) : String.valueOf(token);
        log.debug("verifyToken: method={} uri={} tokenHash={}", method, uri, tokenHash);

        if (StringUtils.isEmpty(token)) {
            log.warn("auth_reject method={} uri={} reason=empty_token", method, uri);
            return false;
        }

        try {
            String[] tokenParts = token.split("-");

            if (tokenParts.length != 4) {
                log.warn("auth_reject method={} uri={} tokenHash={} reason=invalid_format", method, uri, tokenHash);
                return false;
            }

            if (!TOKEN_VERSION.equals(tokenParts[0])) {
                log.warn("auth_reject method={} uri={} tokenHash={} reason=bad_version", method, uri, tokenHash);
                return false;
            }

            long expireTime = Long.parseLong(tokenParts[2]);
            if (expireTime < System.currentTimeMillis()) {
                log.warn("auth_reject method={} uri={} tokenHash={} reason=expired", method, uri, tokenHash);
                return false;
            }

            if (token.equals(generateToken(uri, method, queryParam, body, expireTime, accessKey, secretKey))) {
                log.debug("auth_ok method={} uri={} tokenHash={}", method, uri, tokenHash);
                return true;
            }
        } catch (Exception e) {
            log.error("auth_error method={} uri={} tokenHash={} error={}", method, uri, tokenHash, e.getMessage());
        }

        return false;
    }


    public static void main(String[] args) {
        String accessKey = "123";
        String secretKey = "456";
        String urlPath = "/pay/123456789";
        String method = "GET";
        String params = "";
        Map<String, Object> body = new HashMap<>();
//        body.put("keyword", "123");
        String token = generateToken(urlPath, method, params, body, System.currentTimeMillis()  + 360000,
                accessKey, secretKey);
        log.info("token : {}", token);

        boolean verifyToken = verifyToken(token, urlPath, method, params, body, accessKey, secretKey);
        log.info("check {}", verifyToken);

    }

}
