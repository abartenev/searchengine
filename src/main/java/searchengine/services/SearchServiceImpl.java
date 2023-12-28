package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.english.EnglishLuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.springframework.stereotype.Service;
import searchengine.dto.searchinfo.SearchResponse;
import searchengine.dto.searchinfo.SearchResponseData;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.SiteEntity;
import searchengine.repositories.indexRepo;
import searchengine.repositories.lemmaRepo;
import searchengine.repositories.siteRepo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {
    private final siteRepo siteRepo;
    private final lemmaRepo lemmaRepo;
    private final indexRepo indexRepo;
    private LuceneMorphology ruMorphology;
    private LuceneMorphology engMorphology;
    private List<String> wordsList;


    @Override
    public SearchResponse getSearchResults(String site, String query) {
        try {
            ruMorphology = new RussianLuceneMorphology();
            wordsList = ruMorphology.getMorphInfo(query);
            System.out.println(wordsList);
        } catch (IOException | RuntimeException e) {
            try {
                engMorphology = new EnglishLuceneMorphology();
                wordsList = engMorphology.getMorphInfo(query);
                System.out.println("Eng = " + wordsList);
            } catch (IOException | RuntimeException e1) {
                System.out.println("2 Ошибка при обработке слова " + query + " " + e.getLocalizedMessage());
            }
        }
        String word2find = wordsList.get(0);
        String lemma2find = word2find.substring(0, word2find.indexOf("|"));
        List<SiteEntity> siteEntities = new ArrayList<>();
        if (site.matches("All")) {
            siteEntities = siteRepo.findAll();
        } else {
            SiteEntity siteEntity = siteRepo.findBySiteUrl(site);
            siteEntities.add(siteEntity);
        }
        SearchResponse response = new SearchResponse();
        List<SearchResponseData> responseData = new ArrayList<>();
        siteEntities.forEach(siteEntity -> {
            Lemma lemma = lemmaRepo.findLemmaByName(lemma2find, siteEntity);
            List<Index> list = indexRepo.findIndex4Lemma(lemma);
            list.sort((o1, o2) -> o1.getRank() > o2.getRank() ? 1 : 0);
            list.forEach(index -> {
                SearchResponseData data = new SearchResponseData();
                data.setSite(index.getPage_id().getSite_Entity_id().getUrl());
                data.setSiteName(index.getPage_id().getSite_Entity_id().getName());
                data.setUrl(index.getPage_id().getPath());
                data.setTitle(index.getPage_id().getPath());
                data.setSnippet(index.getPage_id().getContent());
                data.setRelevance(Float.toString(index.getRank()));
                responseData.add(data);
            });
            response.setResult(true);
            response.setCount(response.getCount() + list.size());
            response.setData(responseData);
        });
        return response;
    }

    @Override
    public List<SiteEntity> findAllsites() {
        return siteRepo.findAll();
    }
}
