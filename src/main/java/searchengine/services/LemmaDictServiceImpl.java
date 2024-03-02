package searchengine.services;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.repositories.indexRepo;
import searchengine.repositories.lemmaRepo;
import searchengine.repositories.pageRepo;
import searchengine.services.interfaces.LemmaDictService;

import javax.persistence.LockModeType;
import java.util.List;

@Service
@Log4j2
public class LemmaDictServiceImpl implements LemmaDictService {

    private final lemmaRepo lemmaRepo;
    private final indexRepo indexRepo;

    private final PlatformTransactionManager transactionManager;

    @Autowired
    public LemmaDictServiceImpl(lemmaRepo lemmaRepo, indexRepo indexRepo, PlatformTransactionManager manager) {
        this.lemmaRepo = lemmaRepo;
        this.indexRepo = indexRepo;
        this.transactionManager = manager;
    }

    @Override
    //@Transactional
    public void fillLemmaDict(String s, Page page, Long lemmaPageCount) {
            synchronized (/*lemmaRepo*/transactionManager) {
            //    synchronized (indexRepo) {
                        Lemma lemma = lemmaRepo.findLemmaByNameAndSite(s, page.getSite_Entity_id());
                        if (lemma == null) {
                            lemma = new Lemma();
                            lemma.setSite_id(page.getSite_Entity_id());
                            lemma.setFrequency(lemmaPageCount.intValue());
                            lemma.setLemma(s);
                            lemmaRepo.save(lemma);
                            System.out.println("1.3: Thread name: " + Thread.currentThread().getName());
                        } else {
                            lemma.setFrequency(lemma.getFrequency() + lemmaPageCount.intValue());
                            lemmaRepo.save(lemma);
                            System.out.println("1.4: Thread name: " + Thread.currentThread().getName());
                        }
                        Index index = indexRepo.findIndex4LemmaNPage(lemma, page);
                        if (index == null) {
                            index = new Index();
                            index.setPage_id(page);
                            index.setLemma_id(lemma);
                            index.setRank(lemmaPageCount.intValue());
                            indexRepo.save(index);
                        } else {
                            index.setRank(index.getRank() + lemmaPageCount.intValue());
                            indexRepo.save(index);
                            lemma.setFrequency(lemma.getFrequency() + lemmaPageCount.intValue());
                            lemmaRepo.save(lemma);
                        }
             //   }
            }
    }

    @Override
    @Transactional
    public void saveIndexes(List<Lemma> lemmas, String s, Page page, Long lemmaPageCount) {
        synchronized (transactionManager) {
//            Lemma lemma = lemmaRepo.findLemmaByNameAndSite(s, page.getSite_Entity_id());
            Lemma lemma = lemmas.parallelStream().filter(lemma1 -> lemma1.getLemma().equals(s) && lemma1.getSite_id().equals(page.getSite_Entity_id())).findFirst().get();
            Index index = indexRepo.findIndex4LemmaNPage(lemma, page);
            if (index == null) {
                index = new Index();
                index.setPage_id(page);
                index.setLemma_id(lemma);
                index.setRank(lemmaPageCount.intValue());
                System.out.println("1: Сохраняем новый индекс в Thread name: " + Thread.currentThread().getName());
                indexRepo.save(index);
            } else {
                index.setRank(index.getRank() + lemmaPageCount.intValue());
                System.out.println("2: Сохраняем обновленный индекс в Thread name: " + Thread.currentThread().getName());
                indexRepo.save(index);
            }
        }
    }

    @Override
    @Transactional
    @Retryable
    public void saveLemmas(List<Lemma> lemmaList) {
        lemmaList.parallelStream().forEach(lemma -> {
        try{
            lemmaRepo.save(lemma);
        } catch (Exception e){
          log.error(e.getLocalizedMessage());
        }}
        );
    }
}
