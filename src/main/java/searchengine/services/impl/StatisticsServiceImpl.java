package searchengine.services.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.services.StatisticsService;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {
    private final SitesList sites;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private TotalStatistics total = new TotalStatistics();
    List<DetailedStatisticsItem> detailed = new ArrayList<>();

    /**
     * Метод собирает статистику количества сохраненных страниц и лемм на всех сайтах
     *
     * @return StatisticsResponse
     */
    @Override
    public StatisticsResponse getStatistics() {
        clearStatisticsData();
        total.setSites(sites.getSites().size());
        total.setIndexing(true);

        List<Site> sitesList = sites.getSites();
        setStatisticsData(sitesList);

        StatisticsResponse response = new StatisticsResponse();
        StatisticsData data = new StatisticsData();
        data.setTotal(total);
        data.setDetailed(detailed);
        response.setStatistics(data);
        response.setResult(true);
        return response;
    }

    /**
     * Метод непосредстивенно вытаскивает статистику из БД
     */
    private void setStatisticsData(List<Site> sitesList) {
        for (int i = 0; i < sitesList.size(); i++) {
            Site site = sitesList.get(i);
            searchengine.model.Site modelSite = siteRepository.findByName(site.getName());
            DetailedStatisticsItem item = new DetailedStatisticsItem();
            item.setName(site.getName());
            item.setUrl(site.getUrl());
            int pages = modelSite != null ? pageRepository.countAllBySiteId(modelSite) : 0;
            int lemmas = modelSite != null ? lemmaRepository.countAllBySiteId(modelSite) : 0;
            item.setPages(pages);
            item.setLemmas(lemmas);

            if (modelSite != null) {
                item.setStatus(modelSite.getStatus().toString());
                item.setError(modelSite.getLastError());
                item.setStatusTime(modelSite.getStatusTime().getTime());
            }

            total.setPages(total.getPages() + pages);
            total.setLemmas(total.getLemmas() + lemmas);
            detailed.add(item);
        }
    }


    private void clearStatisticsData() {
        total = new TotalStatistics();
        detailed = new ArrayList<>();
    }
}
