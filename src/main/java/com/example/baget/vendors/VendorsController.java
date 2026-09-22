package com.example.baget.vendors;

import com.example.baget.util.WebUtils;
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
@RequestMapping("/vendors")
public class VendorsController {

    private final VendorsService vendorsService;

    public VendorsController(final VendorsService vendorsService) {
        this.vendorsService = vendorsService;
    }

    @GetMapping
    public String list(final Model model) {
        model.addAttribute("vendorses", vendorsService.findAll());
        return "vendors/list";
    }

    @GetMapping("/add")
    public String add(@ModelAttribute("vendors") final VendorsDTO vendorsDTO) {
        return "vendors/add";
    }

    @PostMapping("/add")
    public String add(@ModelAttribute("vendors") @Valid final VendorsDTO vendorsDTO,
            final BindingResult bindingResult, final RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "vendors/add";
        }
        vendorsService.create(vendorsDTO);
        redirectAttributes.addFlashAttribute(WebUtils.MSG_SUCCESS, WebUtils.getMessage("vendors.create.success"));
        return "redirect:/vendors";
    }

    @GetMapping("/edit/{vendorNo}")
    public String edit(@PathVariable(name = "vendorNo") final Long vendorNo, final Model model) {
        model.addAttribute("vendors", vendorsService.get(vendorNo));
        return "vendors/edit";
    }

    @PostMapping("/edit/{vendorNo}")
    public String edit(@PathVariable(name = "vendorNo") final Long vendorNo,
            @ModelAttribute("vendors") @Valid final VendorsDTO vendorsDTO,
            final BindingResult bindingResult, final RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "vendors/edit";
        }
        vendorsService.update(vendorNo, vendorsDTO);
        redirectAttributes.addFlashAttribute(WebUtils.MSG_SUCCESS, WebUtils.getMessage("vendors.update.success"));
        return "redirect:/vendors";
    }

    @PostMapping("/delete/{vendorNo}")
    public String delete(@PathVariable(name = "vendorNo") final Long vendorNo,
            final RedirectAttributes redirectAttributes) {
        vendorsService.delete(vendorNo);
        redirectAttributes.addFlashAttribute(WebUtils.MSG_INFO, WebUtils.getMessage("vendors.delete.success"));
        return "redirect:/vendors";
    }

}
