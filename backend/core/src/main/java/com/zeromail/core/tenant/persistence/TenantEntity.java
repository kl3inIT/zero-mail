package com.zeromail.core.tenant.persistence;

import com.zeromail.core.shared.persistence.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "tenants")
public class TenantEntity extends AbstractEntity {

    public static final String DEFAULT_TIME_ZONE = "Asia/Ho_Chi_Minh";

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "time_zone", nullable = false, length = 64)
    private String timeZone = DEFAULT_TIME_ZONE;

    @Column(name = "triage_paused", nullable = false)
    private boolean triagePaused = false;

    @Column(name = "triage_shadow_mode", nullable = false)
    private boolean triageShadowMode = false;

    protected TenantEntity() {}

    public TenantEntity(UUID id, String displayName) {
        super(id);
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
    }

    public boolean isTriagePaused() {
        return triagePaused;
    }

    public void setTriagePaused(boolean triagePaused) {
        this.triagePaused = triagePaused;
    }

    public boolean isTriageShadowMode() {
        return triageShadowMode;
    }

    public void setTriageShadowMode(boolean triageShadowMode) {
        this.triageShadowMode = triageShadowMode;
    }
}
