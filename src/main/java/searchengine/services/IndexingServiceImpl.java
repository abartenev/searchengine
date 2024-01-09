package searchengine.services;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.indexing.indexStatus;
import searchengine.dto.indexing.pageStatus;
import searchengine.model.Index;
import searchengine.model.SiteEntity;
import searchengine.model.Status;
import searchengine.repositories.indexRepo;
import searchengine.repositories.lemmaRepo;
import searchengine.repositories.pageRepo;
import searchengine.repositories.siteRepo;
import searchengine.services.interfaces.IndexingService;
import searchengine.services.interfaces.LemmaService;
import searchengine.source.ScrapTask;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.time.LocalDateTime;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;

@Service
@Log4j2
public class IndexingServiceImpl implements IndexingService {
    private final SitesList sites;
    private final siteRepo siteRepo;
    private final pageRepo pageRepo;
    private final lemmaRepo lemmaRepo;
    private final indexRepo indexRepo;
    private final LemmaService lemmaService;
    private ForkJoinPool forkJoinPool; //пул потоков для парсинга и индексации
    private TreeSet<String> strSet; //сет для хранения и записи полученных ссылок сайта
    private ForkJoinTask task;

    @Autowired
    public IndexingServiceImpl(SitesList sites, searchengine.repositories.siteRepo siteRepo, searchengine.repositories.pageRepo pageRepo, searchengine.repositories.lemmaRepo lemmaRepo, searchengine.repositories.indexRepo indexRepo, LemmaService lemmaService) {
        this.sites = sites;
        this.siteRepo = siteRepo;
        this.pageRepo = pageRepo;
        this.lemmaRepo = lemmaRepo;
        this.indexRepo = indexRepo;
        this.lemmaService = lemmaService;
    }

    @Override
    public indexStatus startIndexing() {
        log.info("Начинаем индексацию.");
        List<Site> sitesList = sites.getSites();
        List<SiteEntity> entities = siteRepo.findAll();
        sitesList.forEach(site -> {
            SiteEntity entity = siteRepo.findBySiteUrl(site.getUrl());
            if (entity == null) {
                entities.add(new SiteEntity(site.getUrl(), site.getName()));
            }
        });

        entities.forEach(site -> {
            int availableProcessosrs = Runtime.getRuntime().availableProcessors();
            if (forkJoinPool == null || forkJoinPool.getActiveThreadCount() == 0) {
                forkJoinPool = new ForkJoinPool(availableProcessosrs);
                site.setStatus_time(LocalDateTime.now());
                site.setStatus(Status.INDEXING);
                siteRepo.save(site);
                pageRepo.findBySiteUrl(site).forEach(page -> {
                    List<Index> indexList = indexRepo.findIndex4LemmaNPage(page);
                    indexRepo.deleteAll(indexList);
                    pageRepo.delete(page);
                });
                ScrapTask scrapTask = new ScrapTask(siteRepo, pageRepo, lemmaRepo, indexRepo, new TreeSet<>(), site, site.getUrl());
                TreeSet<String> res = forkJoinPool.invoke(scrapTask);
                try {
                    lemmaService.savePagesLemma();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        return new indexStatus(true, null);
    }

    @Override
    public indexStatus stopIndexing() {
        if (task != null && !task.isDone()) {
            task.quietlyComplete();
            forkJoinPool.shutdown();
            return new indexStatus(true, null);
        }
        return new indexStatus(false, "Индексация уже остановлена");
    }

    @Override
    public pageStatus addUpdatePage(String page) {
        pageStatus status;
        try {
            URL url = new URL(URLDecoder.decode(page));
            url.toURI(); //Если прошли проверку ссылки, идем дальше
            SiteEntity site = siteRepo.findBySiteUrl(page);
            if (site == null) {
                site = new SiteEntity();
                site.setUrl(url.toURI().toString());
                site.setName(url.getHost());
                site.setStatus(Status.INDEXING);
                siteRepo.save(site);
            } else {
                site.setStatus_time(LocalDateTime.now());
                site.setStatus(Status.INDEXING);
            }
            status = new pageStatus(true, "OK");
        } catch (MalformedURLException | URISyntaxException e) {
            status = new pageStatus(false, e.getLocalizedMessage());
            //throw new RuntimeException(e);
        }
        return status;
    }
}
