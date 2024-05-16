package searchengine.config;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class MorphologyConfig {

    @Bean
    public LuceneMorphology RussianLuceneMorphology() {
        try {
            LuceneMorphology luceneMorph = new RussianLuceneMorphology();
            return luceneMorph;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
