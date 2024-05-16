package searchengine.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;

@Data
@ConstructorBinding
@ConfigurationProperties(prefix = "search-settings")
public class SearchConfig {
    private final Integer wordRankLimit;
    private final Integer snippetInterval;
}
