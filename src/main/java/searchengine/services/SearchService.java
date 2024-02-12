package searchengine.services;

import org.springframework.http.ResponseEntity;
import searchengine.dto.search.SearchDto;

import java.io.IOException;
import java.util.List;

public interface SearchService {
    ResponseEntity performSearch();
    void siteSearch(String site) throws IOException, InterruptedException;
    void setQuery(String query);
    void setSite(String site);
    void setOffset(int offset);
    void setLimit(int limit);
}
