package com.portfolio.devinsight.common;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class Json {
  private final JsonMapper mapper = JsonMapper.builder().build();

  public String write(Object value) {
    return mapper.writeValueAsString(value);
  }

  public <T> T read(String value, Class<T> type) {
    return mapper.readValue(value, type);
  }

  public JsonNode tree(String value) {
    return mapper.readTree(value);
  }
}
