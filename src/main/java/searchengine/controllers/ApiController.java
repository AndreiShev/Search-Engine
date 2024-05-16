package searchengine.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.search.SearchRequest;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.IndexingService;
import searchengine.services.SearchService;
import searchengine.services.StatisticsService;
import searchengine.services.impl.IndexingServiceImpl;
import searchengine.services.impl.SearchServiceImpl;
import searchengine.services.impl.StatisticsServiceImpl;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {
    private final StatisticsService statisticsService;
    private final IndexingService indexingService;
    private final SearchService searchService;

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity stopIndexing() throws InterruptedException {
        ResponseEntity result = indexingService.stopIndexing();
        return result;
    }

    @GetMapping("/startIndexing")
    @ResponseBody
    public ResponseEntity startIndexing() {
        ResponseEntity result = indexingService.performIndexing();
        return result;
    }

    @PostMapping("/indexPage")
    @ResponseBody
    public ResponseEntity indexPage(@RequestParam String url) throws InterruptedException {
        ResponseEntity result = indexingService.performIndexingByUrl(url);
        return result;
    }

    @GetMapping("/search{query}{offset}{limit}{site}")
    public ResponseEntity search(@RequestParam String query,
                                 @Nullable @RequestParam String site,
                                 @RequestParam int offset,
                                 @RequestParam int limit) {
        SearchRequest searchRequest = new SearchRequest(query, site, Integer.valueOf(offset), Integer.valueOf(limit));
        ResponseEntity responseEntity = searchService.performSearch(searchRequest);
        return responseEntity;
    }
}
