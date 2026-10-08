package controller;

import entity.CartItem;
import repository.CartItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.List;

@Controller
public class CheckoutController {

    @Autowired
    private CartItemRepository cartItemRepository;

    @PostMapping("/checkout")
    public String processToCheckout(
            @RequestParam(value = "selectedItemIds", required = false) List<Long> selectedItemIds,
            Model model) {

        // Không chọn sản phẩm nào
        if (selectedItemIds == null || selectedItemIds.isEmpty()) {
            return "redirect:/cart?error=empty";
        }

        // Lấy các CartItem được chọn
        List<CartItem> checkoutItems =
                cartItemRepository.findByIdIn(selectedItemIds);

        // Tính tổng tiền sản phẩm
        BigDecimal totalItemsPrice = checkoutItems.stream()
                .filter(item ->
                        item.getVariant() != null &&
                                item.getVariant().getPrice() != null)
                .map(item -> {
                    BigDecimal price = item.getVariant().getPrice();

                    int quantity = item.getQuantity() != null
                            ? item.getQuantity()
                            : 1;

                    return price.multiply(BigDecimal.valueOf(quantity));
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Phí ship
        BigDecimal shippingFee = BigDecimal.valueOf(30000);

        // Gửi dữ liệu sang checkout.html
        model.addAttribute("checkoutItems", checkoutItems);
        model.addAttribute("totalItemsPrice", totalItemsPrice);
        model.addAttribute("shippingFee", shippingFee);

        // Tổng thanh toán
        model.addAttribute(
                "totalPayment",
                totalItemsPrice.add(shippingFee)
        );

        return "checkout";
    }
}