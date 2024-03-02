package searchengine.services.interfaces;

import searchengine.dto.searchinfo.SearchResponse;
import searchengine.model.SiteEntity;

import java.util.List;

public interface SearchService {
    SearchResponse getSearchResults(String site, String query,Integer limit,Integer offset);

    List<SiteEntity> findAllsites();
}
