package entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_id", nullable = false)
    private Long shopId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "origin")
    private String origin;

    @Column(name = "batch_code", nullable = false)
    private String batchCode;

    @Column(name = "received_date", nullable = false)
    private LocalDate receivedDate;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @Column(name = "approval_status", nullable = false)
    private String approvalStatus;

    @Column(name = "selling_status", nullable = false)
    private String sellingStatus;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // Quan hệ 1-Nhiều với ProductVariant
    @OneToMany(mappedBy = "product")
    private List<ProductVariant> variants;

    // Quan hệ Nhiều-Nhiều với Category
    @ManyToMany
    @JoinTable(
            name = "product_categories",
            joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    private List<Category> categories;

}
