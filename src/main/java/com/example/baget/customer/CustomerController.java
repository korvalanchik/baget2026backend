package com.example.baget.customer;

import com.example.baget.util.WebUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
@RequestMapping("/customers")
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping
    public String list(final Model model) {
        model.addAttribute("customers", customerService.findAll());
        return "customer/list";
    }

    @GetMapping("/add")
    public String add(@ModelAttribute("customer") final CustomerDTO customerDTO) {
        return "customer/add";
    }

    @PostMapping("/add")
    public String add(@ModelAttribute("customer") @Valid final CustomerDTO customerDTO,
            final BindingResult bindingResult, final RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "customer/add";
        }
        customerService.create(customerDTO);
        redirectAttributes.addFlashAttribute(WebUtils.MSG_SUCCESS, WebUtils.getMessage("customer.create.success"));
        return "redirect:/customers";
    }

    @GetMapping("/edit/{custNo}")
    public String edit(@PathVariable(name = "custNo") final Long custNo, final Model model) {
        model.addAttribute("customer", customerService.get(custNo));
        return "customer/edit";
    }

    @PostMapping("/edit/{custNo}")
    public String edit(@PathVariable(name = "custNo") final Long custNo,
            @ModelAttribute("customer") @Valid final CustomerDTO customerDTO,
            final BindingResult bindingResult, final RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "customer/edit";
        }
        customerService.update(custNo, customerDTO);
        redirectAttributes.addFlashAttribute(WebUtils.MSG_SUCCESS, WebUtils.getMessage("customer.update.success"));
        return "redirect:/customers";
    }

    @PostMapping("/delete/{custNo}")
    public String delete(@PathVariable(name = "custNo") final Long custNo,
            final RedirectAttributes redirectAttributes) {
        customerService.delete(custNo);
        redirectAttributes.addFlashAttribute(WebUtils.MSG_INFO, WebUtils.getMessage("customer.delete.success"));
        return "redirect:/customers";
    }

}
