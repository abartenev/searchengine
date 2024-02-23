package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.english.EnglishLuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.springframework.stereotype.Service;
import searchengine.dto.searchinfo.SearchResponse;
import searchengine.dto.searchinfo.SearchResponseData;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.SiteEntity;
import searchengine.repositories.indexRepo;
import searchengine.repositories.lemmaRepo;
import searchengine.repositories.siteRepo;
import searchengine.services.interfaces.SearchService;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.RecursiveTask;
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
    private HashSet<String> lemmaWords;
    private volatile List<SearchResponseData> responseData;
    private SearchResponse response;
    private String currentQuery;
    private Iterator<List<SearchResponseData>> dataIterator;

    public HashSet<String> getLemmaWords(String pageContent,HashSet<String> pageLemmas){
        HashSet<String> hashSet = new HashSet<>();
        if (pageLemmas == null) {
            hashSet.addAll(Arrays
                    .stream(pageContent.split("\\p{Blank}+"))
                    .map(String::trim)
                    .map(String::toLowerCase)
                    .filter(s -> s.matches("[a-zA-Zа-яА-Я]+"))
                    .filter(s -> s.length() > 2)
                    .map(this::getLemmaWord)
                    .collect(Collectors.toSet()));
        } else {
            hashSet.addAll(
                    Arrays.asList(pageContent.split("\\p{Blank}+"))
                            .parallelStream()
                            .map(String::trim)
                            .filter(s -> s.matches("[a-zA-Zа-яА-Я]+"))
                            .filter(s -> s.length() > 2)
                            .filter(this::substrListHasWord)
                            .collect(Collectors.toSet())
            );
        }
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
        return lemmaWords.contains(lemmaWordLocal);//lemmaWordLocal.equals(lemmaWord);
    }

    public boolean substrListHasWord(String word2check) {

        for (String word : lemmaWords) {
            if (word2check.length()>3 && word.length()>3 && word2check.toLowerCase().startsWith(word.substring(0, 4))) {
                return true;
            }
        }
        return false;
    }

    List<Index> indexByLemma(String siteName,String lemmaName) {
        List<Index> listIndex = new ArrayList<>();
        if (siteName.matches("All")) {
            List<Lemma> lemmaList = lemmaRepo.getLemmasByName(lemmaName);
            lemmaList.forEach(lemma -> {
                listIndex.addAll(indexRepo.findIndex4Lemma(lemma));
                //indexList.sort((o1, o2) -> o1.getRank() < o2.getRank() ? 1 : 0);
            });
        } else {
            Lemma lemma = lemmaRepo.findLemmaByNameAndSite(lemmaName,siteRepo.findBySiteUrl(siteName));
            listIndex.addAll(indexRepo.findIndex4Lemma(lemma));
        }
        return listIndex;
    }

    @Override
    public SearchResponse getSearchResults(String site, String query, Integer limit) {
        try {
            if (dataIterator == null || currentQuery == null || !currentQuery.equals(query)) {
                currentQuery = query;
                response = new SearchResponse();
                dataIterator = null;
                responseData = null;
                indexList = null;
                lemmaWords = getLemmaWords(query, null);
                lemmaWords.forEach(lemmaWord -> {
                    if (indexList == null || indexList.isEmpty()) {
                        indexList = indexByLemma(site, lemmaWord);
                        System.out.println(indexList.size());
                    } else {
                        List<Index> indexList1 = indexByLemma(site, lemmaWord);
                        HashSet<Page> pagesNextLemma = new HashSet<>();
                        indexList1.forEach(index -> pagesNextLemma.add(index.getPage_id()));
                        indexList.removeIf(index2 -> !pagesNextLemma.contains(index2.getPage_id()));
                        System.out.println(indexList.size());
                    }
                });
                makeResponse(response);
                dataIterator = ListUtils.partition(responseData, limit).iterator();
                if (dataIterator.hasNext()) {
                    response.setData(dataIterator.next());
                }
            } else {
                if (dataIterator.hasNext()) {
                    response.setData(dataIterator.next());
                }
            }
        } catch (Exception e) {
            if (response == null) {
                response = new SearchResponse();
                response.setError(e.getLocalizedMessage());
            }
        }
        return response;
    }

    private void makeResponse(SearchResponse response){
        indexList.sort((o1, o2) -> o1.getRank() > o2.getRank() ? 1 : 0);
        responseData = new ArrayList<>();
            RecursiveAction task1 = new RecursiveAction() {
                private volatile List<List<Index>> lists;
                @Override
                protected void compute() {
                    int parallelCount = ForkJoinTask.getPool().getParallelism();
                    List<RecursiveAction> lemmaTasks = new ArrayList<>();
                    lists = ListUtils.partition(indexList, parallelCount);
                    lists.forEach(indexList ->{
                        RecursiveAction subtask1 = new RecursiveAction() {
                            @Override
                            protected void compute() {
                                System.out.println("Search Thread.currentThread().getName()" + Thread.currentThread().getName());
                                indexList.forEach(index -> {
                                    String content = index.getPage_id().getContent();
                                    HashSet<String> reversedWords = getLemmaWords(content,lemmaWords);
                                    System.out.println(reversedWords.size());
                                    // Найти совпадения
                                    boolean addresults = false;
                                    StringBuilder snippet = new StringBuilder();
                                    addresults = createSnippet(reversedWords.stream().toList(), content, snippet, addresults);
                                    if (addresults) {
                                        SearchResponseData data = new SearchResponseData();
                                        data.setSite(index.getPage_id().getSite_Entity_id().getUrl());
                                        data.setSiteName(index.getPage_id().getSite_Entity_id().getName());
                                        data.setUrl(index.getPage_id().getPath());
                                        data.setTitle(index.getPage_id().getPath());
                                        data.setRelevance(Float.toString(index.getRank()));
                                        data.setSnippet(snippet.toString());
                                        responseData.add(data);
                                        response.setResult(true);
                                        response.setCount(response.getCount() + 1);
                                    } else {
                                        if (responseData.size() == 0) {
                                            response.setError("По данному поисковому запросу ничего не найдено.");
                                        }
                                    }
                                });
                            }
                        };
                        subtask1.fork();
                        subtask1.join();
                    });
                }
            };
            ForkJoinPool.commonPool().invoke(task1);
            response.setData(responseData);
            ForkJoinPool.commonPool().shutdownNow();
    }

    private static boolean createSnippet(List<String> reverseLemmaList, String str, StringBuilder snippet, boolean addresults) {
        String fragment2 = null;
        List<String> replString = reverseLemmaList.stream().map(s -> "<b>"+s+"</b>").collect(Collectors.toList());
        String str2 = StringUtils.replaceEach(str,reverseLemmaList.toArray(new String[0]),replString.toArray(new String[0]));
//        String patterString = "\\b(" + StringUtils.join(reverseLemmaList,"|") + ")\\b";
//        Pattern pattern = Pattern.compile(patterString);
//        Matcher matcher = pattern.matcher(str2);
            // Найти фрагмент текста, в котором находятся совпадения
        for (String wordFromLemma : reverseLemmaList){
            Pattern pattern = Pattern.compile(wordFromLemma, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.MULTILINE);
            Matcher matcher = pattern.matcher(str2);
            while (matcher.find()) {
                int start = matcher.start();
                int end = matcher.end();
                try {
                    int maxLengthText = str2.length();
                    int startPos = (start - 75) < 0 ? 0 : (start - 75);
                    int endPos = (end + 75) > maxLengthText ? maxLengthText : (end + 75);
                    String fragment = str2.substring(startPos, endPos);
                    //fragment2 = StringUtils.replaceIgnoreCase(fragment,wordFromLemma,"<b>"+wordFromLemma+"</b>");
                    snippet.append("<p>"+fragment+"</p>");
                    addresults = true;
                } catch (Exception e) {
                    System.out.println(e.getLocalizedMessage());
                }
                break;
                // Вывести фрагмент текста
                //System.out.println(fragment2);
            }
            //break;
        }
        return addresults;
    }

    @Override
    public List<SiteEntity> findAllsites() {
        return siteRepo.findAll();
    }
}
