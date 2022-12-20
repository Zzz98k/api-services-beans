/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.azure.model.kube;

import static io.harness.azure.model.AzureConstants.KUBECFG_CURRENT_CONTEXT;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@NoArgsConstructor
@OwnedBy(HarnessTeam.CDP)
public class AzureKubeConfig {
  private List<ClustersConfig> clusters;
  private List<ContextsConfig> contexts;
  @JsonProperty(KUBECFG_CURRENT_CONTEXT) private String currentContext;
  private String kind;
  private List<UsersConfig> users;

  // harness custom field
  private String aadToken;
}
