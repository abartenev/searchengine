package searchengine.dto.indexing;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class pageStatus {
    private final boolean result;
    private final String error;

    public pageStatus(boolean result, String errortext) {
        this.result = result;
        this.error = errortext;
    }
}
