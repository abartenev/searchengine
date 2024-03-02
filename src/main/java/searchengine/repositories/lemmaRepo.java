package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Lemma;
import searchengine.model.SiteEntity;

import java.util.ArrayList;
import java.util.List;

@Repository
public interface lemmaRepo extends JpaRepository<Lemma, Integer>{

    //@Transactional(propagation = Propagation.REQUIRED,isolation = Isolation.SERIALIZABLE,readOnly = true)
    @Query("SELECT t FROM Lemma t WHERE lower(t.lemma) = replace(lower(:lemmaName),'ё','е') and t.site_id = :site")
    Lemma findLemmaByNameAndSite(@Param("lemmaName") String lemmaName, @Param("site") SiteEntity site);

    @Query("SELECT t FROM Lemma t WHERE lower(t.lemma) = replace(lower(:lemmaName),'ё','е')")
    List<Lemma> getLemmasByName(@Param("lemmaName") String lemmaName);

    @Query("SELECT t FROM Lemma t WHERE lower(t.lemma) in (:lemmaNames) and t.site_id in (:siteids)")
    List<Lemma> getLemmasByNames(@Param("lemmaNames") List<String> lemmaNames,@Param("siteids") List<SiteEntity> siteids);


    @Query("SELECT t FROM Lemma t WHERE t.site_id = :site")
    List<Lemma> findBySiteUrl(@Param("site") SiteEntity site);
}
