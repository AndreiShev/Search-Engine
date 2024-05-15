package searchengine.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;
import searchengine.config.SitesList;
import searchengine.lemmas.Lemmatizer;
import searchengine.model.*;
import searchengine.parserData.PageLoaderInfo;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.io.IOException;
import java.util.*;

import static searchengine.parser.ParserWrapper.*;

@Component
@Slf4j
public class IndexingUtils {
    private static SiteRepository siteRepository;
    private static PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private static Lemmatizer lemmatizer;

    @Autowired
    public IndexingUtils(SiteRepository siteRepository,
                         PageRepository pageRepository,
                         LemmaRepository lemmaRepository,
                         IndexRepository indexRepository,
                         Lemmatizer lemmatizer) {
        IndexingUtils.siteRepository = siteRepository;
        IndexingUtils.pageRepository = pageRepository;
        this.indexRepository = indexRepository;
        this.lemmaRepository = lemmaRepository;
        IndexingUtils.lemmatizer = lemmatizer;
    }

    public void deleteAllWebsiteDate() {
        indexRepository.deleteAll();
        lemmaRepository.deleteAll();
        pageRepository.deleteAll();
        siteRepository.deleteAll();
    }

    public static Document getJsoupDocument(String url) throws IOException, InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException();
        }

        Connection connection = Jsoup.connect(url).userAgent(RandomUserAgent.getRandomUserAgent()).timeout(100000).get().connection();
        int status = connection.execute().statusCode();
        if (status < 200 || status > 299) {
            throw new IOException(url + " connection failed.");
        }

        return connection.get();
    }

    public Site getSiteByPagePath(String path, SitesList sitesList) {
        UriComponents pageUri = UriComponentsBuilder.fromUriString(path).build();
        UriComponents siteUri = UriComponentsBuilder.newInstance().scheme(pageUri.getScheme()).host(pageUri.getHost()).build();
        Site site = siteRepository.findByUrl(siteUri.toString());

        if (site != null) {
            return site;
        }

        for (searchengine.config.Site item: sitesList.getSites()) {
            if (item.getUrl().equals(siteUri.toString())) {
                site = siteRepository.save(new Site(SiteStatus.FAILED,
                        new Date(),
                        null,
                        item.getUrl(),
                        item.getName()));
                break;
            }
        }

        return site;
    }

    /**
     * Метод проверяет выполнена ли индексация страницы
     */
    public boolean pageIsIndexed(Page page) {
        if (page == null || page.equals(new Page())) {
            return false;
        }

        return indexRepository.findByPageId(page).size() != 0;
    }

    public void erasePageIndexing(Page page) {
        List<Index> indexList = indexRepository.findByPageId(page);

        if (indexList.size() != 0) {
            for (Index item: indexList) {
                indexRepository.deleteAllByLemmaId(item.getLemmaId());
                Lemma lemma = item.getLemmaId();
                lemma.setFrequency(lemma.getFrequency()-1);
                lemmaRepository.save(lemma);
            }
        }

        pageRepository.delete(page);
    }

    public static Site addSite(Site site) {
        if (site != null && !site.equals(new searchengine.model.Site())) {
            siteRepository.save(site);
        }

        return site;
    }

    public static Page addPage(Page page) throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException();
        }

        if (page != null && !page.equals(new searchengine.model.Page())) {
            page = pageRepository.save(page);
            changeSiteStatusTime(page.getSiteId().getId());
        }

        return page;
    }

    public static void changeSiteStatus(String name, SiteStatus status, String error) {
        if (name != null && status != null && !name.equals("")) {
            Site site = siteRepository.findByName(name);

            if (site != null) {
                site.setStatus(status);
                site.setLastError(error);
                site.setStatusTime(new Date());
                siteRepository.save(site);
            }
        }
    }

    public static void changeSiteStatusTime(Integer id) {
        if (id != null) {
            Optional<Site> site = siteRepository.findById(id);
            site.get().setStatusTime(new Date());
            siteRepository.save(site.get());
        }
    }

    /**
     * Метод загружает страницу по переданной ссылке. В случае успеха записывает данные для последующей индексации
     * и выбирает ссылки с текущей страницы в соответствующие хранилища
     *
     * @param url ссылка на проверки
     * @param site текущий сайт
     */
    public static void updateMapBaseLinks(UriComponents url, Site site) throws InterruptedException
    {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException();
        }

        Thread.sleep(parserSleepTime);
        Document document;
        try {
            document = getJsoupDocument(url.toString());
        } catch (IOException e) {
            log.info("url: " + url + ", " + e.getMessage());
            return;
        }

        setPageLoaderInfo(url, site, document);
        addLinksToLinksMap(url, document);
    }

    private static void setPageLoaderInfo(UriComponents url, Site site, Document document) throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException();
        }

        pageLoaderMap.put(url.toString(), 0);
        PageLoaderInfo pageLoaderInfo = new PageLoaderInfo(site, url.toString(), document.html());
        pageLoaderInfoMap.put(url.toString(), pageLoaderInfo);
    }

    /**
     * Метод выбирает все ссылки на текущей странице и добавляет их во временное хранилище
     *
     * @param url ссылка, от которой берется база для относительных ссылок
     * @param document объект, с которого выбираются ссылки
     */
    private static void addLinksToLinksMap(UriComponents url, Document document) throws InterruptedException {
        List<Element> links = document.getElementsByTag("a");

        for (Element itemLink: links) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }

            String itemHref = itemLink.attr("href");
            if (IndexingUtils.urlIsSubSite(itemHref, domain)) {
                addLinkToLinksMap(UriComponentsBuilder.fromUriString(itemHref).build());
            } else if (IndexingUtils.urlIsRelative(itemHref)) {
                addLinkToLinksMap(UriComponentsBuilder.fromUriString(getBase(url).toString()).path(itemHref).build());
            }
        }
    }

    /**
     * Метод получает базу ссылки
     *
     * @param url ссылка, от которой берется база для относительных ссылок
     */
    private static UriComponents getBase(UriComponents url) throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException();
        }

        return UriComponentsBuilder.newInstance().scheme(url.getScheme()).host(url.getHost()).build();
    }

    /**
     * Метод проверяет не является ли ссылка абсолютной
     *
     * @param url ссылка для проверки
     * @param siteDomain домен текущего сайта
     * @return результат проверки
     */
    public static boolean urlIsSubSite(String url, String siteDomain) throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException();
        }

        UriComponents uri = UriComponentsBuilder.fromUriString(url).build();
        UriComponents resultUri = null;

        if (uri.getHost() != null) {
            resultUri = rebuildLink(uri);
            return resultUri.toString().contains(siteDomain);
        }

        return false;
    }

    /**
     * Метод проверяет ссылка относительная или нет
     *
     * @param url ссылка для проверки
     * @return результат проверки
     * */
    public static boolean urlIsRelative (String url) throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException();
        }

        return (!url.equals("/") && !urlIsFile(url));
    }

    /**
     * Метод проверяет наличие файловых расширений в ссылке
     *
     * @param url ссылка для проверки
     * @return результат проверки
     */
    public static boolean urlIsFile(String url) throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException();
        }

        return url.contains(".pdf") || url.contains(".fb") || url.contains(".mobi") || url.contains(".djvu")
                || url.contains(".doc") || url.contains(".txt");
    }

    /**
     * Метод проверяет наличие файловых расширений в ссылке
     *
     * @param host текущего сайта
     * @return домен текущего сайта
     */
    public static String getDomain(String host) throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException();
        }

        if (StringUtils.countMatches(host, ".") > 1) {
            return host.substring(host.indexOf(".")+1);
        } else {
            return host;
        }
    }

    /**
     * Метод запускает индексацию пачки страниц, удаляя информацию об индексированных страницах из временного хранилища
     *
     * @param site текущий сайт
     * @param linkSet список страниц для индексации
     */
    public static void addLemmas(Site site, Set<String> linkSet) throws IOException, InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException();
        }

        lemmatizer.indexBundleOfPages(site, linkSet, pageLoaderInfoMap);
        for (String item: linkSet) {
            pageLoaderInfoMap.remove(item);
        }
    }

    /**
     * Непосредственное добавление ссылки во временное хранилище
     *
     * @param url ссылка для добавления в хранилище
     */
    private static void addLinkToLinksMap(UriComponents url) throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException();
        }

        UriComponents result = rebuildLink(url);
        if (!linksMap.containsKey(result.toString()))
            linksMap.put(result.toString(), 0);
    }

    /**
     * Метод перестраивает ссылку, избавляясь от атрибутов запроса
     *
     * @param url перестраиваемая ссылка
     * @return перестроенная ссылка
     */
    private static UriComponents rebuildLink(UriComponents url) throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException();
        }

        List<String> pathSegmentsTemp = new ArrayList<>(url.getPathSegments());
        List<String> pathSegments = new ArrayList<>();

        for (String item: pathSegmentsTemp) {
            if (!item.contains("#") && !item.contains("?") && !item.contains("&")) {
                pathSegments.add(item);
            }
        }

        Set<String> set = new HashSet<>(pathSegments);
        pathSegments.clear();
        pathSegments.addAll(set);
        String[] arr = pathSegments.toArray(new String[0]);

        return UriComponentsBuilder.newInstance().scheme(url.getScheme()).host(url.getHost()).pathSegment(arr).build();
    }

    /**
     * Метод разделяет поровну непроверенные ссылки между потоками
     */
    public static synchronized Map<String, Integer> resetPartMap() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException();
        }

        Map<String, Integer> result = new HashMap<>();
        List<String> unIndexingUrl = getUnIndexingUrl();
        setDivider(unIndexingUrl);

        int count = 0;
        for (String item: unIndexingUrl) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }

            if (count >= divider) {
                break;
            }

            result.put(item, linksMap.get(item));
            linksMap.replace(item, 1);
            count++;
        }

        checkPhaseItem();
        return result;
    }

    /**
     * Метод выбирает непроверенные ссылки из временного хранилища
     */
    public static List<String> getUnIndexingUrl() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException();
        }

        List<String> result = new ArrayList<>();
        for (String item: linksMap.keySet()) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }

            if (linksMap.get(item) == 0) {
                result.add(item);
            }
        }

        return result;
    }

    /**
     * Метод проверяет всем ли потокам выданы пачки ссылок на проверку. Если всем, значит наступает следующая итерация
     * и надо будет определять новое кол-во ссылок на поток
     */
    private static void checkPhaseItem() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException();
        }

        if (phaseItem <= numberParserThreads) {
            phaseItem++;
        } else {
            phaseItem = 0;
            divider = 0;
        }
    }

    /**
     * Метод определяет количество ссылок на поток
     *
     * @param unIndexingUrl непроверенные ссылки
     */
    private static void setDivider(List<String> unIndexingUrl) throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException();
        }

        if (unIndexingUrl.size() != 0 && divider == 0) {
            if (unIndexingUrl.size() < numberParserThreads) {
                divider = unIndexingUrl.size();
            } else {
                divider = unIndexingUrl.size() / numberParserThreads;
            }
        }
    }

    public static boolean exceedingTimeLimit(Date begin, Long timeLimit) {
        Date now = new Date();
        long difference = now.getTime() - begin.getTime();
        return difference >= timeLimit;
    }
}
