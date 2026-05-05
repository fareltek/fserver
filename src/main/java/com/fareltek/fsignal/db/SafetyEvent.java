package com.fareltek.fsignal.db;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

import java.nio.ByteBuffer;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Table("safety_events")
public class SafetyEvent implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean isNew;
    private OffsetDateTime eventTime;
    private OffsetDateTime receiveTime;
    private String sourceAddr;
    private Integer sequence;
    private Integer sourceId;
    private String messageType;
    private String deviceType;
    private Integer deviceId;
    private String severity;
    private Integer eventCode;
    private Integer eventData;
    private Integer eventFlags;
    private ByteBuffer rawPayload;
    private String description;
    private Boolean acknowledged;
    private String acknowledgedBy;
    private OffsetDateTime acknowledgedTime;

    public static SafetyEvent fromRaw(String sourceAddr, byte[] rawData, String hex) {
        SafetyEvent e = new SafetyEvent();
        e.id = UUID.randomUUID();
        e.isNew = true;
        e.eventTime = OffsetDateTime.now(ZoneOffset.UTC);
        e.receiveTime = OffsetDateTime.now(ZoneOffset.UTC);
        e.sourceAddr = sourceAddr;
        e.messageType = "RAW_DATA";
        e.severity = "INFO";
        e.rawPayload = ByteBuffer.wrap(rawData);
        e.description = hex;
        e.acknowledged = false;
        return e;
    }

    @Override public UUID getId() { return id; }
    @Override public boolean isNew() { return isNew; }
    public void setId(UUID id) { this.id = id; }

    public OffsetDateTime getEventTime() { return eventTime; }
    public void setEventTime(OffsetDateTime eventTime) { this.eventTime = eventTime; }

    public OffsetDateTime getReceiveTime() { return receiveTime; }
    public void setReceiveTime(OffsetDateTime receiveTime) { this.receiveTime = receiveTime; }

    public String getSourceAddr() { return sourceAddr; }
    public void setSourceAddr(String sourceAddr) { this.sourceAddr = sourceAddr; }

    public Integer getSequence() { return sequence; }
    public void setSequence(Integer sequence) { this.sequence = sequence; }

    public Integer getSourceId() { return sourceId; }
    public void setSourceId(Integer sourceId) { this.sourceId = sourceId; }

    public String getMessageType() { return messageType; }
    public void setMessageType(String messageType) { this.messageType = messageType; }

    public String getDeviceType() { return deviceType; }
    public void setDeviceType(String deviceType) { this.deviceType = deviceType; }

    public Integer getDeviceId() { return deviceId; }
    public void setDeviceId(Integer deviceId) { this.deviceId = deviceId; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public Integer getEventCode() { return eventCode; }
    public void setEventCode(Integer eventCode) { this.eventCode = eventCode; }

    public Integer getEventData() { return eventData; }
    public void setEventData(Integer eventData) { this.eventData = eventData; }

    public Integer getEventFlags() { return eventFlags; }
    public void setEventFlags(Integer eventFlags) { this.eventFlags = eventFlags; }

    public ByteBuffer getRawPayload() { return rawPayload; }
    public void setRawPayload(ByteBuffer rawPayload) { this.rawPayload = rawPayload; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Boolean getAcknowledged() { return acknowledged; }
    public void setAcknowledged(Boolean acknowledged) { this.acknowledged = acknowledged; }

    public String getAcknowledgedBy() { return acknowledgedBy; }
    public void setAcknowledgedBy(String acknowledgedBy) { this.acknowledgedBy = acknowledgedBy; }

    public OffsetDateTime getAcknowledgedTime() { return acknowledgedTime; }
    public void setAcknowledgedTime(OffsetDateTime acknowledgedTime) { this.acknowledgedTime = acknowledgedTime; }
}
