package com.example.baget.orders;

import com.example.baget.customer.CustomerService;
import com.example.baget.items.Items;
import com.example.baget.items.ItemsDTO;
import com.example.baget.items.ItemsService;
import com.example.baget.util.WebUtils;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;


@Controller
@RequestMapping("/orders")
public class OrdersController {

    private final OrdersService ordersService;
    private final CustomerService customerService;
    private final ItemsService itemsService;

    public OrdersController(final OrdersService ordersService, CustomerService customerService, ItemsService itemsService) {
        this.ordersService = ordersService;
        this.customerService = customerService;
        this.itemsService = itemsService;
    }

    @GetMapping
//    public String list(final Model model) {
//        model.addAttribute("orderses", ordersService.findAll());
//        return "orders/list";
//    }

    public String list(Model model,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<OrdersDTO> ordersPage = ordersService.findAll(pageable);

        model.addAttribute("ordersPage", ordersPage);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", ordersPage.getTotalPages());
        return "orders/list";
    }


    @GetMapping("/add")
    public String add(Model model, final OrdersDTO ordersDTO) {
        model.addAttribute("orders", ordersDTO);
        model.addAttribute("customers", customerService.findAll());
//        model.addAttribute("items", itemsService.findAll());
        return "orders/add";
    }

    @PostMapping("/add")
    public String add(@ModelAttribute("orders") @Valid final OrdersDTO ordersDTO,
                      final BindingResult bindingResult, final RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "orders/add";
        }
        ordersService.create(ordersDTO);
        redirectAttributes.addFlashAttribute(WebUtils.MSG_SUCCESS, WebUtils.getMessage("orders.create.success"));
        return "redirect:/orders";
    }

    @GetMapping("/add/new")
    public String addNew(Model model, final OrdersDTO ordersDTO) {
        model.addAttribute("orders", ordersDTO);
        model.addAttribute("customers", customerService.findAll());
        model.addAttribute("requestUri", "/orders/add/new");
        return "orders/add_new";
    }

    @PostMapping("/add/new")
    public String addNew(@ModelAttribute("orders") @Valid final OrdersDTO ordersDTO,
                      final BindingResult bindingResult, final RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "orders/add_new";
        }
        Long orderNo = ordersService.create(ordersDTO);
        redirectAttributes.addFlashAttribute(WebUtils.MSG_SUCCESS, WebUtils.getMessage("orders.create.success"));
        return "redirect:/orders/edit/" + orderNo;
    }

    @GetMapping("/edit/{orderNo}")
    public String edit(@PathVariable(name = "orderNo") final Long orderNo, final Model model) {
        model.addAttribute("orders", ordersService.get(orderNo));
        model.addAttribute("customers", customerService.get(ordersService.get(orderNo).getCustNo()));
        model.addAttribute("items", itemsService.findByOrderNo(orderNo));
        return "orders/edit";
    }

    @PostMapping("/edit/{orderNo}")
    public String edit(@PathVariable(name = "orderNo") final Long orderNo,
            @ModelAttribute("orders") @Valid final OrdersDTO ordersDTO,
            final BindingResult bindingResult, final RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "orders/edit";
        }
        ordersService.update(orderNo, ordersDTO);
        redirectAttributes.addFlashAttribute(WebUtils.MSG_SUCCESS, WebUtils.getMessage("orders.update.success"));
        return "redirect:/orders";
    }

    @PostMapping("/delete/{orderNo}")
    public String delete(@PathVariable(name = "orderNo") final Long orderNo,
            final RedirectAttributes redirectAttributes) {
        ordersService.delete(orderNo);
        redirectAttributes.addFlashAttribute(WebUtils.MSG_INFO, WebUtils.getMessage("orders.delete.success"));
        return "redirect:/orders";
    }

    @PostMapping("/addItem")
    public String addItem(@RequestParam("orderNo") Long orderNo, @RequestBody final ItemsDTO itemDTO, Model model) {
        // Знайти замовлення за orderNo
        OrdersDTO order = ordersService.get(orderNo);
        itemDTO.setOrderNo(orderNo);
        itemDTO.setItemNo(order.getItems().stream().count()+1);
        order.getItems().add(itemDTO);

        // Зберегти оновлене замовлення
        ordersService.update(orderNo, order);

        // Оновити модель і повернути оновлений список елементів
        model.addAttribute("orders", order);
        return "orders/edit :: itemsContainer";
    }



    @PostMapping("/removeItem")
    public String removeItem(@RequestParam("orderNo") Long orderNo, @RequestParam("itemIndex") int itemIndex, Model model) {
        OrdersDTO order = ordersService.get(orderNo);

        // Remove the item by its index
        if (itemIndex >= 0 && itemIndex < order.getItems().size()) {
            order.getItems().remove(itemIndex);
        }

        // Add the updated order to the model
        model.addAttribute("orders", order);

        // Return the fragment of HTML that represents the updated items list
        return "orders/edit :: itemRow";
    }

}
