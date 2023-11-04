package searchengine.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.Page;

@Repository
public interface pageRepo extends JpaRepository<Page, Integer> {
}
