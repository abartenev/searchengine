package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dao.pageRepo;
import searchengine.dao.siteRepo;
import searchengine.dto.indexing.indexStatus;
import searchengine.model.SiteEntity;

import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;

@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {
    private final SitesList sites;
    private final siteRepo siteRepo;
    private final pageRepo pageRepo;
    private ForkJoinPool forkJoinPool; //пул потоков для парсинга и индексации
    private TreeSet<String> strSet; //сет для хранения и записи полученных ссылок сайта
    private ForkJoinTask task;

    @Override
    public indexStatus startIndexing() {

        List<Site> sitesList = sites.getSites();
        Site site = sitesList.get(0);

        int availableProcessosrs = Runtime.getRuntime().availableProcessors();
        if (forkJoinPool == null || forkJoinPool.getActiveThreadCount() == 0) {
            forkJoinPool = new ForkJoinPool(availableProcessosrs);
            SiteEntity siteEntity1 = siteRepo.save(new SiteEntity(site.getUrl(), site.getName()));
            ScrapTask scrapTask = new ScrapTask(siteRepo, pageRepo,new TreeSet<>(),siteEntity1);
            task = forkJoinPool.submit(scrapTask);
        }
//        } else if (forkJoinPool.getRunningThreadCount() > 0) {
//            Iterable<searchengine.model.Site> sites1 = siteRepo.findAll();//findBySiteName(site.getName());
//            for (searchengine.model.Site site2 : sites1) {
//                System.out.println(site2.getName() + " is " + site2.getStatus());
//            }
//            System.out.println("Количество выполняемых потоков " + forkJoinPool.getRunningThreadCount());
//            return new indexStatus(false,"Индексация еще выполняется");
//        } else {
//            if (forkJoinPool.getRunningThreadCount() == 0) {
//                System.out.println("Количество выполняемых потоков " + 0 + ". Сохраняем результат и завершим пул потоков");
//                strSet = (TreeSet<String>) task.join(); //получаем результат парсинга страниц по завершению всех потоков
//                searchengine.model.Site site1 = new searchengine.model.Site(site.getUrl(), site.getName());
//                site1.setName(site.getName());
//                site1.setStatus(Status.INDEXING);
//                site1.setUrl(site.getUrl());
//                site1.setStatus_time(LocalDateTime.now());
//                siteRepo.save(site1);
//                strSet.forEach(s -> {
//                    Page page = new Page();
//                    page.setSite_id(site1);
//                    page.setPath(s);
//                    page.setCode(100);
//                    pageRepo.save(page);
//                });
//                forkJoinPool.shutdownNow();
//            }
//        }
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
}
