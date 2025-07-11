package net.eson.component;

import okhttp3.*;
import okio.BufferedSource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;

/**
 * @author Eson
 * @date 2025年07月10日 14:05
 */
@Component
public class QwenApiClient {
    private static final String API_KEY = "sk-7e3f22977b25483c9e48d5522a0c127c";
    private static final String API_URL = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation";

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(15))  // 连接超时
            .writeTimeout(Duration.ofSeconds(15))    // 写请求体超时
            .readTimeout(Duration.ofSeconds(30))     // 读响应超时
            .callTimeout(Duration.ofSeconds(35))     // 整个调用超时
            .retryOnConnectionFailure(true)           // 失败时自动重试
            .build();

    public BufferedSource callQwenStream(String prompt) throws IOException {
        MediaType jsonType = MediaType.parse("application/json");

        String json = """
    {
      "model": "qwen-turbo",
      "input": {
        "prompt": "%s"
      },
      "parameters": {
        "temperature": 0.7
      },
      "stream": true
    }
    """.formatted(prompt);

        RequestBody body = RequestBody.create(json, jsonType);

        Request request = new Request.Builder()
                .url(API_URL)
                .post(body)
                .addHeader("Authorization", "Bearer " + API_KEY)
                .addHeader("Content-Type", "application/json")
                .build();

        Response response = httpClient.newCall(request).execute();
        if (!response.isSuccessful() || response.body() == null) {
            throw new IOException("DashScope error: " + response);
        }
        // 返回 Okio Source（记得由调用方关闭）
        return response.body().source();
    }

}
    