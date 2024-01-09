package searchengine.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import searchengine.model.SiteEntity;
import searchengine.services.interfaces.SearchService;

import java.util.List;

@Controller
@Configuration
public class DefaultController {
    @Value("${welcome.message}")
    private String message;
    @Autowired
    private SearchService searchService;

    /**
     * Метод формирует страницу из HTML-файла index.html,
     * который находится в папке resources/templates.
     * Это делает библиотека Thymeleaf.
     */

    @RequestMapping("/")
    public String index(Model model) {
        List<SiteEntity> sites = searchService.findAllsites();
        model.addAttribute("sites", sites);
        model.addAttribute("message", message);
        return "index";
    }
}
