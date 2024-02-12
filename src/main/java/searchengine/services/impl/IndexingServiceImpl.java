package searchengine.services.impl;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import searchengine.config.SitesList;
import searchengine.dto.FalseResponse;
import searchengine.dto.TrueResponse;
import searchengine.lemmas.Lemmatizer;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.parser.ParserWrapper;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.services.IndexingService;
import searchengine.utils.IndexingUtils;

import java.io.IOException;
import java.util.*;

@Service
@Slf4j
public class IndexingServiceImpl implements IndexingService {
    private SitesList sitesList;
    public static SiteRepository siteRepository;
    public static PageRepository pageRepository;
    public static IndexingUtils indexingUtils;
    public static Lemmatizer lemmatizer;

    @Autowired
    public IndexingServiceImpl(SitesList sitesList,
                               SiteRepository siteRepository,
                               PageRepository pageRepository,
                               IndexingUtils indexingUtils,
                               Lemmatizer lemmatizer) {
        this.sitesList = sitesList;
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.indexingUtils = indexingUtils;
        this.lemmatizer = lemmatizer;
    }

    /**
     * Метод выполняет индексацию по всем имеющимся в конфиге сайтам
     *
     * @return true в случае успешно выполненной индексации, false в противном
     */
    @Override
    public ResponseEntity performIndexing()  {
        if (getActiveIndexingThread() != null) {
            return new ResponseEntity<>(new FalseResponse(false, "Индексация уже запущена"),
                    HttpStatus.BAD_REQUEST);
        }
       indexingUtils.deleteAllWebsiteDate();

        ParserWrapper parserWrapper = new ParserWrapper();
        parserWrapper.setName("ParserWrapper");
        parserWrapper.setSitesList(sitesList);
        parserWrapper.start();

        return new ResponseEntity<>(new TrueResponse(true), HttpStatus.OK);
    }

    /**
     * Метод выполняет индексацию для указанной страницы
     *
     * @return true в случае успешно выполненной индексации, false в противном
     */
    @Override
    public ResponseEntity performIndexingByUrl(String url) throws InterruptedException {
        try {
            Page page = pageRepository.findByPath(url);
            searchengine.model.Site site = indexingUtils.getSiteByPagePath(url, sitesList);
            Document document = null;

            if (Jsoup.connect(url).execute().statusCode() >= 400) {
                return getFalseResponse();
            }

            document = Jsoup.connect(url).get();

            if (site == null) {
                return getFalseResponse();
            }

            if (indexingUtils.pageIsIndexed(page)) {
                indexingUtils.erasePageIndexing(page);
                page = uodatePage(page, site, url, document);
            } else if (page == null) {
                page = uodatePage(page, site, url, document);
            }

            boolean result = lemmatizer.addLemmas(document.html(), site, page);
            if (result) {
                return new ResponseEntity<>(new TrueResponse(true), HttpStatus.OK);
            }

            return getFalseResponse();
        } catch (IOException e) {
            e.printStackTrace();
            return getFalseResponse();
        }
    }

    private Page uodatePage(Page page, Site site, String url, Document document) throws IOException {
        return pageRepository.save(new Page(site, url, Jsoup.connect(url).execute().statusCode(), document.html()));
    }

    /**
     * Метод останалвивает индексацию в случае ее выполнения
     *
     * @return true в случае успешно выполненной индексации, false в противном
     */
    @Override
    public ResponseEntity stopIndexing() throws InterruptedException {
        ParserWrapper parserWrapper = getActiveIndexingThread();

        if (parserWrapper != null) {
            parserWrapper.stopIndexing(true);
            parserWrapper.interrupt();
            return new ResponseEntity<>(new TrueResponse(true), HttpStatus.OK);
        }

        return new ResponseEntity<>(new FalseResponse(false, "Индексация не запущена"),
                    HttpStatus.METHOD_NOT_ALLOWED);
    }

    private ParserWrapper getActiveIndexingThread() {
        Set<Thread> threadSet = Thread.getAllStackTraces().keySet();

        for (Thread item: threadSet) {
            if (item.getName().contains("ParserWrapper")) {
                return (ParserWrapper) item;
            }
        }

        return null;
    }

    private ResponseEntity getFalseResponse() {
        return new ResponseEntity<>(new FalseResponse(false,
                "Данная страница находится за пределами сайтов"),
                HttpStatus.METHOD_NOT_ALLOWED);
    }
}
