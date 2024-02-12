package searchengine.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;

@Data
@ConstructorBinding
@ConfigurationProperties(prefix = "indexing-settings.sites")
public class Site {
    public String url;
    public String name;

    public Site(String url, String name) {
        this.url = url;
        this.name = name;
    }
}
