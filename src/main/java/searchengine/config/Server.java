package searchengine.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;

@Data
@ConstructorBinding
@ConfigurationProperties(prefix = "server")
public class Server {
    private Integer port;

    public Server(Integer port) {
        this.port = port;
    }
}
