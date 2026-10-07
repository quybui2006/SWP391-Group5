package controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class CustomerAccountController {

    @GetMapping("/customer/account/address")
    public String addressBook() {
        return "customer/address-book";
    }
}
