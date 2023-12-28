package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.model.Page;
import searchengine.model.SiteEntity;

import java.util.List;

@Repository
public interface pageRepo extends JpaRepository<Page, Integer> {

    @Query("SELECT t FROM Page t WHERE t.site_Entity_id = :site")
    List<Page> findBySiteUrl(@Param("site") SiteEntity site);
}
