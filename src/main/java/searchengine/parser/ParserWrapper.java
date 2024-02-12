package searchengine.parser;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.model.Lemma;
import searchengine.model.SiteStatus;
import searchengine.utils.IndexingUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static searchengine.utils.IndexingUtils.getDomain;
import static searchengine.utils.IndexingUtils.updateMapBaseLinks;

@Setter
@Slf4j
public class ParserWrapper extends Thread {
    private SitesList sitesList;
    private searchengine.model.Site currentSite;
    public static volatile ConcurrentHashMap<String, Integer> linksMap = new ConcurrentHashMap<>();
    public static volatile ConcurrentHashMap<String, Integer> pageLoaderMap = new ConcurrentHashMap<>();
    public static volatile ConcurrentHashMap<String, PageLoaderInfo> pageLoaderInfoMap = new ConcurrentHashMap<>();
    public static Integer numberParserThreads;
    public static Integer pageLoaderThreadCount;
    public static volatile String domain;
    private ExecutorService service = null;
    private PageLoader pageLoader;
    private final Long timeLimit = (long) (60 * 40 * 1000);
    private final int sleepTime = 10000;
    public static volatile Integer phaseItem = 0;
    public static volatile int divider = 0;
    public static volatile int parserSleepTime = 400;
    public static final int minWaitingTime = 200;
    public static Map<String, Lemma> lemmaMap = new HashMap<>();
    public static Set<String> indexDataSet = new HashSet<>();

    /**
     * Метод запускает скрапинг сайтов по очереди. Сайты берутся из конфига. Если время обработки сайта превышает лимит,
     * то для него процесс будет остановлен, и парсер перейдет к следующему сайту. Лимит времени внедрен для банальной
     * экономии моего личного времени. Например, с Life.ru за ~20 минут записывается в БД
     * 6-7 тыс. страниц. Для презентации проекта этих данных вполне хватит.
     * */
    @Override
    public void run() {
        setThreadCount();
        try {
            for (Site item: sitesList.getSites()) {
                currentSite = IndexingUtils.addSite(new searchengine.model.Site(SiteStatus.INDEXING, new Date(), null, item.getUrl(), item.getName()));
                startPageLoader(currentSite);
                UriComponents baseURL = UriComponentsBuilder.fromUriString(item.getUrl()).build();
                domain = getDomain(baseURL.getHost());
                linksMap.put(baseURL.toString(), 1);
                updateMapBaseLinks(baseURL, currentSite);
                service = Executors.newFixedThreadPool(numberParserThreads);
                for (int i = 0; i <= numberParserThreads; i++) {
                    Parser parser = new Parser(currentSite, new HashMap<>());
                    service.submit(parser);
                }

                Date begin = new Date();
                service.shutdown();
                while (!IndexingUtils.exceedingTimeLimit(begin, timeLimit) && !service.isTerminated()) {
                    if (!pageLoader.isAlive())
                        startPageLoader(currentSite);

                    Thread.sleep(sleepTime);
                }

                stopIndexing(false);
                IndexingUtils.changeSiteStatus(currentSite.getName(), SiteStatus.INDEXED, "");
            }

            log.info("Indexing is over");
        } catch (Exception e) {
            log.error("ParserWrapper: " + e.getMessage());
        }
    }

    public void stopIndexing(boolean forcedStop) throws InterruptedException {
        while(!pageLoader.isInterrupted()) {
            pageLoader.interrupt();
            Thread.sleep(100);
        }

        while(!service.isTerminated()) {
            service.shutdownNow();
            Thread.sleep(100);
        }

        if (forcedStop) {
            IndexingUtils.changeSiteStatus(currentSite.getName(),
                    SiteStatus.FAILED,
                    "Indexing is forcibly stopped.");
        }

        clearLinksMapData();
    }


    private void setThreadCount() {
        if (Runtime.getRuntime().availableProcessors() <= 2) {
            pageLoaderThreadCount = 1;
            numberParserThreads = 1;
        }  else {
            pageLoaderThreadCount = 1;
            numberParserThreads = (Runtime.getRuntime().availableProcessors()-1) - pageLoaderThreadCount;
        }
    }

    private void startPageLoader(searchengine.model.Site site) {
        pageLoader = new PageLoader(this, site);
        pageLoader.setName("PageLoader");
        pageLoader.start();
    }

    private void clearLinksMapData() {
        linksMap.clear();
        pageLoaderMap.clear();
        pageLoaderInfoMap.clear();
    }
}
