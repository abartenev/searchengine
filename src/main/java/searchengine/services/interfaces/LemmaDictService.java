package searchengine.services.interfaces;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Page;

import javax.persistence.LockModeType;


public interface LemmaDictService {
    void fillLemmaDict(String s, Page p, Long lemmaPageCount);
}
