/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.k8s.model;

public enum HelmVersion {
  V2,
  V3,
  V380;

  public static boolean isHelmV3(HelmVersion helmVersion) {
    return V3.equals(helmVersion) || V380.equals(helmVersion);
  }
  public static HelmVersion fromString(String helmVersion) {
    if (helmVersion == null) {
      return V2;
    }
    switch (helmVersion) {
      case "V3":
        return V3;
      case "V380":
        return V380;
      default:
        return V2;
    }
  }
}
