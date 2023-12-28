package searchengine.config;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode
public class Site {
    private String url;
    private String name;
}
