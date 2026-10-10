package service;

import entity.CartItem;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import repository.CartItemRepository;

import java.util.Optional;


@Service
public class CartService {

    @Autowired
    private CartItemRepository cartItemRepository;

    // Gắn @Transactional để nếu lỗi giữa chừng thì tự động rollback (hủy) toàn bộ thao tác
    @Transactional
    public void addToCart(Long cartId, Long variantId, Integer quantityToAdd) {
        Optional<CartItem> existingItem = cartItemRepository.findByCartIdAndVariantId(cartId, variantId);

        if (existingItem.isPresent()) {

            CartItem item = existingItem.get();
            item.setQuantity(item.getQuantity() + quantityToAdd);
            cartItemRepository.save(item);
        } else {

            CartItem newItem = new CartItem();
            newItem.setCartId(cartId);
            newItem.setVariantId(variantId);
            newItem.setQuantity(quantityToAdd);
            cartItemRepository.save(newItem);
        }
    }
}