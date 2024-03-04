package searchengine.services;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
//@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {
    private final siteRepo siteRepo;
    private final lemmaRepo lemmaRepo;
    private final indexRepo indexRepo;
    private Set<Page> pageList;
    private HashSet<String> lemmaWords;
    private volatile List<SearchResponseData> responseData;
    private SearchResponse response;
    private String currentQuery;
    private String currentSite;
    private Iterator<List<SearchResponseData>> dataIterator;
    private final LuceneMorphology ruMorphology;
    private final LuceneMorphology engMorphology;

    public SearchServiceImpl(searchengine.repositories.siteRepo siteRepo, searchengine.repositories.lemmaRepo lemmaRepo, searchengine.repositories.indexRepo indexRepo) throws IOException {
        this.siteRepo = siteRepo;
        this.lemmaRepo = lemmaRepo;
        this.indexRepo = indexRepo;
        this.ruMorphology = new RussianLuceneMorphology();
        this.engMorphology = new EnglishLuceneMorphology();
    }

    public HashSet<String> getLemmaWords(String pageContent, HashSet<String> pageLemmas) {
        HashSet<String> hashSet = new HashSet<>();
        if (pageLemmas == null) {
            hashSet.addAll(
                    Arrays.asList(pageContent.split("\\p{Blank}+"))
                            .parallelStream()
                            .map(String::trim)
                            .map(String::toLowerCase)
                            .filter(s -> s.matches("[a-zA-Zа-яА-ЯёЁ]+"))
                            .filter(s -> s.length() > 2)
                            .map(s -> s.replace('ё','е'))
                            .map(s -> s.replace('Ё','Е'))
                            .map(this::getLemmaWord)
                            .collect(Collectors.toSet())
            );
        } else {
            hashSet.addAll(
                    Arrays.asList(pageContent.split("\\p{Blank}+"))
                            .parallelStream()
                            .map(String::trim)
                            .filter(s -> s.matches("[a-zA-Zа-яА-ЯёЁ]+"))
                            .filter(s -> s.length() > 2)
                            .map(s -> s.replace('ё','е'))
                            .map(s -> s.replace('Ё','Е'))
                            .filter(this::substrListHasWord)
                            .collect(Collectors.toSet())
            );
        }
        return hashSet;
    }

    public String getLemmaWord(String query) {
        try {
            List<String> res = ruMorphology.getMorphInfo(query);
            System.out.println(res.get(0));
            return res.get(0).substring(0, res.get(0).indexOf("|"));
        } catch (RuntimeException e) {
            try {
                List<String> res = engMorphology.getMorphInfo(query);
                System.out.println("Eng = " + res.get(0));
                return res.get(0).substring(0, res.get(0).indexOf("|"));
            } catch (RuntimeException e1) {
                System.out.println("2 Ошибка при обработке слова " + query + " " + e.getLocalizedMessage());
            }
        }
        return null;
    }

    public boolean substrListHasWord(String word2check) {

        return lemmaWords.parallelStream()
                //.filter(s ->word2check.length() >= 3 && s.length() >= 3 && word2check.toLowerCase().startsWith(s.substring(0, 2)))
                .anyMatch(s -> {
                    String lem = getLemmaWord(word2check.toLowerCase());
                    return lem != null && lem.equals(s);
                });
    }

    List<Index> indexByLemma(String siteName, String lemmaName) {
        List<Index> listIndex = new ArrayList<>();
        if (siteName.matches("All")) {
            List<Lemma> lemmaList = lemmaRepo.getLemmasByName(lemmaName);
            lemmaList.forEach(lemma -> {
                listIndex.addAll(indexRepo.findIndex4Lemma(lemma));
            });
        } else {
            Lemma lemma = lemmaRepo.findLemmaByNameAndSite(lemmaName, siteRepo.findBySiteUrl(siteName));
            listIndex.addAll(indexRepo.findIndex4Lemma(lemma));
        }
        return listIndex;
    }

    @Override
    public SearchResponse getSearchResults(String site, String query, Integer limit, Integer offset) {
        boolean needNewRequest = dataIterator == null
                || currentQuery == null || currentSite == null || !currentSite.equals(site)
                || !currentQuery.equals(query);
        try {
            if (needNewRequest) {
                currentQuery = query;
                currentSite = site;
                dataIterator = null;
                responseData = null;
                pageList = null;
                response = new SearchResponse();
                lemmaWords = getLemmaWords(StringUtils.replaceIgnoreCase(query,"ё","е"), null);
                Set<Set<Page>> sets = lemmaWords.parallelStream()
                        .map(s -> indexByLemma(site, s))
                        .sorted(Comparator.comparing(indices -> indices.size()))
                        .collect(Collectors.toList())
                        .parallelStream()
                        .map(indices -> indices.parallelStream()
                                .map(Index::getPage_id)
                                .collect(Collectors.toSet())).collect(Collectors.toSet());
                Set<Page> res = new HashSet<>();
                for (Set<Page> p : sets) {
                    boolean allPagesNotEmpty = !sets.parallelStream().anyMatch(pages -> pages.size() == 0);
                    boolean startRetain = res.size() == 0 || res.isEmpty();
                    if (startRetain && allPagesNotEmpty) {
                        res.addAll(p);
                    } else {
                        res.retainAll(p);
                    }
                }
                if (res.size() > 0) {
                    List<SiteEntity> entities = siteRepo.findBySites(site).stream().toList();
                    //List<Page> pageList = res.parallelStream().filter(page -> page.getId().intValue() == 1113).toList();
                    pageList = indexRepo.findIndex4Lemmas(
                            lemmaRepo.getLemmasByNames(
                                    lemmaWords.parallelStream().map(s -> s.toLowerCase().replace('ё','е')).toList()
                                    , entities
                            )
                            , res.stream().toList()
                    ).parallelStream()
                            .flatMap(index -> Stream.of(index.getPage_id()))
                            //.filter(page -> page.getId().intValue() == 952)
                            .collect(Collectors.toSet());
                    response.setError(null);
                    makeResponse(response);
                    dataIterator = ListUtils.partition(responseData, limit).iterator();
                    if (dataIterator.hasNext()) {
                        response.setData(dataIterator.next());
                    }
                } else {
                    if (response == null || !response.isResult()) {
                        response = new SearchResponse();
                        response.setError("На выбранных ресурсах нет такой информации");
                    }
                }
            } else {
                if (offset == 0) {
                    dataIterator = ListUtils.partition(responseData, limit).iterator();
                    response.setData(dataIterator.next());
                }
                if (offset == limit && dataIterator.hasNext()) {
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

    private void makeResponse(SearchResponse response) {
        responseData = new ArrayList<>();
        RecursiveAction task1 = new RecursiveAction() {
            private volatile List<List<Page>> lists;

            @Override
            protected void compute() {
                int parallelCount = ForkJoinTask.getPool().getParallelism();
                List<RecursiveAction> lemmaTasks = new ArrayList<>();
                lists = ListUtils.partition(pageList.stream().toList(), parallelCount);
                lists.forEach(idx -> {
                    RecursiveAction subtask1 = new RecursiveAction() {
                        @Override
                        protected void compute() {
                            System.out.println("Search Thread.currentThread().getName()" + Thread.currentThread().getName());
                            idx.forEach(page -> {
                                String content = StringUtils.replaceIgnoreCase(page.getContent(),"ё","е");
                                HashSet<String> reversedWords = getLemmaWords(content, lemmaWords);
                                // Найти совпадения
                                boolean addresults = false;
                                StringBuilder snippet = new StringBuilder();
                                addresults = createSnippet(reversedWords.parallelStream().toList(), content, snippet, addresults);
                                if (addresults) {
                                    SearchResponseData data = new SearchResponseData();
                                    data.setSite("");
                                    data.setSiteName(page.getSite_Entity_id().getName());
                                    data.setUri(page.getPath());
                                    data.setTitle(page.getPath());
                                    //data.setRelevance(Float.toString(index.getRank()));
                                    data.setSnippet(snippet.toString());
                                    responseData.add(data);
                                    response.setResult(true);
                                    response.setCount(response.getCount() + 1);
                                } else {
                                    if (responseData.size() == 0) {
                                        response.setError("На выбранных ресурсах нет такой информации.");
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
        response.setData(responseData.stream().distinct().toList());
        ForkJoinPool.commonPool().shutdownNow();
    }

    private static boolean createSnippet(List<String> reverseLemmaList, String str, StringBuilder snippet, boolean addresults) {
        String fragment2 = null;
        List<String> replString = reverseLemmaList.parallelStream().map(s -> "<b>" + s + "</b>").collect(Collectors.toList());
        String str2 = StringUtils.replaceEach(str, reverseLemmaList.toArray(new String[0]), replString.toArray(new String[0]));
        // Найти фрагмент текста, в котором находятся совпадения
        for (String wordFromLemma : reverseLemmaList) {
            Pattern pattern = Pattern.compile("<b>" + wordFromLemma + "</b>", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.MULTILINE);
            Matcher matcher = pattern.matcher(str2);
            while (matcher.find()) {
                int start = matcher.start();
                int end = matcher.end();
                try {
                    int maxLengthText = str2.length();
                    int startPos = (start - 75) < 0 ? 0 : (start - 75);
                    int endPos = (end + 75) > maxLengthText ? maxLengthText : (end + 75);
                    String fragment = str2.substring(startPos, endPos);
                    snippet.append("<p>" + fragment + "</p>");
                    addresults = true;
                } catch (Exception e) {
                    System.out.println(e.getLocalizedMessage());
                }
                break;
            }
        }
        return addresults;
    }

    @Override
    public List<SiteEntity> findAllsites() {
        return siteRepo.findAll();
    }
}
