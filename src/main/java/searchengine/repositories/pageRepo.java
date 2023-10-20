package searchengine.repositories;

import org.springframework.data.repository.CrudRepository;
import searchengine.model.Page;

public interface pageRepo extends CrudRepository<Page, Integer> {
}
