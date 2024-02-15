package searchengine.services;

import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.ListUtils;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.english.EnglishLuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.SiteEntity;
import searchengine.repositories.indexRepo;
import searchengine.repositories.lemmaRepo;
import searchengine.repositories.pageRepo;
import searchengine.repositories.siteRepo;
import searchengine.services.interfaces.LemmaService;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveAction;
import java.util.stream.Collectors;

@Log4j2
@Service
public class LemmaServiceImpl implements LemmaService {
    private final pageRepo pageRepo;
    private final lemmaRepo lemmaRepo;
    private final indexRepo indexRepo;
    private final siteRepo siteRepo;
    private ForkJoinPool forkJoinPool;

    @Autowired
    public LemmaServiceImpl(lemmaRepo lemmaRepo, pageRepo pageRepo, indexRepo indexRepo, siteRepo siteRepo) {
        this.lemmaRepo = lemmaRepo;
        this.pageRepo = pageRepo;
        this.indexRepo = indexRepo;
        this.siteRepo = siteRepo;
    }

    @Override
    public void savePagesLemma() throws IOException {
        ConcurrentHashMap<Page,ConcurrentHashMap<String,Integer>> hashMap0 = new ConcurrentHashMap<>();
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
                                    HashSet<String> hashSet = new HashSet<>();
                                    //Hashtable<String, Integer> lemmasOnPage = new Hashtable<>();
                                    String pageText = page.getContent();
                                    hashSet.addAll(Arrays.stream(pageText.split("\\p{Blank}+")).map(String::trim).map(String::toLowerCase).filter(s -> s.matches("[a-zA-Zа-яА-Я]+")).filter(s -> s.length() > 2).collect(Collectors.toSet()));
                                    System.out.println("Thread.currentThread().getName()" + Thread.currentThread().getName() + "list.size = " + list.size() + " hashSet.size = " + hashSet.size() + "Curr list index = " + lists.indexOf(list));
                                    for (String word : hashSet) {
                                        try {
                                            List<String> stringList = ruMorphology.getMorphInfo(word);
                                            //System.out.println(stringList);
                                            stringList.forEach(s -> {
                                                String lemmaString = s.substring(0, s.indexOf("|"));
                                                ConcurrentHashMap<String,Integer> map = new ConcurrentHashMap<>();
                                                map.put(lemmaString,1);
                                                hashMap0.putIfAbsent(page,map);
                                                hashMap0.get(page).computeIfPresent(lemmaString,(s1, counter) -> counter++);
                                                //fillLemmaDict(s, page);
                                            });
                                        } catch (RuntimeException e) {
                                            try {
                                                List<String> engStringList = engMorphology.getMorphInfo(word);
                                                //System.out.println(engStringList);
                                                engStringList.forEach(s -> {
                                                    String lemmaString = s.substring(0, s.indexOf("|"));
                                                    ConcurrentHashMap<String,Integer> map = new ConcurrentHashMap<>();
                                                    map.put(lemmaString,1);
                                                    hashMap0.putIfAbsent(page,map);
                                                    hashMap0.get(page).computeIfPresent(lemmaString,(s1, counter) -> counter++);
                                                    //fillLemmaDict(s, page);
                                                });
                                            } catch (RuntimeException e1) {
                                                log.info("2 Ошибка при обработке слова " + word + " " + e.getLocalizedMessage() + e.getStackTrace());
                                            }
                                        }
                                    }
                                    //lemmaSave(page, lemmasOnPage);
                                }
                            });
                        }

                        @Transactional
                        private void fillLemmaDict(String s, Page p) {
                            String lemmaString = s.substring(0, s.indexOf("|"));
                            Lemma lemma = lemmaRepo.findLemmaByName(lemmaString, p.getSite_Entity_id());
                            if (lemma == null) {
                                lemma = new Lemma();
                                lemma.setSite_id(p.getSite_Entity_id());
                                lemma.setFrequency(1);
                                lemma.setLemma(lemmaString);
                                lemmaRepo.save(lemma);
                            } else {
                                lemma.setFrequency(lemma.getFrequency() + 1);
                                lemmaRepo.save(lemma);
                            }

                            Index index = indexRepo.findIndex4LemmaNPage(lemma, p);
                            if (index == null) {
                                index = new Index();
                                index.setPage_id(p);
                                index.setLemma_id(lemma);
                                index.setRank(1);
                                indexRepo.save(index);
                            } else {
                                index.setRank(index.getRank() + 1);
                                indexRepo.save(index);
                                lemma.setFrequency(lemma.getFrequency() + 1);
                                lemmaRepo.save(lemma);
                            }

                        }
                    };
                    action1.fork();
                    lemmaTasks.add(action1);
                }
                lemmaTasks.forEach(ForkJoinTask::join);
//                if (ForkJoinTask.getPool().getActiveThreadCount() == 1 /*&& lists.get(lists.size() - 1) == list*/
//                    ForkJoinTask.getPool().getRunningThreadCount()) {
//                    ForkJoinTask.getPool().shutdown();
//                    System.out.println("Last lemma get");
//                }
                //System.out.println("Thread=" + Thread.currentThread().getName() +"; getRunningThreadCount() = " + ForkJoinTask.getPool().getRunningThreadCount() + " sizelist=" + lists.size() + "indexList="+lists.);
            }
        };

        ForkJoinPool.commonPool().invoke(action);
        System.out.println("Thread.currentThread().getName()" + Thread.currentThread().getName() +"; ForkJoinPool.commonPool().getRunningThreadCount() = " + ForkJoinPool.commonPool().getRunningThreadCount());


////////////////////////////
    }
}
