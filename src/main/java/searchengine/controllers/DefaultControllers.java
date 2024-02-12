package searchengine.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class DefaultControllers {
    /**
     * Метод формирует страницу из HTML-файла index.html,
     * который находится в папке resources/templates.
     * Это делает библиотека Thymeleaf.
     */
    @RequestMapping("/")
    public String index() {
        return "index";
    }
}
