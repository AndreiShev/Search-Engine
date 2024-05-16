package searchengine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import searchengine.config.*;

@SpringBootApplication
@EnableConfigurationProperties({Server.class, Datasource.class, SitesList.class, Site.class, SearchConfig.class})
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
