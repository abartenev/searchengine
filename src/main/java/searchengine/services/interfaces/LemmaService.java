package searchengine.services.interfaces;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Page;

import java.io.IOException;


public interface LemmaService {
    void savePagesLemma() throws IOException;

}
