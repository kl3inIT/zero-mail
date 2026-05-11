package com.zeromail.core.tenant.persistence;

import java.util.UUID;

import com.zeromail.core.shared.persistence.AbstractEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "tenants")
public class TenantEntity extends AbstractEntity {

  @Column(name = "display_name", nullable = false)
  private String displayName;

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
