package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.model.SiteEntity;

import java.util.List;

@Repository
public interface siteRepo extends JpaRepository<SiteEntity, Integer> {

    @Query("SELECT t FROM SiteEntity t WHERE t.url = :urlsite")
    SiteEntity findBySiteUrl(@Param("urlsite") String urlsite);

    @Query("SELECT t FROM SiteEntity t WHERE (t.url = :urlsite or 'All' = :urlsite)")
    List<SiteEntity> findBySites(@Param("urlsite") String urlsite);

}