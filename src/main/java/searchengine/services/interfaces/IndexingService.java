package searchengine.services.interfaces;

import searchengine.dto.indexing.indexStatus;
import searchengine.dto.indexing.pageStatus;

import java.io.IOException;


public interface IndexingService {
    indexStatus startIndexing() throws IOException;

    indexStatus stopIndexing();

    pageStatus addUpdatePage(String page);
}
