package searchengine.dto.searchinfo;

import lombok.Data;

@Data
public class SearchResponseData {
    private String site; //Главный домен сайта
    private String siteName; //название сайта
    private String uri; //ссылка
    private String title; //Заголовок страницы
    private String snippet; //Фрагмент текста, в котором нашли совпадения
    private String relevance; //Релевантность
}
