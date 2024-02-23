package searchengine.services.interfaces;

import searchengine.model.Page;


public interface LemmaDictService {
    void fillLemmaDict(String s, Page p, Long lemmaPageCount);
}
