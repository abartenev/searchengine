package searchengine.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.indexing.indexStatus;
import searchengine.dto.indexing.pageStatus;
import searchengine.dto.searchinfo.SearchResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.model.SiteEntity;
import searchengine.repositories.siteRepo;
import searchengine.services.IndexingService;
import searchengine.services.LemmaService;
import searchengine.services.SearchService;
import searchengine.services.StatisticsService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RestController
@Configuration
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;
    private final IndexingService indexingService;
    private final SearchService searchingService;
    private final LemmaService lemmaService;
    private final siteRepo siteRepo;

    @Autowired
    public ApiController(
            StatisticsService statisticsService
            ,IndexingService indexingService
            ,SearchService searchService
            ,LemmaService lemmaService
            ,siteRepo repo
            ) {
        this.statisticsService = statisticsService;
        this.indexingService = indexingService;
        this.searchingService = searchService;
        this.lemmaService = lemmaService;
        this.siteRepo = repo;
    }

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<indexStatus> startIndexing() throws IOException {
        return ResponseEntity.ok(indexingService.startIndexing());
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<indexStatus> stopIndexing() {
        return ResponseEntity.ok(indexingService.stopIndexing());
    }

    @PostMapping(value = "/indexPage") // text/plain не работает.
    public ResponseEntity<pageStatus> indexPage(@RequestBody String page) {
        return ResponseEntity.ok(indexingService.addUpdatePage(page.substring(4))); //обрезаем url=
    }

    @GetMapping("/startLemmaSearch")
    public void startLemmaSearch() throws IOException {
        lemmaService.savePagesLemma();
    }

    @GetMapping("/search")
    public ResponseEntity<SearchResponse> searchEntities(@RequestParam("site") String site, @RequestParam("query") String query) {
        return ResponseEntity.ok(searchingService.getSearchResults(site, query));
    }

    @GetMapping("/sites")
    public List<SiteEntity> getTasks() {
        Iterable<SiteEntity> list = siteRepo.findAll();
        List<SiteEntity> list1 = new ArrayList<>();
        list.forEach(list1::add);
        return list1;
    }

    @GetMapping("/site/{ID}")
    public ResponseEntity<SiteEntity> getTaskById(@PathVariable int ID) {
        Optional<SiteEntity> todoList = siteRepo.findById(ID);
        if (todoList.isPresent()) {
            return new ResponseEntity(todoList.get(), HttpStatus.OK);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/delete/{ID}")
    public ResponseEntity<SiteEntity> deleteSite(@PathVariable int ID) {
        SiteEntity siteEntity = siteRepo.findById(ID).orElse(null);
        if (siteEntity != null) {
            siteRepo.delete(siteEntity);
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/entity")
    public SiteEntity getSiteEntity() {
        return new SiteEntity("http://url","nameofsite");
    }
}
