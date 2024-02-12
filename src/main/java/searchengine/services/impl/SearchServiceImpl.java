package searchengine.services.impl;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.FalseResponse;
import searchengine.dto.search.SearchDto;
import searchengine.dto.search.SearchResponse;
import searchengine.lemmas.Lemmatizer;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.services.SearchService;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SearchServiceImpl implements SearchService {
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final Lemmatizer lemmatizer;
    private SitesList siteList;
    private final int wordRankLimit = 2000;
    private final int snippetInterval = 110;
    private List<Page> finalPageList;
    private HashMap<Page, Float> absoluteRelevance = new HashMap<>();
    private HashMap<Page, Float> relativeRelevance = new HashMap<>();
    private HashMap<Lemma, Integer> sortedLemmas = new HashMap<>();
    private HashMap<String, String> ratioLemmasAndQuery = new HashMap<>();
    private String query;
    private String site;
    private int offset;
    private int limit;
    private List<SearchDto> searchDtoList = new ArrayList<>();

    @Autowired
    public SearchServiceImpl(LemmaRepository lemmaRepository, IndexRepository indexRepository,
                             PageRepository pageRepository, SiteRepository siteRepository,
                             Lemmatizer lemmatizer, SitesList sitesList) throws IOException {
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
        this.pageRepository = pageRepository;
        this.siteRepository = siteRepository;
        this.lemmatizer = lemmatizer;
        this.siteList = sitesList;
    }

    /**
     * Метод обертка для siteSearch() необходимый для поиска не только по одному сайту
     *
     * @return список результатов поиска, преобразованных в SearchDto
     */
    @Override
    public ResponseEntity performSearch() {
        if (query.isBlank()) {
            return new ResponseEntity<>(new FalseResponse(false, "Задан пустой поисковый запрос"),
                    HttpStatus.BAD_REQUEST);
        }

        searchDtoList.clear();

        try {
            if (site == null) {
                for (Site siteItem: siteList.getSites()) {
                    siteSearch(siteItem.getUrl());
                }
            } else {
                siteSearch(site);
            }
        } catch (IOException | InterruptedException e) {
            return new ResponseEntity<>(new FalseResponse(false, "Задан пустой поисковый запрос"),
                    HttpStatus.BAD_REQUEST);
        }

        return new ResponseEntity<>(new SearchResponse(true, searchDtoList.size(), searchDtoList),
                    HttpStatus.OK);
    }

    /**
     * Для указанного сайта метод ищет удовлетворяющие запросу сниппеты
     *
     * @param site url сайта, по которому будет осуществляться поиск
     */
    @Override
    public void siteSearch(String site) throws IOException, InterruptedException {
        searchengine.model.Site currentSite = siteRepository.findByUrl(site);
        HashMap<String, Integer> stringLemmasMap = lemmatizer.getLemmas(query);

        if (stringLemmasMap.size() == 0) {
            return;
        }

        HashMap<Lemma, Integer> selectedLemmas = selectLemmasFromDB(stringLemmasMap.keySet(), currentSite);

        if (selectedLemmas.size() == 0 || stringLemmasMap.size() != selectedLemmas.size()) {
            return;
        }

        ratioLemmasAndQuery = lemmatizer.getRatioLemmasAndQuery(query, selectedLemmas);
        sortedLemmas = sortLemmas(selectedLemmas);
        setFinalPageList(sortedLemmas);
        setSearchData();
        resettingClassData();
    }

    private @NotNull HashMap<Lemma, Integer> selectLemmasFromDB(@NotNull Set<String> lemmas, searchengine.model.Site site) {
        HashMap<Lemma, Integer> result = new HashMap<>();

        for (String lemmaItem: lemmas) {
            result.putAll(getLemmaMap(lemmaItem, site.getUrl()));
        }

        return result;
    }

    /**
     * Метод убирает из списка лемм слишком часто встречающиеся леммы
     *
     * @param lemmaItem лемма
     * @param site url сайта, на котором выполнять поиск
     * @return ключ является страницей, а значение ее релевантность
     */
    private @NotNull HashMap<Lemma, Integer> getLemmaMap(String lemmaItem, String site) {
        HashMap<Lemma, Integer> result = new HashMap<>();

        searchengine.model.Site siteModel = siteRepository.findByUrl(site);
        Lemma lemma = lemmaRepository.findByLemmaAndSiteId(lemmaItem, siteModel);
        if (lemma != null && lemma.getFrequency() < wordRankLimit) {
            result.put(lemma, lemma.getFrequency());
        }

        return result;
    }

    /**
     * Подготовка итогового списка лемм. По первой, самой редкой лемме из списка, находятся все страницы,
     * на которых она встречается. Далее ищутся соответствия следующей леммы из этого списка страниц. Операция
     * повторяется для каждой следующей леммы
     *
     * @param sortedLemmas отсортированные в порядке возрастания встречаемости на сайте леммы
     * @return заполняется переменная класса finalPageList
     */
    private void setFinalPageList(@NotNull HashMap<Lemma, Integer> sortedLemmas) {
        Lemma[] lemmaArray = sortedLemmas.keySet().toArray(new Lemma[sortedLemmas.size()]);
        List<Page> tempPageList = new ArrayList<>();
        List<Index> indexList = indexRepository.findByLemmaId(lemmaArray[0]);

        for (Index item: indexList) {
            tempPageList.add(item.getPageId());
        }

        if (lemmaArray.length == 1) {
            finalPageList = tempPageList;
            return;
        }

        setListPages(lemmaArray, tempPageList);
    }

    /**
     * Вспомогательный метод для setFinalPageList(), выполняющий алгоритм в описании setFinalPageList()
     *
     * @param lemmaArray отсортированные в порядке возрастания встречаемости на сайте леммы
     * @param pageList список страниц, на которых встречаются леммы
     * @return заполняется переменная класса finalPageList
     */
    private void setListPages(Lemma[] lemmaArray, @NotNull List<Page> pageList) {
        List<Page> tempListPage = new ArrayList<>();

        for (Page item: pageList) {
            Index index = indexRepository.findByPageIdAndLemmaId(item, lemmaArray[1]);
            if (index != null) {
                tempListPage.add(index.getPageId());
            }
        }

        if (lemmaArray.length == 2) {
            finalPageList = tempListPage;
        } else {
            setListPages(Arrays.copyOfRange(lemmaArray, 1, lemmaArray.length), tempListPage);
        }
    }

    /**
     * Метод выполняет подбор и запись результатов поиска по списку страниц на сайте
     *
     * @return список моделей searchDto
     */
    private List<SearchDto> setSearchData() {
        getAbsoluteRelevance();
        getRelativeRelevance();

        for (Page item: finalPageList) {
            JSONArray snippets = getSnippets(item);
            if (snippets.length() == 0) {
                continue;
            }

            String site = item.getSiteId().getUrl();
            String siteName = item.getSiteId().getName();
            String uri = UriComponentsBuilder.fromUriString(item.getPath()).build().getPath();
            String title = Jsoup.parse(item.getContent()).title();
            String snippet = (String) snippets.get(0);
            float relevance = relativeRelevance.get(item);

            SearchDto searchDto = new SearchDto(site, siteName, uri, title, snippet, relevance);
            searchDtoList.add(searchDto);
        }

        return searchDtoList;
    }

    private void getAbsoluteRelevance() {
        for (Page page: finalPageList) {
            for (Lemma lemma: sortedLemmas.keySet()) {
               Index index = indexRepository.findByPageIdAndLemmaId(page, lemma);

               if (absoluteRelevance.containsKey(page)) {
                   absoluteRelevance.put(page, absoluteRelevance.get(page) + index.getWordRank());
               } else {
                   absoluteRelevance.put(page, index.getWordRank());
               }
            }
        }

        absoluteRelevance = sortPages(absoluteRelevance);
    }

    private void getRelativeRelevance() {
        List<Float> absoluteRelevanceList = new ArrayList<>(absoluteRelevance.values().stream().toList());

        try{
            float relevanceMax = absoluteRelevanceList.get(absoluteRelevanceList.size()-1);

            for (Page page: finalPageList) {
                relativeRelevance.put(page, absoluteRelevance.get(page) / relevanceMax);
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            log.error("Query: " + query + ", finalPageList.size = " + finalPageList.size()
                    + ", absoluteRelevance.size = : " + absoluteRelevance.size()
                    + ", relativeRelevance.size: " + relativeRelevance.size());
        }
    }

    /**
     * Метод сортирует леммы в порядке возрастания их встречаемости на сайте
     *
     * @param unpopularLemmas отсортированные в порядке возрастания встречаемости на сайте леммы
     * @return ключ является леммой, а значение количество страниц, на которых слово встречается хотя бы один раз
     */
    private HashMap<Lemma, Integer> sortLemmas(@NotNull Map<Lemma, Integer> unpopularLemmas) {
        HashMap<Lemma, Integer> result = unpopularLemmas.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByValue())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (oldValue, newValue) -> oldValue, LinkedHashMap::new));
        return result;
    }

    /**
     * Метод сортирует страницы в порядке возрастания их релевантности
     *
     * @param pages ключ является леммой, а значение ее релевантность
     * @return ключ является страницей, а значение ее релевантность
     */
    private HashMap<Page, Float> sortPages(@NotNull Map<Page, Float> pages) {
        HashMap<Page, Float> result = pages.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByValue())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (oldValue, newValue) -> oldValue, LinkedHashMap::new));
        return result;
    }

    /**
     * Метод выбирает сниппеты по точному совпадению слов из запроса.
     *
     * @param page страница, на которой выполняется поиск сниппетов
     * @return массив сниппетов
     */
    private JSONArray getSnippets(@NotNull Page page) {
        String content = lemmatizer.clearHTML(page.getContent()).toLowerCase(Locale.ROOT);
        List<String> wordSnippetList = new ArrayList<>();

        for (Lemma item: sortedLemmas.keySet()) {
            String queryWord = ratioLemmasAndQuery.get(item.getLemma());
            if (wordSnippetList.size() == 0) {
                wordSnippetList = getFirstWordSnippetList(content, queryWord);
            } else {
                updateSnippetList(wordSnippetList, queryWord);
            }

            if (wordSnippetList.size() == 0) {
                break;
            }
        }

        JSONArray snippets = createJsonArrayFromList(wordSnippetList);
        return snippets;
    }

    private void updateSnippetList(List<String> wordSnippetList, String queryWord) {
        for (String listItem: new ArrayList<>(wordSnippetList)) {
            Pattern word = Pattern.compile("\\b"+queryWord+"\\b");
            Matcher match = word.matcher(listItem);
            wordSnippetList.remove(listItem);
            int count = 0;
            int numberHighlightCharacters = 7;
            while (match.find()) {
                listItem = new StringBuilder(listItem).insert(match.end()+count, "</b>").toString();
                listItem = new StringBuilder(listItem).insert(match.start()+count, "<b>").toString();
                count +=numberHighlightCharacters;
            }

            if (count==0) {
                continue;
            }

            wordSnippetList.add(listItem);
        }
    }


    /**
     * Метод делает выборку отрезков текста с наиболее уникальными словами поиска на странице.
     *
     * @param content текс, взятый их html страницы
     * @param wordToFind искомое слово
     * @return Список найденных отрезков текста
     */
    public List<String> getFirstWordSnippetList(String content, String wordToFind) {
        Pattern word = Pattern.compile("\\b"+wordToFind+"\\b");
        Matcher match = word.matcher(content);
        List<String> resultList = new ArrayList<>();
        int count = 0;
        int numberHighlightCharacters = 7;
        while (match.find()) {
            content = new StringBuilder(content).insert(match.end() +count, "</b>").toString();
            content = new StringBuilder(content).insert(match.start()+count, "<b>").toString();
            int firstIndex = (match.start() +count) < snippetInterval ? 0 : (match.start() + count) - snippetInterval;
            count +=numberHighlightCharacters;
            int lastIndex = ((match.end()+count) > content.length() - snippetInterval) ?
                    content.length() : (match.end()+count) + snippetInterval;

            resultList.add(content.substring(firstIndex, lastIndex));

            if (sortedLemmas.size() == 1) {
                break;
            }
        }

        return resultList;
    }

    public JSONArray createJsonArrayFromList(@NotNull List<String> list) {
        JSONArray jsonArray = new JSONArray();
        for (String item : list) {
            jsonArray.put(item);
        }
        return jsonArray;
    }

    private void resettingClassData() {
        sortedLemmas.clear();
        finalPageList.clear();
        absoluteRelevance.clear();
        relativeRelevance.clear();
    }


    @Override
    public void setQuery(String query) {
        this.query = query;
    };

    @Override
    public void setSite(String site) {
        this.site = site;
    };
    @Override
    public void setOffset(int offset) {
        this.offset = offset;
    };

    @Override
    public void setLimit(int limit) {
        this.limit = limit;
    };
}
