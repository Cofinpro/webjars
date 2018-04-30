package de.cofinpro.webjars.springbootmvc;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Created by David
 * Date: 30.04.2018 - 23:37.
 */
@Controller
@RequestMapping(value = "/")
public class MainController {
    @GetMapping
    public String index() {
        return "index.html";
    }
}
