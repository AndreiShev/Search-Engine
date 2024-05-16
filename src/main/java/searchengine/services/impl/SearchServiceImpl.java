package searchengine.services.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.jsoup.Jsoup;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import searchengine.config.SearchConfig;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.FalseResponse;
import searchengine.dto.search.SearchDto;
import searchengine.dto.search.SearchRequest;
import searchengine.dto.search.SearchResponse;
import searchengine.lemmas.Lemmatizer;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.SiteRepository;
import searchengine.services.SearchService;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {
    private final SiteRepository siteRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final Lemmatizer lemmatizer;
    private final SitesList siteList;
    private final SearchConfig searchConfig;
    private List<Page> finalPageList;
    private HashMap<Page, Float> absoluteRelevance = new HashMap<>();
    private HashMap<Page, Float> relativeRelevance = new HashMap<>();
    private HashMap<Lemma, Integer> sortedLemmas = new HashMap<>();
    private HashMap<String, String> ratioLemmasAndQuery = new HashMap<>();
    private List<SearchDto> searchDtoList = new ArrayList<>();
    private SearchRequest searchRequest;

    private HashMap<String, Set<Lemma>> siteLemmas = new HashMap<>();

    /**
     * Метод обертка для siteSearch() необходимый для поиска не только по одному сайту
     *
     * @return список результатов поиска, преобразованных в SearchDto
     */

    @Override
    public ResponseEntity performSearch(SearchRequest searchRequest) {
        if (searchRequest.getQuery().isBlank()) {
            return new ResponseEntity<>(new FalseResponse(false, "Задан пустой поисковый запрос"),
                    HttpStatus.BAD_REQUEST);
        }

        if (!newSearch(searchRequest)) {
            this.searchRequest = searchRequest;
            return new ResponseEntity<>(new SearchResponse(true, searchDtoList.size(), getResultList()), HttpStatus.OK);
        }

        resettingClassData();
        this.searchRequest = searchRequest;
        pageSearch();
        getAbsoluteRelevance();
        getRelativeRelevance();
        setSearchData();

        return new ResponseEntity<>(new SearchResponse(true, searchDtoList.size(), getResultList()),
                    HttpStatus.OK);
    }

    private boolean newSearch(SearchRequest searchRequest) {
        if (this.searchRequest != null
                && searchRequest.getQuery().equals(this.searchRequest.getQuery())
                && (searchRequest.getSite() == null && this.searchRequest.getSite() == null
                || searchRequest.getSite().equals(this.searchRequest.getSite()))) {
            return false;
        }

        return true;
    }

    private void pageSearch() {
        if (searchRequest.getSite() == null) {
            for (Site siteItem: siteList.getSites()) {
                searchForPagesOnSite(siteItem.getUrl());
            }
        } else {
            searchForPagesOnSite(searchRequest.getSite());
        }
    }

    /**
     * Для указанного сайта метод ищет удовлетворяющие запросу сниппеты
     *
     * @param site url сайта, по которому будет осуществляться поиск
     */
    @Override
    public void searchForPagesOnSite(String site) {
        searchengine.model.Site currentSite = siteRepository.findByUrl(site);
        HashMap<String, Integer> stringLemmasMap = lemmatizer.getLemmas(searchRequest.getQuery());

        if (stringLemmasMap.size() == 0) {
            return;
        }

        HashMap<Lemma, Integer> selectedLemmas = selectLemmasFromDB(stringLemmasMap.keySet(), currentSite);

        if (selectedLemmas.size() == 0 || stringLemmasMap.size() != selectedLemmas.size()) {
            return;
        }

        ratioLemmasAndQuery = lemmatizer.getRatioLemmasAndQuery(searchRequest.getQuery(), selectedLemmas);
        sortedLemmas = sortLemmas(selectedLemmas);
        siteLemmas.put(site, selectedLemmas.keySet());
        addPagesToFinalPageList(sortedLemmas);
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
        if (lemma != null && lemma.getFrequency() < searchConfig.getWordRankLimit()) {
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
    private void addPagesToFinalPageList(@NotNull HashMap<Lemma, Integer> sortedLemmas) {
        Lemma[] lemmaArray = sortedLemmas.keySet().toArray(new Lemma[sortedLemmas.size()]);
        List<Page> tempPageList = new ArrayList<>();
        List<Index> indexList = indexRepository.findByLemmaId(lemmaArray[0]);

        for (Index item: indexList) {
            tempPageList.add(item.getPageId());
        }

        if (lemmaArray.length == 1) {
            if (finalPageList == null || finalPageList.size() == 0) {
                finalPageList = tempPageList;
            } else {
                finalPageList.addAll(tempPageList);
            }

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
        List<Page> tempPageList = new ArrayList<>();

        for (Page item: pageList) {
            Index index = indexRepository.findByPageIdAndLemmaId(item, lemmaArray[1]);
            if (index != null) {
                tempPageList.add(index.getPageId());
            }
        }

        if (lemmaArray.length == 2) {
            if (finalPageList == null || finalPageList.size() == 0) {
                finalPageList = tempPageList;
            } else {
                finalPageList.addAll(tempPageList);
            }
        } else {
            setListPages(Arrays.copyOfRange(lemmaArray, 1, lemmaArray.length), tempPageList);
        }
    }

    /**
     * Метод выполняет подбор и запись результатов поиска по списку страниц на сайте
     *
     * @return список моделей searchDto
     */
    private List<SearchDto> setSearchData() {
        searchDtoList = new ArrayList<>();
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
            Set<Lemma> lemmas = siteLemmas.get(page.getSiteId().getUrl());

            for (Lemma lemma: lemmas) {
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
            if (absoluteRelevanceList.size() != 0) {
                float relevanceMax = absoluteRelevanceList.get(absoluteRelevanceList.size()-1);

                for (Page page: finalPageList) {
                    relativeRelevance.put(page, absoluteRelevance.get(page) / relevanceMax);
                }
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            log.error("Query: " + searchRequest.getQuery() + ", finalPageList.size = " + finalPageList.size()
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
            int firstIndex = (match.start() +count) < searchConfig.getSnippetInterval() ? 0 : (match.start() + count) - searchConfig.getSnippetInterval();
            count +=numberHighlightCharacters;
            int lastIndex = ((match.end()+count) > content.length() - searchConfig.getSnippetInterval()) ?
                    content.length() : (match.end()+count) + searchConfig.getSnippetInterval();

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

    private List<SearchDto> getResultList() {
        int start = searchRequest.getOffset() == 0 ? 0 : searchRequest.getLimit() * (searchRequest.getOffset()+1);
        int end = start + searchRequest.getLimit() > searchDtoList.size() ? searchDtoList.size() : start + searchRequest.getLimit();
        List<SearchDto> copyList = new ArrayList<>(searchDtoList.subList(start, end));
        return copyList;
    }

    private void resettingClassData() {
        try {
            searchDtoList.clear();
            sortedLemmas.clear();
            finalPageList.clear();
            absoluteRelevance.clear();
            relativeRelevance.clear();
        } catch (NullPointerException ex) {
            log.info("Очищаемые поля SearchServiceImpl пусты.");
        }
    }
}
