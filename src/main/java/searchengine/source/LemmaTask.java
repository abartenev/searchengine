package searchengine.source;

import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.ListUtils;
import org.apache.lucene.morphology.LuceneMorphology;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.repositories.indexRepo;
import searchengine.repositories.lemmaRepo;

import java.util.*;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveAction;
import java.util.stream.Collectors;


@Log4j2
public class LemmaTask extends RecursiveAction {
    private final List<Page> pages;
    private final lemmaRepo lemmaRepo;
    private final indexRepo indexRepo;
    private final LuceneMorphology ruMorphology;
    private final LuceneMorphology engMorphology;
    private Page page;

    public LemmaTask(List<Page> pages, lemmaRepo lemmaRepo, indexRepo indexRepo, LuceneMorphology ruMorphology, LuceneMorphology engMorphology) {
        this.pages = pages;
        this.lemmaRepo = lemmaRepo;
        this.indexRepo = indexRepo;
        this.ruMorphology = ruMorphology;
        this.engMorphology = engMorphology;
    }

    public LemmaTask(List<Page> pages, lemmaRepo lemmaRepo, indexRepo indexRepo, LuceneMorphology ruMorphology, LuceneMorphology engMorphology, Page page) {
        this.pages = pages;
        this.lemmaRepo = lemmaRepo;
        this.indexRepo = indexRepo;
        this.ruMorphology = ruMorphology;
        this.engMorphology = engMorphology;
        this.page = page;
    }

    private void fillLemmaDict(String s, Page p, Hashtable<String, Integer> pageLemmaCounter) {
        String lemmaString = s.substring(0, s.indexOf("|"));
        if (!pageLemmaCounter.containsKey(lemmaString)) {
            pageLemmaCounter.put(lemmaString, 1);
        } else {
            pageLemmaCounter.put(lemmaString, pageLemmaCounter.get(lemmaString) + 1);
        }
    }

    @Override
    public void compute() {
        int parallelCount = ForkJoinTask.getPool().getParallelism();
        if (page == null) {
            List<LemmaTask> lemmaTasks = new ArrayList<>();
            List<List<Page>> lists = ListUtils.partition(pages, parallelCount);
            for (List<Page> list : lists) {
                LemmaTask task = new LemmaTask(list, lemmaRepo, indexRepo, ruMorphology, engMorphology, new Page());
                task.fork();
                lemmaTasks.add(task);
            }
            lemmaTasks.forEach(ForkJoinTask::join);
        } else {
            for (Page page : pages) {
                pageProcessing(page);
                if (ForkJoinTask.getPool().getActiveThreadCount() == 1 && pages.get(pages.size() - 1) == page) {
                    ForkJoinTask.getPool().shutdown();
                    log.info("Last lemma get");
                }
            }
        }
    }

    public void pageProcessing(Page page) {
        //log.info("Поток получения лемм" + Thread.currentThread().getName() + ". Страница = " + page.getPath());
        //log.info("Леммы ForkJoinTask.getPool().getActiveThreadCount() " + ForkJoinTask.getPool().getActiveThreadCount());

        if (page != null && !page.getContent().isEmpty()) {
            HashSet<String> hashSet = new HashSet<>();
            Hashtable<String, Integer> lemmasOnPage = new Hashtable<>();
            String pageText = page.getContent();
            hashSet.addAll(Arrays.stream(pageText.split("\\p{Blank}+")).map(String::trim).map(String::toLowerCase).filter(s -> s.matches("[a-zA-Zа-яА-Я]+")).filter(s -> s.length() > 2).collect(Collectors.toSet()));
            for (String word : hashSet) {
                try {
                    List<String> stringList = ruMorphology.getMorphInfo(word);
                    System.out.println(stringList);
                    stringList.forEach(s -> {
                        fillLemmaDict(s, page, lemmasOnPage);
                    });
                } catch (RuntimeException e) {
                    try {
                        List<String> engStringList = engMorphology.getMorphInfo(word);
                        System.out.println(engStringList);
                        engStringList.forEach(s -> {
                            fillLemmaDict(s, page, lemmasOnPage);
                        });
                    } catch (RuntimeException e1) {
                        log.info("2 Ошибка при обработке слова " + word + " " + e.getLocalizedMessage() + e.getStackTrace());
                    }
                }
            }
            lemmaSave(page, lemmasOnPage);
        }
    }

    public void lemmaSave(Page page, Hashtable<String, Integer> lemmaString) {
        lemmaString.forEach((lemmaWord, pageCounter) -> {
            addLemmaAndIndex(page, lemmaWord, pageCounter);
        });
    }

    public void addLemmaAndIndex(Page page, String s, Integer pageCounter) {
        //synchronized (lemmaRepo) {
            Lemma lemma = lemmaRepo.findLemmaByNameAndSite(s, page.getSite_Entity_id());
            if (lemma == null) {
                lemma = new Lemma();
                lemma.setSite_id(page.getSite_Entity_id());
                lemma.setFrequency(pageCounter);
                lemma.setLemma(s);
                lemmaRepo.save(lemma);
            }
            Index index = indexRepo.findIndex4LemmaNPage(lemma, page);
            if (index == null) {
                index = new Index();
                index.setPage_id(page);
                index.setLemma_id(lemma);
                index.setRank(pageCounter);
                indexRepo.save(index);
            } else {
                index.setRank(index.getRank() + pageCounter);
                indexRepo.save(index);
                lemma.setFrequency(lemma.getFrequency() + pageCounter);
                lemmaRepo.save(lemma);
            }
        //}
    }

}
