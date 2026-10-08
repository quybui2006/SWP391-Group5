package repository;
import entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    // Lấy danh sách sản phẩm trong giỏ của cart_id (Giả sử user_id = 1 có cart_id = 1)
    List<CartItem> findByCartId(Long cartId);

    // Lấy các mục được chọn khi bấm nút Mua Hàng
    List<CartItem> findByIdIn(List<Long> ids);
}