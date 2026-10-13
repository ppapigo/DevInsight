package com.portfolio.devinsight.analysis;

import com.portfolio.devinsight.common.*;
import com.portfolio.devinsight.config.AppProperties;
import jakarta.validation.Validator;
import java.time.Duration;
import java.util.List;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class OpenAiService implements StructuredAi {
  private final AppProperties props;
  private final Json json;
  private final AiBudget budget;
  private final Validator validator;
  private volatile OpenAiChatModel model;

  public OpenAiService(AppProperties props, Json json, AiBudget budget, Validator validator) {
    this.props = props;
    this.json = json;
    this.budget = budget;
    this.validator = validator;
  }

  @Override
  public void requireConfigured() {
    if (props.getOpenaiApiKey().isBlank())
      throw new ApiException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "AI_NOT_CONFIGURED",
          "실제 AI 분석에는 OPENAI_API_KEY 설정이 필요합니다.");
  }

  private synchronized OpenAiChatModel model() {
    if (model == null)
      model =
          OpenAiChatModel.builder()
              .options(
                  OpenAiChatOptions.builder()
                      .apiKey(props.getOpenaiApiKey())
                      .model(props.getOpenaiModel())
                      .timeout(Duration.ofSeconds(60))
                      .maxRetries(0)
                      .maxCompletionTokens(props.getMaxOutputTokens())
                      .build())
              .build();
    return model;
  }

  @Override
  public <T> T generate(String system, String input, String schema, Class<T> type) {
    requireConfigured();
    // UTF-8 byte count is a conservative upper bound for BPE tokens, including schema and framing.
    if (TextBudget.bytes(system) + TextBudget.bytes(input) + TextBudget.bytes(schema) + 512
        > props.getMaxInputTokens())
      throw new ApiException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "INPUT_BUDGET_EXCEEDED",
          "분석 입력 한도를 초과했습니다. 자료 또는 수집 파일 수를 줄여 주세요.");
    budget.reserve();
    try {
      var options =
          OpenAiChatOptions.builder()
              .model(props.getOpenaiModel())
              .maxCompletionTokens(props.getMaxOutputTokens())
              .responseFormat(
                  OpenAiChatModel.ResponseFormat.builder().jsonSchema(schema).strict(true).build())
              .build();
      var response =
          model()
              .call(
                  new Prompt(List.of(new SystemMessage(system), new UserMessage(input)), options));
      if (response == null
          || response.getResult() == null
          || !"stop".equalsIgnoreCase(response.getResult().getMetadata().getFinishReason()))
        throw new IllegalArgumentException("Incomplete model response");
      String content = response.getResult().getOutput().getText();
      if (content == null || content.length() > 60000)
        throw new IllegalArgumentException("Invalid model response");
      T result = json.read(content, type);
      if (!validator.validate(result).isEmpty())
        throw new IllegalArgumentException("Invalid model response");
      return result;
    } catch (Exception e) {
      throw new ApiException(
          HttpStatus.BAD_GATEWAY,
          "AI_RESPONSE_FAILED",
          "AI 응답을 완료하거나 검증하지 못했습니다. 기존 분석은 유지됩니다. 다시 시도할 수 있습니다.");
    }
  }
}
