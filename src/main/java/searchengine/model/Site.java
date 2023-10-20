package searchengine.model;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "Site")
@Data
public class Site {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Integer id;
    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "ENUM('INDEXING','INDEXED','FAILED')")
    private Status status; //статус полной индексации
    private LocalDateTime status_time; //дата и время статуса
    @Column(columnDefinition = "TEXT")
    private String last_error; //текст ошибки индексации
    @Column(columnDefinition = "MEDIUMTEXT")
    private String url; //адрес главной страницы
    @Column(columnDefinition = "TEXT")
    private String name; //название сайта

    public Site(String url, String name) {
        this.url = url;
        this.name = name;
        this.setStatus_time(LocalDateTime.now());
        this.status = Status.INDEXING;
    }
}
