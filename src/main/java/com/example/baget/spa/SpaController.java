package com.example.baget.spa;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaController {

    @GetMapping({"/clients", "/clients/{id}"})
    public String spa() {
        return "forward:/customer/index.html";
    }

}