package searchengine.source;

import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.ListUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import searchengine.model.Page;
import searchengine.model.SiteEntity;
import searchengine.model.Status;
import searchengine.repositories.indexRepo;
import searchengine.repositories.lemmaRepo;
import searchengine.repositories.pageRepo;
import searchengine.repositories.siteRepo;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@AllArgsConstructor
@Log4j2
public class ScrapTask extends RecursiveTask<TreeSet<String>> {
    private final siteRepo siteRepo;
    private final pageRepo pageRepo;
    private final lemmaRepo lemmaRepo;
    private final indexRepo indexRepo;
    private final SiteEntity siteEntity;
    private final List<String> listOfUrls;
    private volatile TreeSet<String> keys;
    private volatile String firstUrl;

    @Override
    protected TreeSet<String> compute() {
        String url2check = null;
        Document document = null;
        int parallelCount = ForkJoinTask.getPool().getParallelism();
        try {
            Set<String> tempSet;
            Random rand = new Random();
            int timeMs = rand.nextInt(200, 2000);
            System.out.println("Поток " + Thread.currentThread().getName() + ". Таймаут " + timeMs + " ms. Размер keys = " + keys.size() + " checkingUrl = " + listOfUrls.size() + " thread count = " + ForkJoinTask.getPool().getActiveThreadCount());
            TimeUnit.MILLISECONDS.sleep(timeMs);
            for (String checkingUrl : listOfUrls) {
                try {
                    document = Jsoup.connect(checkingUrl).userAgent("Mozilla/5.0 (Windows NT 6.1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/41.0.2228.0 Safari/537.36").referrer("https://lenta.ru/") //откуда отправляем запрос
                            .timeout(24000).followRedirects(true).get();
                    //save content*********************************************
                    saveUrlPage(checkingUrl, document);
                    //save content end*********************************************
                    tempSet = document.select("a[href]").stream()//
                            .filter(element -> element.attr("href").startsWith("/")) //
                            .filter(element -> !element.attr("href").matches(".*(\\?|html).*")) //
                            .map(element -> element.absUrl("href"))//
                            .filter(s -> !keys.contains(s))//
                            .filter(s -> !s.equals(firstUrl))//
                            .filter(s -> s.startsWith(firstUrl))//
                            .collect(Collectors.toSet());
                    keys.addAll(tempSet);
                    List<ScrapTask> scrapTasks = new ArrayList<>();
                    List<List<String>> list = ListUtils.partition(tempSet.stream().toList(), parallelCount);
                    list.forEach(strings -> {
                        ScrapTask task = new ScrapTask(siteRepo, pageRepo, lemmaRepo, indexRepo, siteEntity, strings, keys, firstUrl);
                        task.fork();
                        scrapTasks.add(task);
                        //task.join();
                    });
                    scrapTasks.forEach(ForkJoinTask::join);
                } catch (IOException e) {
                    if (checkingUrl == null || checkingUrl.equals(siteEntity.getUrl())) {
                        log.info("Получили ошибку при обработке первого адреса e=" + e.getLocalizedMessage() + ".\n" + "Выходим.");
                        Page page = new Page(siteEntity, checkingUrl);
                        page.setContent(e.getLocalizedMessage());
                        page.setCode(408);
                        siteEntity.setStatus_time(LocalDateTime.now());
                        System.out.println("Page = " + page.getPath() + " ");
                        pageRepo.save(page);
                        siteRepo.save(siteEntity);
                    } else {
                        log.info("Получили ошибку при обработке адреса e=" + e.getLocalizedMessage());
                    }
                } catch (RuntimeException e) {
                    if (e.getLocalizedMessage().contains("HTTP error fetching URL")) {
                        log.info("Получили ошибку HTTP error fetching URL");
                    }
                }
            }
        } catch (InterruptedException e) {
            System.out.println("InterruptedException e");
            log.error(e);
            throw new RuntimeException(e);
        }

        endProcessingNgetStrings();

        return keys;
    }

    private void saveUrlPage(String checkingUrl, Document document) {
        Page page = new Page(siteEntity, checkingUrl);
        page.setContent(document.wholeText());
        page.setCode(200);
        siteEntity.setStatus_time(LocalDateTime.now());
        pageRepo.save(page);
    }

    private TreeSet<String> endProcessingNgetStrings() {
        if (ForkJoinTask.getPool().getActiveThreadCount() == 1) {
            ForkJoinTask.getPool().shutdown();

            log.info("ForkJoinTask.getPool().shutdown() and return keys");
            System.out.println("ForkJoinTask.getPool().shutdown() and return keys");
            siteEntity.setStatus(Status.INDEXED);
            siteRepo.save(siteEntity);
            return keys;
        }
        return null;
    }
}
