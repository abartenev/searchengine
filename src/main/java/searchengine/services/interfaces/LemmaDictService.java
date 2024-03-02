package searchengine.services.interfaces;

import searchengine.model.Lemma;
import searchengine.model.Page;

import java.util.List;


public interface LemmaDictService {
    void fillLemmaDict(String s, Page p, Long lemmaPageCount);

    void saveLemmas(List<Lemma> lemmaList);

    void saveIndexes(List<Lemma> lemmas, String s, Page p, Long lemmaPageCount);
}
