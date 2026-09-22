package com.skycel.backend.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Sirve la web (PWA) para celular y tablet en /app. Es una sola página con ruteo por hash (#/...), así que
 * basta con reenviar la raíz al index; los recursos propios (css, js, iconos) los sirve Spring Boot directo
 * desde src/main/resources/static/app porque están dentro de esa carpeta.
 */
@Controller
public class AppWebController {

    @GetMapping({"/app", "/app/"})
    public String app() {
        return "forward:/app/index.html";
    }
}
