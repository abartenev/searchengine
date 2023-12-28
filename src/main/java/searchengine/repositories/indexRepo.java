package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;

import java.util.List;

@Repository
public interface indexRepo extends JpaRepository<Index, Integer> {
    @Query("SELECT t FROM Index t WHERE t.lemma_id = :lemma and t.page_id = :page")
    Index findIndex4LemmaNPage(@Param("lemma") Lemma lemmaName, @Param("page") Page page);

    @Query("SELECT t FROM Index t WHERE t.lemma_id = :lemma")
    List<Index> findIndex4Lemma(@Param("lemma") Lemma lemma);

    @Query("SELECT t FROM Index t WHERE t.page_id = :page")
    List<Index> findIndex4LemmaNPage(@Param("page") Page page);
}
