package searchengine.dto.search;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.Value;

import java.util.List;

@Value
@Data
@RequiredArgsConstructor
public class SearchResponse {
    private boolean result;
    private int count;
    private List<SearchDto> data;
}
