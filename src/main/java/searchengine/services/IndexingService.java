package searchengine.services;

import searchengine.dto.indexing.indexStatus;

public interface IndexingService {
    indexStatus startIndexing();
    indexStatus stopIndexing();
}
