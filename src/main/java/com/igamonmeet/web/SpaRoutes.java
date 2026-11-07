package com.igamonmeet.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Client-side routes the React app handles; the server just serves index.html. */
@Controller
public class SpaRoutes {

    @GetMapping("/recover")
    public String recover() {
        return "forward:/index.html";
    }
}
