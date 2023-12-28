package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.SiteEntity;
import searchengine.repositories.lemmaRepo;
import searchengine.repositories.pageRepo;
import searchengine.repositories.siteRepo;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final Random random = new Random();
    private final SitesList sites;
    private final List<SiteEntity> siteEntities;
    @Autowired
    private final siteRepo siteRepo;
    @Autowired
    private final pageRepo pageRepo;
    @Autowired
    private final lemmaRepo lemmaRepo;


//    @Override
//    public StatisticsResponse getStatistics() {
//        String[] statuses = {"INDEXED", "FAILED", "INDEXING"};
//        String[] errors = {
//                "Ошибка индексации: главная страница сайта не доступна",
//                "Ошибка индексации: сайт не доступен",
//                ""
//        };
//
//        TotalStatistics total = new TotalStatistics();
//        total.setSites(sites.getSites().size());
//        total.setIndexing(true);
//
//        List<DetailedStatisticsItem> detailed = new ArrayList<>();
//        List<Site> sitesList = sites.getSites();
//        for (int i = 0; i < sitesList.size(); i++) {
//            Site site = sitesList.get(i);
//            DetailedStatisticsItem item = new DetailedStatisticsItem();
//            item.setName(site.getName());
//            item.setUrl(site.getUrl());
//            int pages = random.nextInt(1_000);
//            int lemmas = pages * random.nextInt(1_000);
//            item.setPages(pages);
//            item.setLemmas(lemmas);
//            item.setStatus(statuses[i % 3]);
//            item.setError(errors[i % 3]);
//            item.setStatusTime(System.currentTimeMillis() -
//                    (random.nextInt(10_000)));
//            total.setPages(total.getPages() + pages);
//            total.setLemmas(total.getLemmas() + lemmas);
//            detailed.add(item);
//        }
//
//        StatisticsResponse response = new StatisticsResponse();
//        StatisticsData data = new StatisticsData();
//        data.setTotal(total);
//        data.setDetailed(detailed);
//        response.setStatistics(data);
//        response.setResult(true);
//        return response;
//    }


    @Override
    public StatisticsResponse getStatistics() {
        siteEntities.clear();
        siteEntities.addAll(siteRepo.findAll());
        TotalStatistics total = new TotalStatistics();
        total.setSites(siteEntities.size());
//        total.setIndexing(true);
        List<DetailedStatisticsItem> detailed = new ArrayList<>();
        siteEntities.forEach(site -> {
            List<Site> sitesList = sites.getSites();
            DetailedStatisticsItem item = new DetailedStatisticsItem();
            item.setName(site.getName());
            item.setUrl(site.getUrl());
            List<Page> pages = pageRepo.findBySiteUrl(site);
            List<Lemma> lemmas = lemmaRepo.findBySiteUrl(site);
            item.setPages(pages.size());
            item.setLemmas(lemmas.size());
            item.setStatus(site.getStatus().name());
            item.setError(site.getLast_error());
            item.setStatusTime(System.currentTimeMillis() -
                    (random.nextInt(10_000)));
            total.setPages(total.getPages() + pages.size());
            total.setLemmas(total.getLemmas() + lemmas.size());
            detailed.add(item);
        });

        StatisticsResponse response = new StatisticsResponse();
        StatisticsData data = new StatisticsData();
        data.setTotal(total);
        data.setDetailed(detailed);
        response.setStatistics(data);
        response.setResult(true);
        return response;
    }
}
