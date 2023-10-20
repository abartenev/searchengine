package searchengine.model;

import lombok.Data;

import javax.persistence.*;
import javax.persistence.Index;

@Entity
@Table(name = "Page",indexes = {@Index(name = "path_idx",columnList = "path")})
@Data
public class Page {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Integer id;
    @ManyToOne
    @JoinColumn
    private Site site_id; //id веб-сайта из таблицы site
    private String path; //адрес страницы
    private Integer code; //http ответ
    @Column(columnDefinition = "MEDIUMTEXT")
    private String content; //контент страницы

}
