/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.security.encryption;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.security.encryption.EncryptionType.CUSTOM;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@OwnedBy(PL)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TargetModule(HarnessModule._980_COMMONS)
public class EncryptedDataDetail {
  private EncryptedRecordData encryptedData;
  private EncryptionConfig encryptionConfig;
  private String fieldName;

  public SecretUniqueIdentifier getIdentifier() {
    String kmsId = isNotEmpty(encryptionConfig.getUuid()) ? encryptionConfig.getUuid() : encryptedData.getKmsId();

    if (encryptionConfig.getEncryptionType() == CUSTOM) {
      return ParameterizedSecretUniqueIdentifier.builder()
          .parameters(encryptedData.getParameters())
          .kmsId(kmsId)
          .build();
    }

    if (isNotEmpty(encryptedData.getPath())) {
      return ReferencedSecretUniqueIdentifier.builder().path(encryptedData.getPath()).kmsId(kmsId).build();
    }

    return InlineSecretUniqueIdentifier.builder().encryptionKey(encryptedData.getEncryptionKey()).kmsId(kmsId).build();
  }
}
