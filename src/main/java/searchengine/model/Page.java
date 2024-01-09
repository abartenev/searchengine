package searchengine.model;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.persistence.Index;
import javax.persistence.*;

@Entity
@Component
@RequiredArgsConstructor
@Table(name = "page", indexes = {@Index(name = "path_idx", columnList = "path")})
@Data
public class Page {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Integer id;
    @ManyToOne
    @JoinColumn
    private SiteEntity site_Entity_id; //id веб-сайта из таблицы site
    private String path; //адрес страницы
    private Integer code; //http ответ
    @Column(columnDefinition = "MEDIUMTEXT")
    private String content; //контент страницы

    public Page(SiteEntity site_Entity_id, String path) {
        this.site_Entity_id = site_Entity_id;
        this.path = path;
    }
}
