package searchengine.model;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.persistence.*;
import java.util.Objects;

@Entity
@Component
@RequiredArgsConstructor
@Table(name = "i_ndex")
@Data
public class Index {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Integer id;
    @ManyToOne
    @JoinColumn
    private Page page_id; //идентификатор страницы
    @ManyToOne
    @JoinColumn
    private Lemma lemma_id; //идентификатор леммы

    public Index(Lemma lemma_id,Page page_id) {
        this.page_id = page_id;
        this.lemma_id = lemma_id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Index index = (Index) o;
        return Objects.equals(page_id, index.page_id) && Objects.equals(lemma_id, index.lemma_id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(page_id, lemma_id);
    }

    @Column(name = "rrank", precision = 10, scale = 2)
    private float rank; //количество леммы для страницы
}
