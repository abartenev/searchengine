package searchengine.model;

import lombok.Data;

import javax.persistence.*;

@Entity
@Table(name = "i_ndex")
@Data
public class Index {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Integer id;
    private Integer page_id; //идентификатор страницы
    private Integer lemma_id; //идентификатор леммы
    @Column(name = "rrank",precision = 10, scale = 2)
    private float rank; //количество леммы для страницы
}
