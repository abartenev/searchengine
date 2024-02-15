package searchengine.model;

import lombok.Data;

import javax.persistence.*;
import java.util.Objects;

@Entity
@Table(name = "Lemma")
@Data
public class Lemma {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Integer id;
    @ManyToOne
    @JoinColumn
    private SiteEntity site_id; //id веб-сайта из таблицы site
    private String lemma; //нормальная форма слова (лемма)
    /**
     * количество страниц, на которых слово встречается хотя бы один раз.
     * Максимальное не может превышать общее количество на сайте.
     */
    private Integer frequency;

    public Lemma() {
    }

    public Lemma(String lemma, SiteEntity site_id) {
        this.site_id = site_id;
        this.lemma = lemma;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Lemma lemma1 = (Lemma) o;
        return Objects.equals(site_id, lemma1.site_id) && Objects.equals(lemma, lemma1.lemma);
    }

    @Override
    public int hashCode() {
        return Objects.hash(site_id, lemma);
    }
}
