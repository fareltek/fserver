package com.fareltek.fsignal.db;

import com.fareltek.fsignal.tcp.Fa51Parser;
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

    @Id private UUID id;
    @Transient private boolean isNew;

    private OffsetDateTime eventTime;
    private OffsetDateTime receiveTime;
    private String  sourceAddr;
    private String  messageType;
    private String  deviceType;
    private Integer deviceId;
    private String  severity;
    private Integer eventCode;
    private Integer eventData;
    private Integer eventFlags;
    private ByteBuffer rawPayload;
    private String  description;
    private Boolean acknowledged;
    private String  acknowledgedBy;
    private OffsetDateTime acknowledgedTime;

    /** Factory: parses FA·51 frame if valid, falls back to RAW_DATA. */
    public static SafetyEvent fromRaw(String sourceAddr, byte[] rawData, String hex) {
        SafetyEvent e = new SafetyEvent();
        e.id          = UUID.randomUUID();
        e.isNew       = true;
        e.receiveTime = OffsetDateTime.now(ZoneOffset.UTC);
        e.sourceAddr  = sourceAddr;
        e.rawPayload   = ByteBuffer.wrap(rawData);
        e.acknowledged = false;

        Fa51Parser.ParsedPacket pkt = Fa51Parser.parse(rawData);
        if (pkt != null) {
            e.messageType = pkt.messageType();
            e.severity    = pkt.severity();
            e.deviceId    = pkt.sourceId();
            e.eventCode   = pkt.eventCode();
            e.eventData   = pkt.eventData();
            e.eventFlags  = pkt.eventFlags();
            e.deviceType  = "FA51-CPU";
            e.description = Fa51Parser.describe(pkt);
        } else {
            e.messageType = "RAW_DATA";
            e.severity    = "INFO";
            String h = hex != null ? hex : java.util.HexFormat.ofDelimiter(" ").withUpperCase().formatHex(rawData);
            e.description = rawData.length + " byte: " + (h.length() > 80 ? h.substring(0, 80) + "…" : h);
        }
        e.eventTime = OffsetDateTime.now(ZoneOffset.UTC);
        return e;
    }

    @Override public UUID    getId()   { return id; }
    @Override public boolean isNew()   { return isNew; }
    public void setId(UUID id)         { this.id = id; }

    public OffsetDateTime getEventTime()                        { return eventTime; }
    public void setEventTime(OffsetDateTime v)                  { this.eventTime = v; }
    public OffsetDateTime getReceiveTime()                      { return receiveTime; }
    public void setReceiveTime(OffsetDateTime v)                { this.receiveTime = v; }
    public String  getSourceAddr()                              { return sourceAddr; }
    public void setSourceAddr(String v)                         { this.sourceAddr = v; }
    public String  getMessageType()                             { return messageType; }
    public void setMessageType(String v)                        { this.messageType = v; }
    public String  getDeviceType()                              { return deviceType; }
    public void setDeviceType(String v)                         { this.deviceType = v; }
    public Integer getDeviceId()                                { return deviceId; }
    public void setDeviceId(Integer v)                          { this.deviceId = v; }
    public String  getSeverity()                                { return severity; }
    public void setSeverity(String v)                           { this.severity = v; }
    public Integer getEventCode()                               { return eventCode; }
    public void setEventCode(Integer v)                         { this.eventCode = v; }
    public Integer getEventData()                               { return eventData; }
    public void setEventData(Integer v)                         { this.eventData = v; }
    public Integer getEventFlags()                              { return eventFlags; }
    public void setEventFlags(Integer v)                        { this.eventFlags = v; }
    public ByteBuffer getRawPayload()                           { return rawPayload; }
    public void setRawPayload(ByteBuffer v)                     { this.rawPayload = v; }
    public String  getDescription()                             { return description; }
    public void setDescription(String v)                        { this.description = v; }
    public Boolean getAcknowledged()                            { return acknowledged; }
    public void setAcknowledged(Boolean v)                      { this.acknowledged = v; }
    public String  getAcknowledgedBy()                          { return acknowledgedBy; }
    public void setAcknowledgedBy(String v)                     { this.acknowledgedBy = v; }
    public OffsetDateTime getAcknowledgedTime()                 { return acknowledgedTime; }
    public void setAcknowledgedTime(OffsetDateTime v)           { this.acknowledgedTime = v; }
}
