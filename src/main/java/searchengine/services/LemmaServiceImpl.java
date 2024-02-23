package searchengine.services;

import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.ListUtils;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.english.EnglishLuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.model.Page;
import searchengine.model.SiteEntity;
import searchengine.repositories.indexRepo;
import searchengine.repositories.lemmaRepo;
import searchengine.repositories.pageRepo;
import searchengine.repositories.siteRepo;
import searchengine.services.interfaces.LemmaDictService;
import searchengine.services.interfaces.LemmaService;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveAction;
import java.util.function.Function;
import java.util.stream.Collectors;

@Log4j2
@Service
public class LemmaServiceImpl implements LemmaService {
    private final pageRepo pageRepo;
    private final lemmaRepo lemmaRepo;
    private final indexRepo indexRepo;
    private final siteRepo siteRepo;
    private ForkJoinPool forkJoinPool;
    private Map<String, Long> wordLemmasCount;
    private final ConcurrentHashMap<Page, Map<String, Long>> wordLemmasCount4page;
    private final LemmaDictService lemmaDictService;

    @Autowired
    public LemmaServiceImpl(lemmaRepo lemmaRepo, pageRepo pageRepo, indexRepo indexRepo, siteRepo siteRepo, LemmaDictService lemmaDictService) {
        this.lemmaRepo = lemmaRepo;
        this.pageRepo = pageRepo;
        this.indexRepo = indexRepo;
        this.siteRepo = siteRepo;
        this.lemmaDictService = lemmaDictService;
        this.wordLemmasCount4page = new ConcurrentHashMap<>();
    }

    @Override
    public void savePagesLemma() throws IOException {
        ConcurrentHashMap<Integer, ConcurrentHashMap<String, Integer>> hashMap0 = new ConcurrentHashMap<>();
        ///////////lemma////////////
        LuceneMorphology ruMorphology = new RussianLuceneMorphology();
        LuceneMorphology engMorphology = new EnglishLuceneMorphology();
        //List<Page> pages = pageRepo.findAll();
        Optional<SiteEntity> site = siteRepo.findById(1);
        List<Page> pages = pageRepo.findBySiteUrl(site.get());
        int availableProcessosrs = Runtime.getRuntime().availableProcessors();
        if (forkJoinPool == null || forkJoinPool.getActiveThreadCount() == 0) {
            forkJoinPool = new ForkJoinPool(availableProcessosrs);
        }
        //LemmaTask lemmaTask = new LemmaTask(pages, lemmaRepo, indexRepo, ruMorphology, engMorphology);
        //forkJoinPool.invoke(lemmaTask);
        RecursiveAction action = new RecursiveAction() {
            private volatile List<List<Page>> lists;

            @Override
            protected void compute() {
                int parallelCount = ForkJoinTask.getPool().getParallelism();
                List<RecursiveAction> lemmaTasks = new ArrayList<>();
                lists = ListUtils.partition(pages, parallelCount);
                for (List<Page> list : lists) {
                    RecursiveAction action1 = new RecursiveAction() {
                        @Override
                        protected void compute() {
                            list.forEach(page -> {
                                if (page != null && !page.getContent().isEmpty()) {
                                    String pageText = page.getContent();
                                    try {
                                        wordLemmasCount = Arrays.stream(pageText
                                                        .split("\\p{Blank}+"))
                                                .map(String::trim).map(String::toLowerCase)
                                                .filter(s -> s.matches("[a-zA-Zа-яА-Я]+"))
                                                .filter(s -> s.length() > 2)
                                                .map(word -> lemmaWord(word))
                                                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
                                        System.out.println("Thread.currentThread().getName()" + Thread.currentThread().getName() + "list.size = " + list.size() + lists.indexOf(list));
                                    } catch (RuntimeException e) {
                                        log.error(e.getLocalizedMessage());
                                    }
                                    wordLemmasCount4page.put(page, wordLemmasCount);
                                }
                            });
                        }
                    };
                    action1.fork();
                    lemmaTasks.add(action1);
                }
                lemmaTasks.forEach(ForkJoinTask::join);
            }

            private String lemmaWord(String word) {
                try {
                    List<String> stringList = ruMorphology.getMorphInfo(word);
                    return stringList.get(0).substring(0, stringList.get(0).indexOf("|"));
                } catch (RuntimeException e) {
                    try {
                        List<String> engStringList = engMorphology.getMorphInfo(word);
                        return engStringList.get(0).substring(0, engStringList.get(0).indexOf("|"));
                    } catch (RuntimeException e1) {
                        log.info("2 Ошибка при обработке слова " + word + " " + e.getLocalizedMessage() + e.getStackTrace());
                    }
                }
                return null;
            }
        };

        ForkJoinPool.commonPool().invoke(action);
        System.out.println("1: Thread.currentThread().getName()" + Thread.currentThread().getName() + "; ForkJoinPool.commonPool().getRunningThreadCount() = " + ForkJoinPool.commonPool().getRunningThreadCount());

        //wordLemmasCount4page.forEach((integer, stringLongMap) -> stringLongMap.forEach((s, aLong) -> lemmaDictService.fillLemmaDict(s,integer,aLong)));
        //wordLemmasCount4page.entrySet().parallelStream().forEach(pageMapEntry -> pageMapEntry.getValue().entrySet().parallelStream().forEach(stringLongEntry -> lemmaDictService.fillLemmaDict(stringLongEntry.getKey(),pageMapEntry.getKey(),stringLongEntry.getValue())));
        RecursiveAction save2db = new RecursiveAction() {

            private volatile List<List<ConcurrentHashMap.Entry<Page, Map<String, Long>>>> lists;

            @Override
            protected void compute() {
                List<RecursiveAction> lemmaTasks = new ArrayList<>();
                int parallelCount = ForkJoinTask.getPool().getParallelism();
                lists = ListUtils.partition(wordLemmasCount4page.entrySet().stream().toList(), parallelCount);
                lists.forEach(entries -> {
                    RecursiveAction action1 = new RecursiveAction() {
                        @Override
                        protected void compute() {
                            System.out.println("1.2: Thread name: " + Thread.currentThread().getName() + ", entries size = " + entries.size());
                            entries.forEach(integerMapEntry -> integerMapEntry.getValue().forEach((s, aLong) ->
                                    {
                                        try {
                                            synchronized (lemmaDictService) {
                                                lemmaDictService.fillLemmaDict(s, integerMapEntry.getKey(), aLong);
                                            }
                                        } catch (Exception e) {
                                            System.out.println(e.getLocalizedMessage());
                                        }
                                    }
                            ));
                        }
                    };
                    action1.fork();
                    lemmaTasks.add(action1);
                });
                ForkJoinTask.invokeAll(lemmaTasks);
            }
        };
        ForkJoinPool.commonPool().invoke(save2db);
        System.out.println("2: Thread.currentThread().getName()" + Thread.currentThread().getName() + "; ForkJoinPool.commonPool().getRunningThreadCount() = " + ForkJoinPool.commonPool().getRunningThreadCount());
    }
}
