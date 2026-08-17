package co.texerp.integrations.repository;

import co.texerp.integrations.domain.AuditEvent;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AuditEventRepository extends StatementRepository<AuditEvent> {

    interface AuditAggregate {
        Long getTotal();
        Long getSuccessful();
        Long getFailed();
        Long getUsers();
    }

    @Query("""
            select a
            from AuditEvent a
            order by a.at desc, a.id desc
            """)
    List<AuditEvent> selectAll();

    @Query("""
            select a
            from AuditEvent a
            order by a.at desc, a.id desc
            """)
    List<AuditEvent> selectRecent(Pageable pageable);

    @Query(value = """
            select a
            from AuditEvent a
            where (:search = ''
                   or lower(coalesce(a.username, '')) like lower(concat('%', :search, '%'))
                   or lower(coalesce(a.details, '')) like lower(concat('%', :search, '%'))
                   or lower(coalesce(a.ip, '')) like lower(concat('%', :search, '%')))
              and (:action = '' or a.action = :action)
              and (:entity = '' or a.entity = :entity)
              and (:success is null or a.success = :success)
            order by a.at desc, a.id desc
            """,
            countQuery = """
            select count(a)
            from AuditEvent a
            where (:search = ''
                   or lower(coalesce(a.username, '')) like lower(concat('%', :search, '%'))
                   or lower(coalesce(a.details, '')) like lower(concat('%', :search, '%'))
                   or lower(coalesce(a.ip, '')) like lower(concat('%', :search, '%')))
              and (:action = '' or a.action = :action)
              and (:entity = '' or a.entity = :entity)
              and (:success is null or a.success = :success)
            """)
    Page<AuditEvent> selectPage(
            @Param("search") String search,
            @Param("action") String action,
            @Param("entity") String entity,
            @Param("success") Boolean success,
            Pageable pageable
    );

    @Query("""
            select count(a) as total,
                   coalesce(sum(case when a.success = true then 1 else 0 end), 0) as successful,
                   coalesce(sum(case when a.success = false then 1 else 0 end), 0) as failed,
                   count(distinct lower(coalesce(a.username, 'system'))) as users
            from AuditEvent a
            """)
    AuditAggregate selectAggregate();

    @Query("""
            select distinct a.action
            from AuditEvent a
            where a.action is not null and trim(a.action) <> ''
            order by a.action
            """)
    List<String> selectActions();

    @Query("""
            select distinct a.entity
            from AuditEvent a
            where a.entity is not null and trim(a.entity) <> ''
            order by a.entity
            """)
    List<String> selectEntities();

    @Query("""
            select count(a)
            from AuditEvent a
            """)
    long selectCount();
}
