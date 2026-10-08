package co.texerp.integrations.repository;

import co.texerp.integrations.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    List<Role> findAllByOrderByNameAsc();

    @Query("select distinct r from Role r where r.id in :ids")
    Set<Role> findAllByIds(@Param("ids") Set<Long> ids);

    @Query("""
            select count(distinct u.id)
            from AppUser u
            join u.roles r
            where r.id = :roleId
              and u.active = true
            """)
    long countActiveUsersByRoleId(@Param("roleId") Long roleId);
}
