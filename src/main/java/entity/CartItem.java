package entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "cart_items")
@Data
public class CartItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cart_id")
    private Long cartId;

    @Column(name = "variant_id")
    private Long variantId;

    private Integer quantity;

    @ManyToOne
    @JoinColumn(name = "variant_id", insertable = false, updatable = false)
    private ProductVariant variant;
}