/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.gitsync.common.dtos;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import io.swagger.v3.oas.annotations.media.Schema;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;

@OwnedBy(HarnessTeam.DX)
@Value
@Builder
@FieldNameConstants(innerTypeName = "SaasGitDTOKeys")
@Schema(name = "SaasGit", description = "This contains a boolean which specifies whether the repoURL is SaasGit or not")
public class SaasGitDTO {
  @Schema(description = "Specifies whether the repoURL is SaasGit or not") @NotNull boolean isSaasGit;
}
