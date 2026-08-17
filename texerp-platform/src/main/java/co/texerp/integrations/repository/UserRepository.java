package co.texerp.integrations.repository;


import co.texerp.integrations.domain.AppUser;
import co.texerp.integrations.domain.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<AppUser, Long>, JpaSpecificationExecutor<AppUser> {

    interface UserAggregate {
        Long getTotal();

        Long getActive();

        Long getAdministrators();

        Long getAnalysts();
    }

    @Query("""
            select u
            from AppUser u
            order by u.name, u.id
            """)
    List<AppUser> selectAll();

    @Query(value = """
            select u
            from AppUser u
            where (:search = ''
                   or lower(u.name) like lower(concat('%', :search, '%'))
                   or lower(u.email) like lower(concat('%', :search, '%')))
            order by u.name, u.id
            """,
            countQuery = """
                    select count(u)
                    from AppUser u
                    where (:search = ''
                           or lower(u.name) like lower(concat('%', :search, '%'))
                           or lower(u.email) like lower(concat('%', :search, '%')))
                    """)
    Page<AppUser> selectPage(@Param("search") String search, Pageable pageable);

    @Query("""
            select u
            from AppUser u
            where u.id = :id
            """)
    Optional<AppUser> selectById(@Param("id") Long id);

    @Query("""
            select u
            from AppUser u
            where lower(trim(u.email)) = lower(trim(:email))
            """)
    Optional<AppUser> selectByEmail(@Param("email") String email);

    @Query("""
            select count(u) as total,
                   coalesce(sum(case when u.active = true then 1 else 0 end), 0) as active,
                   coalesce(sum(case when u.role = co.texerp.integrations.domain.Role.ADMINISTRADOR then 1 else 0 end), 0) as administrators,
                   coalesce(sum(case when u.role = co.texerp.integrations.domain.Role.ANALISTA then 1 else 0 end), 0) as analysts
            from AppUser u
            """)
    UserAggregate selectAggregate();

    @Query("""
            select count(u)
            from AppUser u
            """)
    long selectCount();

    @Query("""
            select count(u)
            from AppUser u
            where lower(trim(u.email)) = lower(trim(:email))
              and (:excludeId is null or u.id <> :excludeId)
            """)
    long selectEmailCount(@Param("email") String email, @Param("excludeId") Long excludeId);

    @Query("""
            select count(u)
            from AppUser u
            where u.role = :role and u.active = true
            """)
    long selectActiveCountByRole(@Param("role") Role role);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update AppUser u
            set u.lastLoginAt = :at,
                u.updatedAt = :at
            where lower(trim(u.email)) = lower(trim(:email))
            """)
    int updateLastLoginAt(@Param("email") String email, @Param("at") Instant at);

    @Modifying(flushAutomatically = true)
    @Query("""
            delete from AppUser u
            where u.id = :id
            """)
    int deleteByIdStatement(@Param("id") Long id);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByUsernameIgnoreCaseAndIdNot(
            String username,
            Long id
    );

    boolean existsByEmailIgnoreCaseAndIdNot(
            String email,
            Long id
    );
}
