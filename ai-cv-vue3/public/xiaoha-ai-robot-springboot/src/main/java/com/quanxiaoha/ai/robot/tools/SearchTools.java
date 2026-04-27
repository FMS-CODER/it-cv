package com.quanxiaoha.ai.robot.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class SearchTools {

    @Value("${searxng.url:http://localhost:8081}")
    private String searxngUrl;

    private static final String DEFAULT_SEARXNG_URL = "http://localhost:8081";

    @Tool(description = "联网搜索，获取最新信息、新闻、技术文档等实时数据（完全免费，自托管 SearXNG）")
    public String webSearch(String query) {
        log.info("## 开始联网搜索，查询：{}", query);

        try {
            String baseUrl = searxngUrl != null && !searxngUrl.isEmpty() 
                ? searxngUrl 
                : DEFAULT_SEARXNG_URL;
            
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String apiUrl = baseUrl + "/search?q=" + encodedQuery + "&format=json&categories=general";

            URI uri = URI.create(apiUrl);
            URL url = uri.toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(30000);

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                log.error("搜索请求失败，响应码：{}", responseCode);
                return "搜索请求失败，请稍后重试。SearXNG 服务可能未启动或端口不正确（当前配置：" + baseUrl + "）。";
            }

            BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)
            );
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            String jsonResponse = response.toString();
            log.info("## 搜索响应长度：{}", jsonResponse.length());

            String result = parseSearxngResponse(jsonResponse);
            log.info("## 搜索结果：{}", result.length() > 500 ? result.substring(0, 500) + "..." : result);
            return result;

        } catch (Exception e) {
            log.error("联网搜索异常", e);
            return "搜索过程中出现错误，请稍后重试。请确认 SearXNG 服务在 " + searxngUrl + " 上运行。错误：" + e.getMessage();
        }
    }

    private String parseSearxngResponse(String json) {
        StringBuilder result = new StringBuilder();
        result.append("【搜索结果】\n\n");

        int resultsStart = json.indexOf("\"results\":[");
        if (resultsStart == -1) {
            return "未找到搜索结果";
        }

        resultsStart += "\"results\":[".length();
        int bracketCount = 1;
        int resultsEnd = resultsStart;
        while (resultsEnd < json.length() && bracketCount > 0) {
            char c = json.charAt(resultsEnd);
            if (c == '[') bracketCount++;
            if (c == ']') bracketCount--;
            resultsEnd++;
        }

        String resultsArray = json.substring(resultsStart, resultsEnd - 1);

        int pos = 0;
        int count = 0;
        while (count < 10 && pos < resultsArray.length()) {
            int objStart = resultsArray.indexOf("{", pos);
            if (objStart == -1) break;

            int objBracketCount = 1;
            int objEnd = objStart + 1;
            while (objEnd < resultsArray.length() && objBracketCount > 0) {
                char c = resultsArray.charAt(objEnd);
                if (c == '{') objBracketCount++;
                if (c == '}') objBracketCount--;
                objEnd++;
            }

            String obj = resultsArray.substring(objStart, objEnd);

            String title = extractValue(obj, "title");
            String content = extractValue(obj, "content");
            String url = extractValue(obj, "url");

            if (title != null && !title.isEmpty()) {
                result.append(count + 1).append(". ").append(title).append("\n");
                if (content != null && !content.isEmpty()) {
                    result.append("   ").append(content).append("\n");
                }
                if (url != null && !url.isEmpty()) {
                    result.append("   链接：").append(url).append("\n");
                }
                result.append("\n");
                count++;
            }

            pos = objEnd;
        }

        if (count == 0) {
            return "未找到相关搜索结果，请尝试换个搜索词。";
        }

        return result.toString();
    }

    private String extractValue(String json, String key) {
        String searchKey = "\"" + key + "\":";
        int start = json.indexOf(searchKey);
        if (start == -1) return null;

        start += searchKey.length();
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }

        if (start >= json.length()) return null;

        char firstChar = json.charAt(start);

        if (firstChar == '"') {
            start++;
            int end = start;
            while (end < json.length()) {
                char c = json.charAt(end);
                if (c == '"' && (end == 0 || json.charAt(end - 1) != '\\')) {
                    break;
                }
                end++;
            }
            String value = json.substring(start, end);
            return value.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\");
        }

        return null;
    }
}
