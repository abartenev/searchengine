package searchengine.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class indexStatus {
    private final boolean result;
    private final String error;

    public indexStatus(boolean result) {
        this.result = result;
        this.error = result ? null : "Индексация уже запущена";
    }
}
