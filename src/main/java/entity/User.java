package entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
@Getter
@Setter
@Entity
@Table(name="users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="full_name", nullable=false)
    private String fullName;
    @Column(nullable=false, unique=true, length=254)
    private String email;
    @Column(name = "password_hash")
    private String passwordHash;
    private String phone;
    @Column(nullable=false)
    private String status="ACTIVE";
    @Column(name = "email_verified_at")
    private LocalDateTime emailVerifiedAt;

}
