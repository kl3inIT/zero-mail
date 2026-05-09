package com.zeromail.core.llm.persistence;

import java.util.Arrays;
import java.util.UUID;

import com.zeromail.core.llm.model.BYOKProvider;
import com.zeromail.core.shared.persistence.AbstractTenantOwnedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "tenant_byok_credentials")
public class TenantByokCredentialsEntity extends AbstractTenantOwnedEntity {

  @Convert(converter = BYOKProviderAttributeConverter.class)
  @Column(name = "provider", nullable = false, length = 32)
  private BYOKProvider provider;

  @Column(name = "endpoint", length = 512)
  private String endpoint;

  @Column(name = "model", length = 128)
  private String model;

  @Column(name = "encrypted_key", nullable = false)
  private byte[] encryptedKey;

  @Column(name = "key_version", nullable = false)
  private short keyVersion;

  protected TenantByokCredentialsEntity() {
    // Hibernate
  }

  public TenantByokCredentialsEntity(
      UUID id,
      UUID tenantId,
      BYOKProvider provider,
      String endpoint,
      String model,
      byte[] encryptedKey,
      short keyVersion) {
    super(id, tenantId);
    this.provider = provider;
    this.endpoint = endpoint;
    this.model = model;
    this.encryptedKey = copyEncryptedKey(encryptedKey);
    this.keyVersion = keyVersion;
  }

  public BYOKProvider getProvider() {
    return provider;
  }

  public String getEndpoint() {
    return endpoint;
  }

  public String getModel() {
    return model;
  }

  public byte[] getEncryptedKey() {
    return copyEncryptedKey(encryptedKey);
  }

  public short getKeyVersion() {
    return keyVersion;
  }

  public void replaceKey(byte[] encryptedKeyEnvelope, short newKeyVersion) {
    encryptedKey = copyEncryptedKey(encryptedKeyEnvelope);
    keyVersion = newKeyVersion;
  }

  public void replaceCredential(
      BYOKProvider newProvider,
      String newEndpoint,
      String newModel,
      byte[] encryptedKeyEnvelope,
      short newKeyVersion) {
    provider = newProvider;
    endpoint = newEndpoint;
    model = newModel;
    replaceKey(encryptedKeyEnvelope, newKeyVersion);
  }

  private static byte[] copyEncryptedKey(byte[] encryptedKey) {
    return encryptedKey == null ? null : Arrays.copyOf(encryptedKey, encryptedKey.length);
  }
}
