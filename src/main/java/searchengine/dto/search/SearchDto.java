package searchengine.dto.search;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.Value;

@Value
@Data
@RequiredArgsConstructor
public class SearchDto {
    private String site;
    private String siteName;
    private String uri;
    private String title;
    private String snippet;
    private Float relevance;
}
