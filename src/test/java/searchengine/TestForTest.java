package searchengine;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;
import searchengine.config.Server;
import searchengine.config.SitesList;
import searchengine.lemmas.Lemmatizer;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.services.impl.IndexingServiceImpl;
import searchengine.utils.IndexingUtils;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static searchengine.utils.IndexingUtils.urlIsFile;


@SpringBootTest
//@ActiveProfiles("test")
public class TestForTest {
    static volatile Map<String, Integer> linksMap = new ConcurrentHashMap<>();
    private static Integer maxThreadCount = 10;
    private LuceneMorphology luceneMorph = new RussianLuceneMorphology();
    @Autowired
    private PageRepository pageRepository;
    @Autowired
    private SiteRepository siteRepository;
    @Autowired
    private LemmaRepository lemmaRepository;
    @Autowired
    private IndexRepository indexRepository;
    @Autowired
    private Lemmatizer lemmatizer;

    @Autowired
    public Server server;

    @Autowired
    public SitesList sitesList;

    @Autowired
    public IndexingUtils indexingUtils;

    @Autowired
    public IndexingServiceImpl indexingService;

    public TestForTest() throws IOException {
    }

    @Test
    public void timeRunTest() throws IOException, InterruptedException {
        Map<String, Lemma> lemmaMap = new HashMap<>();
        Set<String> indexDataSet = new HashSet<>();

        for (searchengine.config.Site item: sitesList.getSites()) {
            Site site = siteRepository.findByName(item.getName());
            List<Integer> pageList = pageRepository.getListIdBySite(site);
            int step = 500;
            System.out.println(pageList.size());
            while (pageList.size() > 0) {
                List<Integer> tempList = pageList.stream().limit(step).collect(Collectors.toList());
                pageList.subList(0, step <= pageList.size()-1 ? step : pageList.size()).clear();
                System.out.println(pageList.size());
                List<Page> pathList = pageRepository.getByIdList(tempList);

                System.out.println(new Date());
                for (Page page: pathList) {
                    HashMap<String, Integer> lemmas = lemmatizer.getLemmas(lemmatizer.clearHTML(page.getContent()));
                    List<Lemma> newLemmaList = new ArrayList<>();
                    List<Index> newIndexList = new ArrayList<>();
                    List<Lemma> savedLemma = new ArrayList<>();

//                    for (String lemma: lemmas.keySet()) {
//                        newLemmaList.add(lemmatizer.checkLemma(site, lemma, lemmaMap, page, indexDataSet));
//                    }

                    savedLemma = lemmaRepository.saveAll(newLemmaList);

                    for (Lemma lemma: savedLemma) {
                        lemmaMap.put(lemma.getLemma(), lemma);
                        indexDataSet.add(page.getId() + " " + lemma.getId());
                        newIndexList.add(lemmatizer.createIndex(page, lemma, lemmas.get(lemma.getLemma())));
                    }
                    indexRepository.saveAll(newIndexList);
                }
                System.out.println(new Date());
            }

            lemmaMap.clear();
            indexDataSet.clear();
        }
    }

    @Test
    public void checkLink() throws InterruptedException {
       // indexingUtils.deleteAllWebsiteDate();


        String[] aLinks = {"http://dombulgakova.ru/#top", "http://events.skillbox.ru/#5", "http://highereducation.skillbox.ru/#bakalavr", "http://highereducation.skillbox.ru/#popupzero3", "http://eng.skillbox.ru/certificate#price-and-format"};
        List<String> links = Arrays.asList(aLinks);

        for (String item: links) {
            UriComponents baseURL = UriComponentsBuilder.fromUriString(item).build();
            System.out.println(rebuildLink(baseURL));
        }
    }

    @Test
    public void checkOneLink() throws InterruptedException {
        UriComponents url = UriComponentsBuilder.fromUriString("http://dombulgakova.ru").build();
        String itemHref = "#top";
        UriComponents test = UriComponentsBuilder.fromUriString(getBase(url).toString()).path(itemHref).build();
        System.out.println(rebuildLink(test));


    }

    @Test
    public void checkSubSite() throws InterruptedException {
        String url = "http://listim.com/wl/70/api#dombulgakova.ru";
        System.out.println(urlIsSubSite(url, "dombulgakova.ru"));

    }

    public static boolean urlIsSubSite(String url, String siteDomain) throws InterruptedException {
        if (Thread.currentThread().isInterrupted())
            throw new InterruptedException();

        UriComponents uri = UriComponentsBuilder.fromUriString(url).build();
        UriComponents resultUri = null;

        if (uri.getHost() != null) {
            resultUri = rebuildLink(uri);
            return resultUri.toString().contains(siteDomain);
        }

        return false;
    }


