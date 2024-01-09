package searchengine.source;

import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.ListUtils;
import org.apache.lucene.morphology.LuceneMorphology;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.repositories.indexRepo;
import searchengine.repositories.lemmaRepo;

import javax.transaction.Transactional;
import java.util.*;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveAction;
import java.util.stream.Collectors;


@Transactional
@Log4j2
public class LemmaTask extends RecursiveAction {
    private final List<Page> pages;
    private final lemmaRepo lemmaRepo;
    private final indexRepo indexRepo;
    private final LuceneMorphology ruMorphology;
    private final LuceneMorphology engMorphology;
    private final HashMap<String, Lemma> hashMap;
    private Page page;

    public LemmaTask(List<Page> pages, lemmaRepo lemmaRepo, indexRepo indexRepo, LuceneMorphology ruMorphology, LuceneMorphology engMorphology) {
        this.pages = pages;
        this.lemmaRepo = lemmaRepo;
        this.indexRepo = indexRepo;
        this.ruMorphology = ruMorphology;
        this.engMorphology = engMorphology;
        this.hashMap = new HashMap<>();
    }

    public LemmaTask(List<Page> pages, lemmaRepo lemmaRepo, indexRepo indexRepo, LuceneMorphology ruMorphology, LuceneMorphology engMorphology, Page page, HashMap<String, Lemma> hashMap1) {
        this.pages = pages;
        this.lemmaRepo = lemmaRepo;
        this.indexRepo = indexRepo;
        this.ruMorphology = ruMorphology;
        this.engMorphology = engMorphology;
        this.page = page;
        this.hashMap = hashMap1;
    }

    @Override
    protected void compute() {
        int parallelCount = ForkJoinTask.getPool().getParallelism();
        if (page == null) {
            List<LemmaTask> lemmaTasks = new ArrayList<>();
            List<List<Page>> lists = ListUtils.partition(pages,parallelCount);
            for (List<Page> list : lists) {
                LemmaTask task = new LemmaTask(list, lemmaRepo, indexRepo, ruMorphology, engMorphology, new Page(), hashMap);
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

    private void pageProcessing(Page page) {
        log.info("Поток получения лемм" + Thread.currentThread().getName() + ". Страница = " + page.getPath());
        log.info("Леммы ForkJoinTask.getPool().getActiveThreadCount() " + ForkJoinTask.getPool().getActiveThreadCount());
        if (page != null && !page.getContent().isEmpty()) {
            HashSet<String> hashSet = new HashSet<>();
            String pageText = page.getContent();
            hashSet.addAll(Arrays.stream(pageText.split("\\p{Blank}+")).filter(s -> s.matches("[a-zA-Zа-яА-Я]+")).filter(s -> s.length() > 2).map(String::trim).map(String::toLowerCase).collect(Collectors.toSet()));
            for (String word : hashSet) {
                try {
                    List<String> stringList = ruMorphology.getMorphInfo(word);
                    System.out.println(stringList);
                    stringList.forEach(s -> {
                        lemmaSave(page, s);
                    });
                } catch (RuntimeException e) {
                    try {
                        List<String> engStringList = engMorphology.getMorphInfo(word);
                        System.out.println(engStringList);
                        engStringList.forEach(s -> {
                            lemmaSave(page, s);
                        });
                    } catch (RuntimeException e1) {
                        log.info("2 Ошибка при обработке слова " + word + " " + e.getLocalizedMessage());
                    }
                }
                //break;
            }
        }
    }

    private void lemmaSave(Page page, String s) {
        String lemmaString = s.substring(0, s.indexOf("|"));
        Lemma lemma = lemmaRepo.findLemmaByName(lemmaString, page.getSite_Entity_id());
        if (lemma == null) {
            lemma = new Lemma();
            lemma.setSite_id(page.getSite_Entity_id());
            lemma.setFrequency(1);
            lemma.setLemma(lemmaString);
            lemmaRepo.save(lemma);
        }
        Index index = indexRepo.findIndex4LemmaNPage(lemma, page);
        if (index == null) {
            index = new Index();
            index.setPage_id(page);
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

}
