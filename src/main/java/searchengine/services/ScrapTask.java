package searchengine.services;

import lombok.Data;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Data
public class ScrapTask extends RecursiveTask<TreeSet<String>> {
    private final String url2scrap;
    private final String firstUrl;
    private volatile TreeSet<String> keys;
    private Document document;
    private searchengine.repository.siteRepo siteRepo;
    private searchengine.repository.pageRepo pageRepo;

    public ScrapTask(String startURLsite, TreeSet<String> p_keys) {
        keys = p_keys;
        url2scrap = startURLsite;
        firstUrl = getFirstUrl();
    }

    public ScrapTask(String startURLsite, TreeSet<String> p_keys, String firstUrl) {
        keys = p_keys;
        url2scrap = startURLsite;
        this.firstUrl = firstUrl;
    }

    @Override
    protected TreeSet<String> compute() {
        try {
            Random rand = new Random();
            int timeMs = rand.nextInt(200, 2000);
            System.out.println("Таймаут " + timeMs + " ms. Размер keys = " + keys.size());
            TimeUnit.MILLISECONDS.sleep(timeMs);
            try {
                document = Jsoup.connect(url2scrap) //"https://lenta.ru/"
                        .userAgent("Mozilla/5.0 (Windows NT 6.1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/41.0.2228.0 Safari/537.36").referrer("https://lenta.ru/") //откуда отправляем запрос
                        .timeout(24000).followRedirects(true).get();
                //обрабатываем загруженную информацию
                System.out.println("Формируем список в потоке: " + Thread.currentThread().getName());
                Set<String> tempSet = document
                        .select("a[href]")
                        .stream()
                        .filter(element -> element.attr("href")
                                .startsWith("/")).filter(element -> !element.attr("href")
                                .matches(".*(\\?|html).*"))
                        .map(element -> element.absUrl("href"))
                        .collect(Collectors.toSet());
                List<ScrapTask> scrapTasks = new ArrayList<>();
                for (String url2check : tempSet) {
                    if (!keys.contains(url2check) && url2check != this.url2scrap) {
                        keys.add(url2check);
                        ScrapTask task = new ScrapTask(url2check, keys);
                        task.fork();
                        scrapTasks.add(task);
                    }
                }
                for (ScrapTask task : scrapTasks) {
                    if (keys.size() >= 100) {
                        task.quietlyComplete();
                        continue;
                    }
                    task.join();
                }
                System.out.println(ForkJoinTask.getPool().getActiveThreadCount());
                System.out.println(ForkJoinTask.getPool().isShutdown());

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
