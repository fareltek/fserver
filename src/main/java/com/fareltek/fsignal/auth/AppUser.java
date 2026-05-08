package com.fareltek.fsignal.auth;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Table("app_users")
public class AppUser implements Persistable<UUID> {

    @Id private UUID id;
    @Transient private boolean isNew;

    private String fullName;
    private String username;
    private String passwordHash;
    private String role;
    private OffsetDateTime createdAt;
    private OffsetDateTime lastLogin;
    private Integer failedAttempts;
    private OffsetDateTime lockedUntil;
    private Boolean active;

    public static AppUser create(String fullName, String username, String passwordHash, String role) {
        AppUser u = new AppUser();
        u.id = UUID.randomUUID();
        u.isNew = true;
        u.fullName = fullName;
        u.username = username;
        u.passwordHash = passwordHash;
        u.role = role;
        u.createdAt = OffsetDateTime.now();
        u.failedAttempts = 0;
        u.active = true;
        return u;
    }

    @Override public UUID    getId()  { return id; }
    @Override public boolean isNew()  { return isNew; }
    public void setId(UUID id)        { this.id = id; }

    public String getFullName()                      { return fullName; }
    public void setFullName(String v)                { this.fullName = v; }
    public String getUsername()                       { return username; }
    public void setUsername(String v)                { this.username = v; }
    public String getPasswordHash()                  { return passwordHash; }
    public void setPasswordHash(String v)            { this.passwordHash = v; }
    public String getRole()                          { return role; }
    public void setRole(String v)                    { this.role = v; }
    public OffsetDateTime getCreatedAt()             { return createdAt; }
    public void setCreatedAt(OffsetDateTime v)       { this.createdAt = v; }
    public OffsetDateTime getLastLogin()             { return lastLogin; }
    public void setLastLogin(OffsetDateTime v)       { this.lastLogin = v; }
    public Integer getFailedAttempts()               { return failedAttempts; }
    public void setFailedAttempts(Integer v)         { this.failedAttempts = v; }
    public OffsetDateTime getLockedUntil()           { return lockedUntil; }
    public void setLockedUntil(OffsetDateTime v)     { this.lockedUntil = v; }
    public Boolean getActive()                       { return active; }
    public void setActive(Boolean v)                 { this.active = v; }
}
