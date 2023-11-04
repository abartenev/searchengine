package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import searchengine.dao.pageRepo;
import searchengine.dao.siteRepo;
import searchengine.model.Page;
import searchengine.model.SiteEntity;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


@RequiredArgsConstructor
public class ScrapTask extends RecursiveTask<TreeSet<String>> {
    private final siteRepo siteRepo;
    private final pageRepo pageRepo;
    private final TreeSet<String> keys;
    private final SiteEntity siteEntity;
    private Document document;
//    private final String url2scrap;
//    private volatile SiteEntity siteEntity2Scrap;
//    public ScrapTask(SiteEntity startURLsite, TreeSet<String> p_keys) {
//        keys = p_keys;
//        url2scrap = startURLsite.getUrl();
//    }
//
//    @Autowired
//    public ScrapTask(String startURLsite, TreeSet<String> p_keys, SiteEntity scrappingSiteEntity) {
//        keys = p_keys;
//        url2scrap = startURLsite;
//        this.siteEntity2Scrap = scrappingSiteEntity;
//    }



    @Override
    protected TreeSet<String> compute() {
        try {
            Random rand = new Random();
            int timeMs = rand.nextInt(200, 2000);
            System.out.println("Поток " + Thread.currentThread().getName() + ". Таймаут " + timeMs + " ms. Размер keys = " + keys.size());
            TimeUnit.MILLISECONDS.sleep(timeMs);
            try {
                document = Jsoup.connect(siteEntity.getUrl()) //"https://lenta.ru/"
                        .userAgent("Mozilla/5.0 (Windows NT 6.1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/41.0.2228.0 Safari/537.36").referrer("https://lenta.ru/") //откуда отправляем запрос
                        .timeout(24000).followRedirects(true).get();
                //обрабатываем загруженную информацию
//                System.out.println("Формируем список в потоке: " + Thread.currentThread().getName());
                Set<String> tempSet = document.select("a[href]").stream().filter(element -> element.attr("href").startsWith("/")).filter(element -> !element.attr("href").matches(".*(\\?|html).*")).map(element -> element.absUrl("href")).collect(Collectors.toSet());
                List<ScrapTask> scrapTasks = new ArrayList<>();
                for (String url2check : tempSet) {
                    if (!keys.contains(url2check) && url2check != siteEntity.getUrl()) {
                        keys.add(url2check);
                        Page page = new Page(siteEntity, url2check);
                        siteEntity.setStatus_time(LocalDateTime.now());
                        System.out.println("Page = " + page.getPath() + " ");
                        pageRepo.save(page);
                        ScrapTask task = new ScrapTask(siteRepo, pageRepo, keys, siteEntity);
                        task.fork();
                        scrapTasks.add(task);
                    }
                }
                for (ScrapTask task : scrapTasks) {
                    task.join();
                }
                System.out.println("ForkJoinTask.getPool().getActiveThreadCount() " + ForkJoinTask.getPool().getActiveThreadCount());
                System.out.println("ForkJoinTask.getPool().isShutdown() " + ForkJoinTask.getPool().isShutdown());
                if (ForkJoinTask.getPool().getActiveThreadCount() == 1) {
                    ForkJoinTask.getPool().shutdown();
                    System.out.println("ForkJoinTask.getPool().shutdown() and return keys");
                    return keys;
                }
            } catch (IOException e) {
                System.out.println("Получили ошибку при обработке адреса e=" + e.getLocalizedMessage());
                throw new RuntimeException(e);
            } catch (RuntimeException e) {
                if (e.getLocalizedMessage().contains("HTTP error fetching URL")) {
                    System.out.println("Получили ошибку HTTP error fetching URL");
                }
            }
        } catch (InterruptedException e) {
            System.out.println("InterruptedException e");
            throw new RuntimeException(e);
        }
        return keys;
    }
}
