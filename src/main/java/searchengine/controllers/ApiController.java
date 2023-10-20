package searchengine.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.repositories.siteRepo;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.model.Site;
import searchengine.model.indexStatus;
import searchengine.services.StatisticsService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;
    @Autowired
    private siteRepo siteRepo;

    public ApiController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<indexStatus> startIndexing(@RequestParam boolean flag) {
        return ResponseEntity.ok(new indexStatus(flag));
    }

    @GetMapping("/sites/")
    public List<Site> getTasks() {
        Iterable<Site> list = siteRepo.findAll();
        List<Site> list1 = new ArrayList<>();
        list.forEach(list1::add);
        return list1;
    }

    @GetMapping("/site/{ID}")
    public ResponseEntity<Site> getTaskById(@PathVariable int ID) {
        Optional<Site> todoList = siteRepo.findById(ID);
        if (todoList.isPresent()) {
            return new ResponseEntity(todoList.get(), HttpStatus.OK);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/delete/{ID}")
    public ResponseEntity<Site> deleteSite(@PathVariable int ID) {
        Site site = siteRepo.findById(ID).orElse(null);
        if (site != null) {
            siteRepo.delete(site);
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}
