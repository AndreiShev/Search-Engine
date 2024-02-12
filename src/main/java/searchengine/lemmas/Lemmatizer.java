package searchengine.lemmas;

import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.stereotype.Component;
import searchengine.model.*;
import searchengine.parser.PageLoaderInfo;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.utils.IndexingUtils;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import static searchengine.utils.IndexingUtils.changeSiteStatusTime;

@Component
@Slf4j
public class Lemmatizer {
    private LuceneMorphology luceneMorph = new RussianLuceneMorphology();
    private LemmaRepository lemmaRepository;
    private IndexRepository indexRepository;
    private PageRepository pageRepository;
    private SiteRepository siteRepository;


    public Lemmatizer() throws IOException {}

    @Autowired
    public Lemmatizer(LemmaRepository lemmaRepository, IndexRepository indexRepository, PageRepository pageRepository, SiteRepository siteRepository) throws IOException {
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
        this.pageRepository = pageRepository;
        this.siteRepository = siteRepository;
    }


    /**
     * Метод разделяет текст на слова, находит все леммы и считает их количество.
     *
     * @param text текст из которого будут выбираться леммы
     * @return ключ является леммой, а значение количеством найденных лемм
     */
    public HashMap<String, Integer> getLemmas(String text) throws IOException {
        try {
            HashMap<String, Integer> result = new HashMap<>();

            if (text == null || text.isEmpty() || text.isBlank())
                return result;

            List<String> listWords = getListWords(text);

            for (String item: listWords) {
                List<String> wordBaseForms = luceneMorph.getNormalForms(item);
                String firstFormWord = wordBaseForms.get(0);

                if (isServicePartSpeech(firstFormWord))
                    continue;

                if (result.containsKey(firstFormWord)) {
                    result.put(firstFormWord, result.get(firstFormWord) + 1);
                } else {
                    result.put(firstFormWord, 1);
                }
            }

            return result;
        } catch (Exception e) {
            Logger.getLogger("Lemmatizer getLemmas: " + e.getMessage());
        }

        return new HashMap<>();
    }

    /**
     * Метод разделяет текст на слова, находит все леммы и считает их количество.
     *
     * @param html текст из которого будут выбираться леммы
     * @param site сайт, для которого будет установлена связь с леммой
     * @param page страница, по которой выполняется поиск и сохраннение лемм
     * @return true в случае успешного добавления, в противном случае false
     */
    public boolean addLemmas(String html, Site site, Page page) throws IOException, InterruptedException {
        if (Thread.currentThread().isInterrupted())
            throw new InterruptedException();

        try {
            HashMap<String, Integer> lemmas = getLemmas(clearHTML(html));
            List<Lemma> lemmaList = new ArrayList<>();
            List<Index> indexList = new ArrayList<>();
            List<Lemma> savedLemma = new ArrayList<>();

            for (String item: lemmas.keySet()) {
                if (Thread.currentThread().isInterrupted())
                    throw new InterruptedException();

                lemmaList.add(checkLemma(site, item, page));
            }

            savedLemma = lemmaRepository.saveAll(lemmaList);

            for (Lemma lemma: savedLemma) {
                if (Thread.currentThread().isInterrupted())
                    throw new InterruptedException();
                indexList.add(createIndex(page, lemma, lemmas.get(lemma.getLemma())));
            }

            indexRepository.saveAll(indexList);
            return true;
        } catch (IOException e) {
            log.error("Lemmatizer.addLemmas Info: " + e.getMessage());
            IndexingUtils.changeSiteStatus(site.getName(), SiteStatus.FAILED, e.getMessage());
            return false;
        }
    }

    /**
     * Метод очищает html  от тегов
     *
     * @param html код страницы
     * @return чистая от тегов строка
     */
    public String clearHTML(String html) {
        String result = Jsoup.parse(html).text();

        return result;
    }

