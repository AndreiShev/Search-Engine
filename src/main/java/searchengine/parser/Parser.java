package searchengine.parser;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;
import searchengine.model.Site;
import searchengine.utils.IndexingUtils;

import java.io.IOException;
import java.util.*;

import static searchengine.parser.ParserWrapper.linksMap;

@Setter
@Getter
@Slf4j
public class Parser implements Runnable {
    private searchengine.model.Site site;
    private Map<String, Integer> partMap;
    public String siteHost;

    public Parser(Site site, Map<String, Integer> partMap) {
        this.site = site;
        this.partMap = partMap;
        siteHost = UriComponentsBuilder.fromUriString(site.getUrl()).build().getHost();
    }

    /**
     * Метод будет запускать парсинг страниц пока есть непроверенные страницы.
     */
    @Override
    public void run() {
        try {
            while (linksMap.values().contains(0)) {
                if (Thread.currentThread().isInterrupted())
                    throw new InterruptedException();

                partMap.putAll(IndexingUtils.resetPartMap());
                parseBundlePages();
            }
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            clearLinksPartMap();
        }
    }

    /**
     * Метод запускает парсинг для выбранной пачки страниц.
     */
    private void parseBundlePages() throws IOException, InterruptedException {
        if (Thread.currentThread().isInterrupted())
            throw new InterruptedException();

        for (String item: partMap.keySet()) {
            if (Thread.currentThread().isInterrupted())
                throw new InterruptedException();

            UriComponents baseURL = UriComponentsBuilder.fromUriString(item).build();
            IndexingUtils.updateMapBaseLinks(baseURL, site);
        }
        clearLinksPartMap();
    }

    /**
     * Метод очищает пачку ссылок после завершения/прерывания парсинга.
     */
    public void clearLinksPartMap() {
        partMap.clear();
    }
}
