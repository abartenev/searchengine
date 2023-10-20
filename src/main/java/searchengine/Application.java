package searchengine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        //System.setProperty("spring.config.name","application");
        SpringApplication.run(Application.class, args);
    }
}