    /**
     * Метод проверяет не является ли слово служебной частью речи
     *
     * @param firstFormWord проверяемое слово в его исходной форме
     * @return true если это служебная часть речи, в проитивном случае false
     */
    public boolean isServicePartSpeech(String firstFormWord) {
        List<String> morphValues = luceneMorph.getMorphInfo(firstFormWord);
        String firstValue = morphValues.get(0);

        return firstValue.contains("СОЮЗ") || firstValue.contains("ПРЕДЛ") || firstValue.contains("МЕЖД") || firstValue.contains("ЧАСТ") || firstValue.contains("ПРЕДК");
    }

    /**
     * Метод очищает строку, в которой ищутся леммы, от лишних символов, разбивая на массив отдельных слов
     *
     * @param text строка, в которой ищутся леммы
     * @return ичищенная и разбитая на массив отдельных слов строка
     */
    public List<String> getListWords(String text) {
        List<String> result = new ArrayList<>();

        text = text.toLowerCase(Locale.ROOT);
        String cleanLine = text.replaceAll("[^а-яё&&[^\\s{2,}]]+", " ");

        if (!cleanLine.isEmpty()){
            result = List.of(cleanLine.split(" "));
        }

        return result;
    }

    /**
     * Метод проверяет на существование в БД леммы. Увеличивает ее frequency на единицу, если лемма ранее встречалась на
     * других страницах сайта
     *
     * @param site сайт на котором найдена лемма
     * @param lemma лемма
     * @param page страница на которой найдена лемма
     * @return экземпляр класс Lemma
     */
    public Lemma checkLemma(Site site, String lemma, Page page) throws IncorrectResultSizeDataAccessException, InterruptedException {
        if (Thread.currentThread().isInterrupted())
            throw new InterruptedException();

        Lemma lemmaObj = null;
        try{
            lemmaObj = lemmaRepository.findByLemmaAndSiteId(lemma, site);

            if (lemmaObj != null && indexRepository.findByPageIdAndLemmaId(page, lemmaObj) == null) {
                lemmaObj.setFrequency(lemmaObj.getFrequency() + 1);
            } else {
                lemmaObj = new Lemma(site, lemma, 1);
            }
        } catch (IncorrectResultSizeDataAccessException e) {
            log.error("Lemma: " + lemma + ", Site: " + site.getName() + ", Page: " + page.getPath() + ". Info: " + e.getMessage());
            throw new IncorrectResultSizeDataAccessException(e.getActualSize());
        }

        return lemmaObj;
    }

    /**
     * Создает экземпляр класса Index для найденной на странице леммы
     *
     * @param page страница на котором найдена лемма
     * @param lemma лемма
     * @param wordRank колество повторений леммы на странице
     * @return экземпляр класса Index
     */
    public Index createIndex(Page page, Lemma lemma, Integer wordRank) {
        return new Index(page, lemma, wordRank);
    }

    public HashMap<String, String> getRatioLemmasAndQuery(String query, HashMap<Lemma, Integer> selectedLemmas)
            throws IOException, InterruptedException {
        if (Thread.currentThread().isInterrupted())
            throw new InterruptedException();

        List<String> listWords = getListWords(query);
        HashMap<String, String> result = new HashMap<>();

        for (String item: listWords) {
            List<String> wordBaseForms = luceneMorph.getNormalForms(item);
            String firstFormWord = wordBaseForms.get(0);

            if (isServicePartSpeech(firstFormWord))
                continue;

            for (Lemma lemmaItem: selectedLemmas.keySet()) {
                if (lemmaItem.getLemma().equals(firstFormWord)) {
                    result.put(firstFormWord, item);
                }
            }

        }

        return result;
    }

    /**
     * Сохраняет список Page в БД и производит их индексацию в один или более потоков.
     * Для больше производительности индексация производится по единоразово загруженным из БД данным
     *
     * @param site текущий сайт
     * @param linkSet список страниц на загрузку
     * @param pageLoaderInfoMap временное хранилище данных страниц для загрузки
     */
    public void indexBundleOfPages(Site site,
                                   Set<String> linkSet,
                                   ConcurrentHashMap<String, PageLoaderInfo> pageLoaderInfoMap,
                                   Map<String, Lemma> lemmaMap,
                                   Set<String> indexDataSet) throws IOException, InterruptedException {
        if (Thread.currentThread().isInterrupted())
            throw new InterruptedException();

        log.info("--------------------------------------Start saving: " + new Date() + ", count = " + linkSet.size());
        List<Page> pageList = setPageList(site, linkSet, pageLoaderInfoMap);
        List<Page> savedPageList = pageRepository.saveAll(pageList);
        changeSiteStatusTime(site.getId());
        indexSavedPageList(site, savedPageList, lemmaMap, indexDataSet);
        log.info("--------------------------------------End saving: " + new Date() + ", count = " + linkSet.size());
    }