    private static UriComponents rebuildLink(UriComponents url) throws InterruptedException {
        if (Thread.currentThread().isInterrupted())
            throw new InterruptedException();

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
     * Метод получает базу ссылки
     *
     * @param url ссылка, от которой берется база для относительных ссылок
     */
    private static UriComponents getBase(UriComponents url) throws InterruptedException {
        if (Thread.currentThread().isInterrupted())
            throw new InterruptedException();

        return UriComponentsBuilder.newInstance().scheme(url.getScheme()).host(url.getHost()).build();
    }

    public void matcher(String content, String wordToFind) {
        Pattern word = Pattern.compile("\\b"+wordToFind+"\\b");
        Matcher match = word.matcher(content);
        int count = 0;

        while (match.find()) {
            content = new StringBuilder(content).insert(match.end() +count, "</b>").toString();
            content = new StringBuilder(content).insert(match.start()+count, "<b>").toString();
            System.out.println(content);
            count+=7;
        }
    }

    private static void addLintToLinksMap(UriComponents subBaseUrl) {
        String result = subBaseUrl.toString();

        if (subBaseUrl.getFragment() != null && subBaseUrl.getQuery() != null) {
            result = subBaseUrl.toString().replace( "#"+subBaseUrl.getFragment(), "");
            result = result.replace("?"+subBaseUrl.getQuery(), "");
        } else if (subBaseUrl.getFragment() != null) {
            result = subBaseUrl.toString().replace( "#"+subBaseUrl.getFragment(), "");
        } else if (subBaseUrl.getQuery() != null) {
            result = subBaseUrl.toString().replace( "?"+subBaseUrl.getQuery(), "");
        }

        System.out.println(result);
    }

    private ConcurrentHashMap<String, Integer> generateMap() {
        ConcurrentHashMap<String, Integer> linksMap = new ConcurrentHashMap<>();
        linksMap.put("http://live.skillbox.ru", 0);
        linksMap.put("http://live.skillbox.ru", 0);
        linksMap.put("http://live.skillbox.ru/", 0);
        for (Integer i = 0; i < 100; i++ ) {
            linksMap.put("http://live.skillbox.ru/" + i, 0);
        }

        return linksMap;
    }

    private Map<String, Integer> resetPartMap(Integer maxThreadCount) {
        Map<String, Integer> partMap = new ConcurrentHashMap<>();
        int sizePart = 100;

//        if (linksMap.keySet().size() < maxThreadCount) {
//            sizePart = linksMap.keySet().size();
//        } else {
//            sizePart = linksMap.keySet().size() / maxThreadCount;
//        }

        int count = 0;

        for (String item: linksMap.keySet()) {
            if (count >= sizePart) {
                break;
            }

            if (linksMap.get(item) == 0 && count <= sizePart) {
                partMap.put(item, linksMap.get(item));
                linksMap.replace(item, 1);
                count++;
            }
        }

        return partMap;
    }

    public static synchronized Map<String, Integer>  resetPartMap1(Map<String, Integer> partMap) throws InterruptedException {
        Thread.sleep(2000);
        System.out.println("Thread name: " + Thread.currentThread().getName());
        System.out.println("partMap.size() before: " + partMap.size());
        partMap.clear();
        int sizePart = 0;
        System.out.println("partMap.size() after clear: " + partMap.size());

        if (linksMap.keySet().size() < maxThreadCount) {
            sizePart = linksMap.keySet().size();
        } else {
            sizePart = linksMap.keySet().size() / maxThreadCount;
        }

        int count = 0;

        for (String item: linksMap.keySet()) {
            if (count >= sizePart) {
                break;
            }

            if (linksMap.get(item) == 0 && count <= sizePart) {
                partMap.put(item, linksMap.get(item));
                linksMap.replace(item, 1);
                count++;
            }
        }
        System.out.println("partMap.size() after: " + partMap.size());
        return partMap;
    }

    public static String getDomain(String host) throws InterruptedException {
        if (Thread.currentThread().isInterrupted())
            throw new InterruptedException();

        if (StringUtils.countMatches(host, ".") > 1) {
            return host.substring(host.indexOf(".")+1);
        } else {
            return host;
        }
    }


    public void first(UriComponents uri) throws InterruptedException {
        int test = 0;

        try {
            throw new IOException();
        } catch (IOException e) {
            System.out.println("asdf");
            second();
        }


        try {
            second();
        } catch (InterruptedException e) {
            System.out.println("поймано : " + e.getMessage());
        }
    }

    public void second() throws InterruptedException {
        throw new InterruptedException();

    }
}
