package repository;

import entity.UserRole;
import entity.UserRoleId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {
    List<UserRole> findByUser_Id(Long userId);

    boolean existsByUser_IdAndRole_Code(Long userId, String roleCode);

    @org.springframework.data.jpa.repository.Query("SELECT ur.user FROM UserRole ur WHERE ur.role.code = :roleCode")
    List<entity.User> findUsersByRoleCode(@org.springframework.data.repository.query.Param("roleCode") String roleCode);
}
