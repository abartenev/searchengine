package searchengine.services;

import lombok.Data;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Data
public class ScrapTask extends RecursiveTask<TreeSet<String>>{
    private TreeSet<String> keys;
    private TreeSet<String> checkedKeys;
    private String url2scrap;
    private Document document;

    public ScrapTask(String startURLsite, TreeSet<String> visitedUrls) {
        keys = new TreeSet<>();
        checkedKeys = visitedUrls;
        url2scrap = startURLsite;
    }

    @Override
    protected TreeSet<String> compute() {
        System.out.println("Ждем 2 секунды. Размер checkedKeys = " + checkedKeys.size());
        try {
//            Thread.sleep(2000L);
            TimeUnit.SECONDS.sleep(4);
            try {
                document = Jsoup.connect(url2scrap) //"https://lenta.ru/"
                        .userAgent("Mozilla/5.0 (Windows NT 6.1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/41.0.2228.0 Safari/537.36")
                        .referrer("https://lenta.ru/") //откуда отправляем запрос
                        .timeout(24000)
                        .followRedirects(true)
                        .get();
                //обрабатываем загруженную информацию
                System.out.println("Формируем список в потоке: " + Thread.currentThread().getName());
                Set<String> tempSet = document.select("a[href]")
                        .stream()
                        .filter(element -> element.attr("href").startsWith("/"))
                        .filter(element -> !element.attr("href").matches(".*(\\?|html).*"))
                        .map(element -> element.absUrl("href"))
                        .collect(Collectors.toSet());
                if (tempSet.size() == 1) {
                    String url2check = tempSet.stream().toList().get(0);
                    ScrapTask scrapTasksub;
                    if (!checkedKeys.contains(url2check)) {
                        checkedKeys.add(url2check);
                        scrapTasksub = new ScrapTask(url2check, checkedKeys);
                        scrapTasksub.fork();
                        keys.addAll(scrapTasksub.join());
                    }

                } else if (tempSet.size() > 2) {
                    List<ScrapTask> scrapTasks = new ArrayList<>();
                    for (String url2check : tempSet) {
                        if (!checkedKeys.contains(url2check)) {
                            checkedKeys.add(url2check);
                            scrapTasks.add(new ScrapTask(url2check, checkedKeys));
                        }
                    }
                    for (ScrapTask scrapTask : scrapTasks) {
                        scrapTask.fork();
                    }
                    for (ScrapTask scrapTask : scrapTasks) {
                        keys.addAll(scrapTask.join());
                    }
                }
            } catch (IOException e) {
                System.out.println("Получили ошибку при обработке адреса e="+e.getLocalizedMessage());
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
        keys.addAll(checkedKeys);
        return keys;
    }
}
