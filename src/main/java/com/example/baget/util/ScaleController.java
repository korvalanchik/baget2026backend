package com.example.baget.util;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/scale")
public class ScaleController {

    private final CloudRunScaleService scaleService;

    @PostMapping("/up")
    public String up() throws Exception {
        scaleService.updateMinInstances(1);
        return "scaled to 1";
    }

    @PostMapping("/down")
    public String down() throws Exception {
        scaleService.updateMinInstances(0);
        return "scaled to 0";
    }

    @PostMapping("/{count}")
    public String scale(@PathVariable int count) throws Exception {
        scaleService.updateMinInstances(count);
        return "scaled to " + count;
    }
}