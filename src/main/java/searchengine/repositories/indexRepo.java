package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;

import javax.persistence.LockModeType;
import java.util.List;

@Repository
public interface indexRepo extends JpaRepository<Index, Integer> {
    @Query("SELECT t FROM Index t WHERE t.lemma_id = :lemma and t.page_id = :page")
    Index findIndex4LemmaNPage(@Param("lemma") Lemma lemmaName, @Param("page") Page page);

    @Query("SELECT t FROM Index t WHERE t.lemma_id = :lemma")
    List<Index> findIndex4Lemma(@Param("lemma") Lemma lemma);

//    @Query("select t2 from Index t2 WHERE t2.page_id in (" +
//            "SELECT distinct t.page_id FROM Index t WHERE t.lemma_id in (:lemma))" +
//            " and t2.lemma_id in (:lemma) order by t2.rank desc")
    @Query("SELECT t FROM Index t WHERE t.lemma_id in (:lemmas) and t.page_id in (:pages) order by t.rank desc")
    List<Index> findIndex4Lemmas(@Param("lemmas") List<Lemma> lemmas, @Param("pages") List<Page> pages);

    @Query("SELECT t FROM Index t WHERE t.page_id = :page")
    List<Index> findIndex4LemmaNPage(@Param("page") Page page);
}
