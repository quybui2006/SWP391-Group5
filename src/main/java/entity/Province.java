package entity;

import jakarta.persistence.*;

@Entity
@Table(name = "provinces")
public class Province {
    @Id
    @Column(length = 20)
    private String code;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    protected Province() { }

    public Province(String code, String name) { this.code = code; this.name = name; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public boolean isActive() { return active; }
}
