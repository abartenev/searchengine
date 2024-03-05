package searchengine.services;

import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.english.EnglishLuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.SiteEntity;
import searchengine.repositories.indexRepo;
import searchengine.repositories.lemmaRepo;
import searchengine.repositories.pageRepo;
import searchengine.repositories.siteRepo;
import searchengine.services.interfaces.LemmaDictService;
import searchengine.services.interfaces.LemmaService;

import javax.persistence.LockModeType;
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
    private final PlatformTransactionManager transactionManager;

    @Autowired
    public LemmaServiceImpl(lemmaRepo lemmaRepo, pageRepo pageRepo, indexRepo indexRepo
            , siteRepo siteRepo, LemmaDictService lemmaDictService,PlatformTransactionManager manager) {
        this.lemmaRepo = lemmaRepo;
        this.pageRepo = pageRepo;
        this.indexRepo = indexRepo;
        this.siteRepo = siteRepo;
        this.lemmaDictService = lemmaDictService;
        this.wordLemmasCount4page = new ConcurrentHashMap<>();
        this.transactionManager =  manager;
    }

    @Override
    public void savePagesLemma() throws IOException {
        ///////////lemma////////////
        LuceneMorphology ruMorphology = new RussianLuceneMorphology();
        LuceneMorphology engMorphology = new EnglishLuceneMorphology();
//        List<Page> pages = pageRepo.findAll();
        List<Page> pages = pageRepo.findBySiteUrl(siteRepo.findById(15017630).get());
        int availableProcessosrs = Runtime.getRuntime().availableProcessors();
        if (forkJoinPool == null || forkJoinPool.getActiveThreadCount() == 0) {
            forkJoinPool = new ForkJoinPool(availableProcessosrs);
        }
        RecursiveAction action = new RecursiveAction() {
            private volatile List<List<Page>> lists;

            public String lemmaWord(String word) {
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
                                        wordLemmasCount = Arrays.asList(pageText
                                                .split("\\p{Blank}+")).parallelStream()
                                                .map(s -> s.replaceAll("\\p{Punct}",""))
                                                .map(String::trim).map(String::toLowerCase)
                                                .filter(s -> s.matches("[a-zA-Zа-яА-ЯёЁ]+"))
                                                .filter(s -> s.length() > 2)
                                                .filter(s -> lemmaWord(s) != null)
                                                .map(word -> lemmaWord(word))
                                                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
                                        System.out.println("Thread.currentThread().getName()" + Thread.currentThread().getName() + "list.size = " + list.size() + lists.indexOf(list));
                                    } catch (RuntimeException e) {
                                        log.error(e.getLocalizedMessage() + " stack trace = " +
                                                Arrays.asList(e.getStackTrace()).parallelStream().skip(0).map(StackTraceElement::toString).reduce((s1, s2) -> s1 + "\n" + s2).get());
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
        };

        ForkJoinPool.commonPool().invoke(action);

        System.out.println("1: Thread.currentThread().getName()" + Thread.currentThread().getName() + "; ForkJoinPool.commonPool().getRunningThreadCount() = " + ForkJoinPool.commonPool().getRunningThreadCount());

        ConcurrentHashMap<String,Lemma> lemmaConcurrentHashMap = new ConcurrentHashMap<>();

        ConcurrentHashMap<String,Lemma> map = new ConcurrentHashMap<>();
        List<Index> indexList = new ArrayList<>();
        wordLemmasCount4page.forEach((page, stringLongMap) -> {
            stringLongMap.forEach((s, aLong) -> {
                String lemmaWord = StringUtils.replaceIgnoreCase(s,"ё","е");
                Lemma lemma = null;
                //lemma
                if (!map.containsKey(lemmaWord)){
                    lemma = new Lemma(lemmaWord,page.getSite_Entity_id(),aLong.intValue());
                    map.put(lemmaWord,lemma);
                } else {
                    lemma = map.get(lemmaWord);
                    lemma.setFrequency(lemma.getFrequency() + aLong.intValue());
                    map.put(lemmaWord,lemma);
                }
            });
        });
        List<Lemma> lemmaList = map.values().stream().toList();
        lemmaDictService.saveLemmas(lemmaList);

        RecursiveAction save2db = new RecursiveAction() {
            private volatile List<List<ConcurrentHashMap.Entry<Page, Map<String, Long>>>> lists;
            private volatile List<Lemma> lemmas;
            @Override
            protected void compute() {
                List<RecursiveAction> lemmaTasks = new ArrayList<>();
                lemmas = lemmaRepo.findAll();
                int parallelCount = ForkJoinTask.getPool().getParallelism();
                lists = ListUtils.partition(wordLemmasCount4page.entrySet().parallelStream().toList(), parallelCount);
                lists.forEach(entries -> {
                    RecursiveAction action1 = new RecursiveAction() {
                        @Override
                        protected void compute() {
                            System.out.println("1.2: Thread name: " + Thread.currentThread().getName() + ", entries size = " + entries.size());
                            entries.forEach(integerMapEntry -> integerMapEntry.getValue().forEach((s, aLong) ->
                                    {
                                        try {
                                            //synchronized (lemmaDictService) {
                                            String lemmaWord = StringUtils.replaceIgnoreCase(s,"ё","е");
                                                lemmaDictService.saveIndexes(lemmas, lemmaWord, integerMapEntry.getKey(), aLong);
                                            //}
                                        } catch (Exception e) {
                                            System.out.println(e.getLocalizedMessage());
                                            log.error(s + " error = " + e.getLocalizedMessage());
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



//        RecursiveAction save2db = new RecursiveAction() {
//            private volatile List<List<ConcurrentHashMap.Entry<Page, Map<String, Long>>>> lists;
//            @Override
//            protected void compute() {
//                List<RecursiveAction> lemmaTasks = new ArrayList<>();
//                int parallelCount = ForkJoinTask.getPool().getParallelism();
//                lists = ListUtils.partition(wordLemmasCount4page.entrySet().parallelStream().toList(), parallelCount);
//                lists.forEach(entries -> {
//                    RecursiveAction action1 = new RecursiveAction() {
//                        @Override
//                        protected void compute() {
//                            System.out.println("1.2: Thread name: " + Thread.currentThread().getName() + ", entries size = " + entries.size());
//                            entries.forEach(integerMapEntry -> integerMapEntry.getValue().forEach((s, aLong) ->
//                                    {
//                                        try {
//                                            //synchronized (lemmaDictService) {
//                                                lemmaDictService.fillLemmaDict(s, integerMapEntry.getKey(), aLong);
//                                            //}
//                                        } catch (Exception e) {
//                                            System.out.println(e.getLocalizedMessage());
//                                        }
//                                    }
//                            ));
//                        }
//                    };
//                    action1.fork();
//                    lemmaTasks.add(action1);
//                });
//                ForkJoinTask.invokeAll(lemmaTasks);
//            }
//        };
//        ForkJoinPool.commonPool().invoke(save2db);
        System.out.println("2: Thread.currentThread().getName()" + Thread.currentThread().getName() + "; ForkJoinPool.commonPool().getRunningThreadCount() = " + ForkJoinPool.commonPool().getRunningThreadCount());
    }
}