    /**
     * Метод проверяет создана ли для сайта переданная лемма. Если создана, то увеличивает ее frequency на единицу.
     * В противном случае создает новый экземпляр леммы с frequency=1.
     *
     * @param site текущий сайт
     * @param lemma сохраняемая лемма
     * @param lemmaMap список всех лемм на сайте
     * @param page текущая страница
     * @param indexDataSet список PageId и LemmaId из таблицы Index.
     * @return экземпляр класса Lemma
     */
    public Lemma checkLemma(Site site, String lemma, Map<String, Lemma> lemmaMap, Page page, Set<String> indexDataSet)
            throws IncorrectResultSizeDataAccessException, InterruptedException {
        if (Thread.currentThread().isInterrupted())
            throw new InterruptedException();

        Lemma lemmaObj = lemmaMap.get(lemma);

        if (lemmaObj != null && !indexDataSet.contains(page.getId() + "_" + lemmaObj.getId())) {
            lemmaObj.setFrequency(lemmaObj.getFrequency() + 1);
        } else {
            lemmaObj = new Lemma(site, lemma, 1);
        }

        return lemmaObj;
    }

    /**
     * Создание списка страниц для загрузки
     *
     * @param site текущий сайт
     * @param linkSet список новых страниц для индексации
     * @param pageLoaderInfoMap временное хранилище данных страниц для загрузки
     */
    private List<Page> setPageList(Site site, Set<String> linkSet,
                                   ConcurrentHashMap<String, PageLoaderInfo> pageLoaderInfoMap) throws InterruptedException {
        if (Thread.currentThread().isInterrupted())
            throw new InterruptedException();

        List<Page> pageList = new ArrayList<>();
        for (String item : linkSet) {
            try {
                PageLoaderInfo pageLoaderInfo = pageLoaderInfoMap.get(item);
                Page page = new Page();
                page.setPath(pageLoaderInfo.getLink());
                page.setSiteId(site);
                page.setContent(pageLoaderInfo.getHtml());
                page.setCode(200);
                pageList.add(page);
            } catch(NullPointerException e){
                System.out.println("Url: " + item + ", Message: " + e.getMessage());
                continue;
            }
        }
        return pageList;
    }

    /**
     * Лемматизация новых страниц
     *
     * @param site текущий сайт
     * @param savedPageList список новых страниц для индексации
     * @param lemmaMap список всех лемм на сайте
     * @param indexDataSet список PageId и LemmaId из таблицы Index.
     */
    public void indexSavedPageList(Site site,
                                   List<Page> savedPageList,
                                   Map<String, Lemma> lemmaMap,
                                   Set<String> indexDataSet) throws IOException, InterruptedException {
        if (Thread.currentThread().isInterrupted())
            throw new InterruptedException();

        for (Page page: savedPageList) {
            HashMap<String, Integer> lemmas = getLemmas(clearHTML(page.getContent()));
            List<Lemma> newLemmaList = new ArrayList<>();
            List<Index> newIndexList = new ArrayList<>();
            List<Lemma> savedLemma = new ArrayList<>();

            for (String lemma: lemmas.keySet()) {
                newLemmaList.add(checkLemma(site, lemma, lemmaMap, page, indexDataSet));
            }

            savedLemma = lemmaRepository.saveAll(newLemmaList);

            for (Lemma lemma: savedLemma) {
                lemmaMap.put(lemma.getLemma(), lemma);
                indexDataSet.add(page.getId() + "_" + lemma.getId());
                newIndexList.add(createIndex(page, lemma, lemmas.get(lemma.getLemma())));
            }
            indexRepository.saveAll(newIndexList);
        }
    }
}
