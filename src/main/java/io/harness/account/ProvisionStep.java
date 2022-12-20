/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.account;

import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;

@Data
@Builder
@FieldNameConstants(innerTypeName = "ProvisionStepKeys")
public class ProvisionStep {
  private String step;
  private boolean done;
}
