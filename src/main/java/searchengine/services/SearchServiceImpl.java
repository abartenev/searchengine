package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
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
import searchengine.services.interfaces.SearchService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {
    private final siteRepo siteRepo;
    private final lemmaRepo lemmaRepo;
    private final indexRepo indexRepo;
    private String lemmaWord;
    private List<Index> indexList;

    public HashSet<String> getAllwords(String pageContent){
        HashSet<String> hashSet = new HashSet<>();
        hashSet.addAll(Arrays
                .stream(pageContent.split("\\p{Blank}+"))
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(s -> s.matches("[a-zA-Zа-яА-Я]+"))
                .filter(s -> s.length() > 2)
                .map(this::getLemmaWord)
                .collect(Collectors.toSet()));
        return hashSet;
    }

    public String getLemmaWord(String query) {
        try {
            LuceneMorphology ruMorphology = new RussianLuceneMorphology();
            List<String> res = ruMorphology.getMorphInfo(query);
            System.out.println(res.get(0));
            return res.get(0).substring(0, res.get(0).indexOf("|"));
        } catch (IOException | RuntimeException e) {
            try {
                LuceneMorphology engMorphology = new EnglishLuceneMorphology();
                List<String> res = engMorphology.getMorphInfo(query);
                System.out.println("Eng = " + res.get(0));
                return res.get(0).substring(0, res.get(0).indexOf("|"));
            } catch (IOException | RuntimeException e1) {
                System.out.println("2 Ошибка при обработке слова " + query + " " + e.getLocalizedMessage());
            }
        }
        return null;
    }

    public boolean isListHasWord(String word2check) {
        String lemmaWordLocal = getLemmaWord(word2check);
        return lemmaWordLocal.equals(lemmaWord);
    }

    @Override
    public SearchResponse getSearchResults(String site, String query, Integer limit) {
        HashSet<String> hashSet = getAllwords(query);
        indexList = null;
        hashSet.forEach(lemmaWord -> {
            if (indexList == null || indexList.isEmpty()) {
                Lemma lemma = lemmaRepo.findLemmaByName(lemmaWord,siteRepo.findBySiteUrl(site));
                indexList = indexRepo.findIndex4Lemma(lemma);
                indexList.sort((o1, o2) -> o1.getRank() < o2.getRank() ? 1 : 0);
            } else {
                Lemma lemma = lemmaRepo.findLemmaByName(lemmaWord,siteRepo.findBySiteUrl(site));
                List<Index> indexList1 = indexRepo.findIndex4Lemma(lemma);
                indexList.retainAll(indexList1);
                System.out.println(indexList);
            }
        });
        SearchResponse response = new SearchResponse();
        return response;

//        lemmaWord = getLemmaWord(query);
//        List<SiteEntity> siteEntities = new ArrayList<>();
//        if (site.matches("All")) {
//            siteEntities = siteRepo.findAll();
//        } else {
//            SiteEntity siteEntity = siteRepo.findBySiteUrl(site);
//            siteEntities.add(siteEntity);
//        }
//        SearchResponse response = new SearchResponse();
//        List<SearchResponseData> responseData = new ArrayList<>();
//        setResponse(siteEntities, lemmaWord, responseData, response);
//        return response;
    }

    private void setResponse(List<SiteEntity> siteEntities, String lemma2find, List<SearchResponseData> responseData, SearchResponse response) {
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
                ///
                String str = index.getPage_id().getContent();
                HashSet<String> allwrd = getAllwords(str);
                List<String> reverseLemmaList = allwrd.stream().filter(s -> s.substring(0,3).equals(lemma2find.substring(0,3))).filter(this::isListHasWord).toList();
                // Найти совпадения
                boolean addresults = false;
                StringBuilder snippet = new StringBuilder();
                addresults = createSnippet(reverseLemmaList, str, snippet, addresults);
                if (addresults) {
                    data.setRelevance(Float.toString(index.getRank()));
                    data.setSnippet(snippet.toString());
                    responseData.add(data);
                    response.setResult(true);
                    response.setCount(response.getCount()+1);
                    response.setData(responseData);
                }
                ///
            });
        });
    }

    private static boolean createSnippet(List<String> reverseLemmaList, String str, StringBuilder snippet, boolean addresults) {
        for (String wordFromLemma : reverseLemmaList){
            Pattern pattern = Pattern.compile(wordFromLemma, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.MULTILINE);
            Matcher matcher = pattern.matcher(str.toLowerCase());
            // Найти фрагмент текста, в котором находятся совпадения
            while (matcher.find()) {
                int start = matcher.start();
                int end = matcher.end();
                try {
                    int maxLengthText = str.length();
                    int startPos = (start - 75) < 0 ? 0 : (start - 75);
                    int endPos = (end + 75) > maxLengthText ? maxLengthText : (end + 75);
                    String fragment = str.substring(startPos, endPos);
                    String fragment2 = StringUtils.replaceIgnoreCase(fragment,wordFromLemma,"<b>"+wordFromLemma+"</b>");
                    snippet.append("<p>"+fragment2+"</p>");
                    addresults = true;
                } catch (Exception e) {
                    System.out.println(e.getLocalizedMessage());
                }
                break;
                // Вывести фрагмент текста
                //System.out.println(fragment2);
            }

        }
        return addresults;
    }

    @Override
    public List<SiteEntity> findAllsites() {
        return siteRepo.findAll();
    }
}
