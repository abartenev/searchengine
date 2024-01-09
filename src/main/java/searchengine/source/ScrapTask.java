package searchengine.source;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
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
@Log4j2
public class ScrapTask extends RecursiveTask<TreeSet<String>> {
    private final siteRepo siteRepo;
    private final pageRepo pageRepo;
    private final lemmaRepo lemmaRepo;
    private final indexRepo indexRepo;
    private final TreeSet<String> keys;
    private final SiteEntity siteEntity;
    private final String checkingUrl;

    @Override
    protected TreeSet<String> compute() {
        String url2check = null;
        Document document = null;
        try {
            Random rand = new Random();
            int timeMs = rand.nextInt(200, 2000);
            System.out.println("Поток " + Thread.currentThread().getName() + ". Таймаут " + timeMs + " ms. Размер keys = " + keys.size() + " checkingUrl = " + checkingUrl);
            TimeUnit.MILLISECONDS.sleep(timeMs);
            TreeSet<String> keys1 = try2scrapUrl(url2check);
            if (keys1 != null) return keys1;
        } catch (InterruptedException e) {
            System.out.println("InterruptedException e");
            log.error(e);
            throw new RuntimeException(e);
        }
        return keys;
    }

    private TreeSet<String> try2scrapUrl(String url2check) {
        Document document;
        try {
            document = Jsoup.connect(checkingUrl) //"https://lenta.ru/"
                    .userAgent("Mozilla/5.0 (Windows NT 6.1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/41.0.2228.0 Safari/537.36").referrer("https://lenta.ru/") //откуда отправляем запрос
                    .timeout(24000).followRedirects(true).get();
            Set<String> tempSet = document.select("a[href]").stream().filter(element -> element.attr("href").startsWith("/")).filter(element -> !element.attr("href").matches(".*(\\?|html).*")).map(element -> element.absUrl("href")).collect(Collectors.toSet());
            List<ScrapTask> scrapTasks = new ArrayList<>();
            Iterator<String> urlsIter = tempSet.iterator();
            while (urlsIter.hasNext()) {
                url2check = urlsIter.next();
                checkUrlNsave(url2check, document, scrapTasks);
                break;
            }
            for (ScrapTask task : scrapTasks) {
                task.join();
            }
            log.info("ForkJoinTask.getPool().getActiveThreadCount() " + ForkJoinTask.getPool().getActiveThreadCount());
            log.info("ForkJoinTask.getPool().isShutdown() " + ForkJoinTask.getPool().isShutdown());
            TreeSet<String> keys1 = endProcessingNgetStrings();
            if (keys1 != null) return keys1;
        } catch (IOException e) {
            if (url2check == null || url2check.equals(siteEntity.getUrl())) {
                log.info("Получили ошибку при обработке первого адреса e=" + e.getLocalizedMessage() + ".\n" + "Выходим.");
                savePageWithException(e);
                return keys;
            } else {
                log.info("Получили ошибку при обработке адреса e=" + e.getLocalizedMessage());
            }
            throw new RuntimeException(e);
        } catch (RuntimeException e) {
            if (e.getLocalizedMessage().contains("HTTP error fetching URL")) {
                log.info("Получили ошибку HTTP error fetching URL");
            }
        }
        return null;
    }

    private void savePageWithException(IOException e) {
        Page page = new Page(siteEntity, checkingUrl);
        page.setContent(e.getLocalizedMessage());
        page.setCode(408);
        siteEntity.setStatus_time(LocalDateTime.now());
        System.out.println("Page = " + page.getPath() + " ");
        pageRepo.save(page);
        siteRepo.save(siteEntity);
    }

    private TreeSet<String> endProcessingNgetStrings() {
        if (ForkJoinTask.getPool().getActiveThreadCount() == 1) {
            ForkJoinTask.getPool().shutdown();

            log.info("ForkJoinTask.getPool().shutdown() and return keys");
            siteEntity.setStatus(Status.INDEXED);
            siteRepo.save(siteEntity);
            return keys;
        }
        return null;
    }

    private void checkUrlNsave(String url2check, Document document, List<ScrapTask> scrapTasks) {
        if (!keys.contains(url2check) && !url2check.equals(siteEntity.getUrl())) {
            keys.add(url2check);
            Page page = new Page(siteEntity, url2check);
            page.setContent(document.wholeText());
            page.setCode(200);
            siteEntity.setStatus_time(LocalDateTime.now());
            System.out.println("Page = " + page.getPath() + " ");
            pageRepo.save(page);
            ScrapTask task = new ScrapTask(siteRepo, pageRepo, lemmaRepo, indexRepo, keys, siteEntity, url2check);
            task.fork();
            scrapTasks.add(task);
        }
    }
}
