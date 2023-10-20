package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.model.Page;

public interface pageRepo extends JpaRepository<Page, Integer> {
}
