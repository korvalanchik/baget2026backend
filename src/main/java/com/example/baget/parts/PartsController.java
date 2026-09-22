package com.example.baget.parts;

import com.example.baget.util.WebUtils;
import com.example.baget.vendors.VendorsService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;


@Controller
@RequestMapping("/parts")
public class PartsController {

    private final PartsService partsService;
    private final VendorsService vendorsService;

    public PartsController(final PartsService partsService, VendorsService vendorsService) {
        this.partsService = partsService;
        this.vendorsService = vendorsService;
    }

    @GetMapping
    public String list(final Model model) {
        model.addAttribute("partses", partsService.findAll());
        return "parts/list";
    }

    @GetMapping("/add")
    public String add(final Model model) {
        model.addAttribute("parts", new PartsDTO());
        model.addAttribute("vendors", vendorsService.findAll());
        return "parts/add";
    }

    @PostMapping("/add")
    public String add(@ModelAttribute("parts") @Valid final PartsDTO partsDTO,
                      final BindingResult bindingResult, final Model model, final RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("vendors", vendorsService.findAll());
            return "parts/add";
        }
        partsService.create(partsDTO);
        redirectAttributes.addFlashAttribute(WebUtils.MSG_SUCCESS, WebUtils.getMessage("parts.create.success"));
        return "redirect:/parts";
    }

    @GetMapping("/edit/{partNo}")
    public String edit(@PathVariable(name = "partNo") final Long partNo, final Model model) {
        model.addAttribute("parts", partsService.get(partNo));
        return "parts/edit";
    }

    @PostMapping("/edit/{partNo}")
    public String edit(@PathVariable(name = "partNo") final Long partNo,
            @ModelAttribute("parts") @Valid final PartsDTO partsDTO,
            final BindingResult bindingResult, final RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "parts/edit";
        }
        partsService.update(partNo, partsDTO);
        redirectAttributes.addFlashAttribute(WebUtils.MSG_SUCCESS, WebUtils.getMessage("parts.update.success"));
        return "redirect:/parts";
    }

    @PostMapping("/delete/{partNo}")
    public String delete(@PathVariable(name = "partNo") final Long partNo,
            final RedirectAttributes redirectAttributes) {
        partsService.delete(partNo);
        redirectAttributes.addFlashAttribute(WebUtils.MSG_INFO, WebUtils.getMessage("parts.delete.success"));
        return "redirect:/parts";
    }

}
