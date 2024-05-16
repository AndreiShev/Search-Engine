package searchengine.services;

import org.springframework.http.ResponseEntity;
import searchengine.dto.search.SearchRequest;

import java.io.IOException;

public interface SearchService {
    ResponseEntity performSearch(SearchRequest searchRequest);
    void searchForPagesOnSite(String site) throws IOException, InterruptedException;
}
