/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.k8s.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class K8sDelegateTaskParams {
  String kubectlPath;
  String kubeconfigPath;
  String workingDirectory;
  String goTemplateClientPath;
  String helmPath;
  String ocPath;
  String kustomizeBinaryPath;
  boolean useLatestKustomizeVersion;
  String gcpKeyFilePath;
}
