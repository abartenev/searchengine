package searchengine.model;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.persistence.*;

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
    @Column(name = "rrank", precision = 10, scale = 2)
    private float rank; //количество леммы для страницы
}
