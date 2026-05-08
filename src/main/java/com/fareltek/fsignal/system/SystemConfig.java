package com.fareltek.fsignal.system;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Table("system_config")
public class SystemConfig {

    @Id
    @Column("config_key")
    private String configKey;

    @Column("config_value")
    private String configValue;

    @Column("description")
    private String description;

    @Column("config_group")
    private String configGroup;

    @Column("requires_restart")
    private Boolean requiresRestart;

    @Column("updated_at")
    private OffsetDateTime updatedAt;

    @Column("updated_by")
    private String updatedBy;

    public String getConfigKey()        { return configKey; }
    public String getConfigValue()      { return configValue; }
    public String getDescription()      { return description; }
    public String getConfigGroup()      { return configGroup; }
    public Boolean getRequiresRestart() { return requiresRestart; }
    public OffsetDateTime getUpdatedAt(){ return updatedAt; }
    public String getUpdatedBy()        { return updatedBy; }

    public void setConfigKey(String v)       { this.configKey = v; }
    public void setConfigValue(String v)     { this.configValue = v; }
    public void setDescription(String v)     { this.description = v; }
    public void setConfigGroup(String v)     { this.configGroup = v; }
    public void setRequiresRestart(Boolean v){ this.requiresRestart = v; }
    public void setUpdatedAt(OffsetDateTime v){ this.updatedAt = v; }
    public void setUpdatedBy(String v)       { this.updatedBy = v; }
}
