package searchengine.parserData;

import lombok.*;
import org.springframework.stereotype.Component;
import searchengine.model.Lemma;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Data
@Getter
@Setter
public class SiteIndexingData {
    private Map<String, Lemma> lemmaMap = new HashMap<>();
    private Set<String> indexDataSet = new HashSet<>();


    public void clear() {
        lemmaMap.clear();
        indexDataSet.clear();
    }
}
