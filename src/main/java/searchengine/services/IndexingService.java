package searchengine.services;

import org.springframework.http.ResponseEntity;

public interface IndexingService {
    ResponseEntity performIndexing();
    ResponseEntity performIndexingByUrl(String url) throws InterruptedException;
    ResponseEntity stopIndexing() throws InterruptedException;
}
