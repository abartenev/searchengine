package searchengine.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.indexing.indexStatus;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.model.SiteEntity;
import searchengine.dao.siteRepo;
import searchengine.services.IndexingService;
import searchengine.services.StatisticsService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;
    private final IndexingService indexingService;
    private siteRepo siteRepo;

    public ApiController(StatisticsService statisticsService, IndexingService indexingService) {
        this.statisticsService = statisticsService;
        this.indexingService = indexingService;
    }

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<indexStatus> startIndexing() {
        return ResponseEntity.ok(indexingService.startIndexing());
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<indexStatus> stopIndexing() {
        return ResponseEntity.ok(indexingService.stopIndexing());
    }

    @GetMapping("/sites/")
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
}
