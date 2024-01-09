package searchengine.services;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.english.EnglishLuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Page;
import searchengine.repositories.indexRepo;
import searchengine.repositories.lemmaRepo;
import searchengine.repositories.pageRepo;
import searchengine.services.interfaces.LemmaService;
import searchengine.source.LemmaTask;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ForkJoinPool;

@Service
public class LemmaServiceImpl implements LemmaService {
    private final pageRepo pageRepo;
    private final lemmaRepo lemmaRepo;
    private final indexRepo indexRepo;
    private ForkJoinPool forkJoinPool;

    @Autowired
    public LemmaServiceImpl(lemmaRepo lemmaRepo, pageRepo pageRepo, indexRepo indexRepo) {
        this.lemmaRepo = lemmaRepo;
        this.pageRepo = pageRepo;
        this.indexRepo = indexRepo;
    }

    @Override
    @Transactional
    public void savePagesLemma() throws IOException {
        ///////////lemma////////////
        LuceneMorphology ruMorphology = new RussianLuceneMorphology();
        LuceneMorphology engMorphology = new EnglishLuceneMorphology();
        List<Page> pages = pageRepo.findAll();
        int availableProcessosrs = Runtime.getRuntime().availableProcessors();
        if (forkJoinPool == null || forkJoinPool.getActiveThreadCount() == 0) {
            forkJoinPool = new ForkJoinPool(availableProcessosrs);
        }
        LemmaTask lemmaTask = new LemmaTask(pages, lemmaRepo, indexRepo, ruMorphology, engMorphology);
        forkJoinPool.invoke(lemmaTask);


////////////////////////////
    }
}
