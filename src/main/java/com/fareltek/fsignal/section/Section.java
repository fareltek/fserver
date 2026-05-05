package com.fareltek.fsignal.section;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;

@Table("sections")
public class Section {

    @Id
    private Integer id;
    private String name;
    private String host;
    private Integer port;
    private Boolean enabled;
    private String description;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public String sourceAddr() {
        return host + ":" + port;
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public Integer getPort() { return port; }
    public void setPort(Integer port) { this.port = port; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime v) { this.createdAt = v; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime v) { this.updatedAt = v; }
}
