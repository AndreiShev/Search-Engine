package searchengine.dto.search;
import lombok.Value;

import java.util.List;

@Value
public class SearchResponse {
    boolean result;
    int count;
    List<SearchDto> data;
}
