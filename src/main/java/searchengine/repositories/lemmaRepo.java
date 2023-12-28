package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.model.Lemma;
import searchengine.model.SiteEntity;

import java.util.List;

@Repository
public interface lemmaRepo extends JpaRepository<Lemma, Integer> {
    @Query("SELECT t FROM Lemma t WHERE t.lemma = :lemmaName and t.site_id = :site")
    Lemma findLemmaByName(@Param("lemmaName") String lemmaName, @Param("site") SiteEntity site);

    @Query("SELECT t FROM Lemma t WHERE t.site_id = :site")
    List<Lemma> findBySiteUrl(@Param("site") SiteEntity site);

}
