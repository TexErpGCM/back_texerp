package co.texerp.integrations.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
public class AuditEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(nullable = false)
    public Instant at = Instant.now();
    @Column(nullable = false)
    public String username;
    @Column(nullable = false)
    public String action;
    @Column(nullable = false)
    public String entity;
    public Long entityId;
    @Column(nullable = false, length = 1500)
    public String details;
    public String ip;
    @Column(name = "old_value", length = 1500)
    public String oldValue;
    @Column(name = "new_value", length = 1500)
    public String newValue;
    public boolean success = true;
}
