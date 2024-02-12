package searchengine.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;

@Data
@ConstructorBinding
@ConfigurationProperties(prefix = "spring.datasource")
public class Datasource {
    private String username;
    private String password;
    private String url;

    public Datasource(String username, String password, String url) {
        this.username = username;
        this.password = password;
        this.url = url;
    }
}
