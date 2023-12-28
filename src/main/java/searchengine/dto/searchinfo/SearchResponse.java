package searchengine.dto.searchinfo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class SearchResponse {
    private boolean result; //Результат поискового запроса
    private int count; //Общее количество результатов
    private List<SearchResponseData> data;
    private String error;
}
