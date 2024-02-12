package searchengine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import searchengine.config.Datasource;
import searchengine.config.Server;
import searchengine.config.Site;
import searchengine.config.SitesList;

@SpringBootApplication
@EnableConfigurationProperties({Server.class, Datasource.class, SitesList.class, Site.class})
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
