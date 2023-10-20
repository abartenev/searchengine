package searchengine.model;

import lombok.Data;

import javax.persistence.*;

@Entity
@Table(name = "Lemma")
@Data
public class Lemma {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Integer id;
    private Integer site_id; //id веб-сайта из таблицы site
    private String lemma; //нормальная форма слова (лемма)
    /**
     * количество страниц, на которых слово встречается хотя бы один раз.
     * Максимальное не может превышать общее количество на сайте.*/
    private Integer frequency;
}
