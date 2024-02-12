package searchengine.dto;

import lombok.Value;

@Value
public class FalseResponse {
    boolean result;
    String error;
}
