package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.model.Site;

@Repository
public interface siteRepo extends JpaRepository<Site, Integer> {
//
//    @Query("SELECT t FROM site t where t.name = :sitename")
//    Iterable<Site> findBySiteName(@Param("sitename") String sitename);
}