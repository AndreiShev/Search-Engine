package searchengine.dto.search;

import lombok.Value;

@Value
public class SearchDto {
    String site;
    String siteName;
    String uri;
    String title;
    String snippet;
    Float relevance;
}
