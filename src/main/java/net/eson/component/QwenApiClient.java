package net.eson.component;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.exception.ApiException;
import com.alibaba.dashscope.exception.InputRequiredException;
import com.alibaba.dashscope.exception.NoApiKeyException;
import io.reactivex.Flowable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.Arrays;
import java.util.function.Consumer;

/**
 * @author Eson
 * @date 2025年07月10日 14:05
 */
@Component
public class QwenApiClient {
    private static final Logger logger = LoggerFactory.getLogger(QwenApiClient.class);

    private final Generation gen;

    public QwenApiClient() {
        this.gen = new Generation();  // 无参构造即可
    }

    private GenerationParam buildGenerationParam(Message userMsg) {
        return GenerationParam.builder()
                .apiKey("sk-7e3f22977b25483c9e48d5522a0c127c")
                .model("qwen-turbo")   // 或 qwen-plus，根据需要替换
                .messages(Arrays.asList(userMsg))
                .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                .incrementalOutput(true)
                .build();
    }


    /**
     * 流式调用，带增量回调
     */
    public void streamCallWithMessage(Message userMsg, Consumer<String> onDelta)
            throws NoApiKeyException, ApiException, InputRequiredException {
        GenerationParam param = buildGenerationParam(userMsg);
        Flowable<GenerationResult> result = gen.streamCall(param);
        result.blockingForEach(message -> {
            String content = message.getOutput().getChoices().get(0).getMessage().getContent();
            onDelta.accept(content);
        });
    }
}