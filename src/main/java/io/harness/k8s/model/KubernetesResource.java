/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.k8s.model;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.govern.Switch.noop;
import static io.harness.govern.Switch.unhandled;
import static io.harness.k8s.manifest.ObjectYamlUtils.encodeDot;
import static io.harness.k8s.manifest.ObjectYamlUtils.readYaml;
import static io.harness.validation.Validator.notNullCheck;

import static java.lang.String.format;
import static java.util.Objects.nonNull;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.KubernetesYamlException;
import io.harness.k8s.manifest.ObjectYamlUtils;
import io.harness.k8s.manifest.ResourceUtils;
import io.harness.k8s.model.harnesscrds.CustomDeploymentStrategyParams;
import io.harness.k8s.model.harnesscrds.DeploymentConfig;
import io.harness.k8s.model.harnesscrds.DeploymentConfigSpec;
import io.harness.k8s.model.harnesscrds.DeploymentStrategy;
import io.harness.k8s.model.harnesscrds.ExecNewPodHook;
import io.harness.k8s.model.harnesscrds.LifecycleHook;
import io.harness.k8s.model.harnesscrds.RecreateDeploymentStrategyParams;
import io.harness.k8s.model.harnesscrds.RollingDeploymentStrategyParams;
import io.harness.yaml.BooleanPatchedRepresenter;

import com.google.common.annotations.VisibleForTesting;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapEnvSource;
import io.kubernetes.client.openapi.models.V1ConfigMapKeySelector;
import io.kubernetes.client.openapi.models.V1ConfigMapProjection;
import io.kubernetes.client.openapi.models.V1ConfigMapVolumeSource;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1DaemonSet;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1EnvFromSource;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1EnvVarSource;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodTemplateSpec;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretEnvSource;
import io.kubernetes.client.openapi.models.V1SecretKeySelector;
import io.kubernetes.client.openapi.models.V1SecretProjection;
import io.kubernetes.client.openapi.models.V1SecretVolumeSource;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1StatefulSet;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeProjection;
import io.kubernetes.client.openapi.models.V1beta1CronJob;
import io.kubernetes.client.util.Yaml;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.constructor.ConstructorException;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
@OwnedBy(CDP)
public class KubernetesResource {
  private static final String MISSING_DEPLOYMENT_SPEC_MSG = "Deployment does not have spec";
  private static final String MISSING_STATEFULSET_SPEC_MSG = "StatefulSet does not have spec";
  private static final String MISSING_DEPLOYMENT_CONFIG_SPEC_MSG = "DeploymentConfig does not have spec";
  private static final String MISSING_CRON_JOB_SPEC_MSG = "CronJob does not have spec";
  private static final String YAML_CONSTRUCTION_EXCEPTION_MESSAGE_FORMAT = "%s %n%s";

  private KubernetesResourceId resourceId;
  private Object value;
  private String spec;

  public Object getField(String key) {
    return ObjectYamlUtils.getField(this.getValue(), key);
  }

  public List<Object> getFields(String key) {
    return ObjectYamlUtils.getFields(this.getValue(), key);
  }

  public KubernetesResource setField(String key, Object newValue) {
    ObjectYamlUtils.setField(this.getValue(), key, newValue);
    return this;
  }

  public KubernetesResource addLabelsInDeploymentSelector(Map<String, String> labels) {
    Object k8sResource = getK8sResource();

    if (k8sResource instanceof DeploymentConfig) {
      DeploymentConfig deploymentConfig = (DeploymentConfig) k8sResource;

      notNullCheck(MISSING_DEPLOYMENT_CONFIG_SPEC_MSG, deploymentConfig.getSpec());
      Map<String, String> selector = deploymentConfig.getSpec().getSelector();
      if (selector != null) {
        selector.putAll(labels);
      }
    } else if (k8sResource instanceof V1Deployment) {
      V1Deployment v1Deployment = (V1Deployment) k8sResource;

      notNullCheck(MISSING_DEPLOYMENT_SPEC_MSG, v1Deployment.getSpec());
      if (v1Deployment.getSpec().getSelector() == null) {
        throw new KubernetesYamlException("Deployment spec does not have selector");
      }

      Map<String, String> matchLabels = v1Deployment.getSpec().getSelector().getMatchLabels();
      if (matchLabels == null) {
        matchLabels = new HashMap<>();
      }

      matchLabels.putAll(labels);
      v1Deployment.getSpec().getSelector().setMatchLabels(matchLabels);
    } else if (k8sResource instanceof V1StatefulSet) {
      V1StatefulSet v1StatefulSet = (V1StatefulSet) k8sResource;

      notNullCheck(MISSING_STATEFULSET_SPEC_MSG, v1StatefulSet.getSpec());
      if (v1StatefulSet.getSpec().getSelector() == null) {
        throw new KubernetesYamlException("StatefulSet spec does not have selector");
      }

      Map<String, String> matchLabels = v1StatefulSet.getSpec().getSelector().getMatchLabels();
      if (matchLabels == null) {
        matchLabels = new HashMap<>();
      }

      matchLabels.putAll(labels);
      v1StatefulSet.getSpec().getSelector().setMatchLabels(matchLabels);
    } else {
      throw new InvalidRequestException(
          format("Unhandled Kubernetes resource %s while adding labels to selector", this.resourceId.getKind()));
    }

    try {
      org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml(
          new Yaml.CustomConstructor(Object.class, new LoaderOptions()), new BooleanPatchedRepresenter());
      this.spec = yaml.dump(k8sResource);
      this.value = readYaml(this.spec).get(0);
    } catch (IOException e) {
      // do nothing
      noop();
    }

    return this;
  }

  public KubernetesResource setReplicaCount(Integer replicas) {
    Object k8sResource = getK8sResource();

    if (k8sResource instanceof DeploymentConfig) {
      DeploymentConfig deploymentConfig = (DeploymentConfig) k8sResource;
      notNullCheck(MISSING_DEPLOYMENT_CONFIG_SPEC_MSG, deploymentConfig.getSpec());
      deploymentConfig.getSpec().setReplicas(replicas);
    } else if (k8sResource instanceof V1Deployment) {
      V1Deployment v1Deployment = (V1Deployment) k8sResource;
      notNullCheck(MISSING_DEPLOYMENT_SPEC_MSG, v1Deployment.getSpec());
      v1Deployment.getSpec().setReplicas(replicas);
    } else if (k8sResource instanceof V1StatefulSet) {
      V1StatefulSet v1StatefulSet = (V1StatefulSet) k8sResource;
      notNullCheck(MISSING_STATEFULSET_SPEC_MSG, v1StatefulSet.getSpec());
      v1StatefulSet.getSpec().setReplicas(replicas);
    } else {
      throw new InvalidRequestException(
          format("Unhandled Kubernetes resource %s while setting replicaCount", this.resourceId.getKind()));
    }

    try {
      org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml(
          new Yaml.CustomConstructor(Object.class, new LoaderOptions()), new BooleanPatchedRepresenter());
      this.spec = yaml.dump(k8sResource);
      this.value = readYaml(this.spec).get(0);
    } catch (IOException e) {
      // do nothing
      noop();
    }

    return this;
  }

  public Integer getReplicaCount() {
    Object k8sResource = getK8sResource();

    if (k8sResource instanceof DeploymentConfig) {
      DeploymentConfig deploymentConfig = (DeploymentConfig) k8sResource;
      notNullCheck(MISSING_DEPLOYMENT_CONFIG_SPEC_MSG, deploymentConfig.getSpec());
      return deploymentConfig.getSpec().getReplicas();
    } else if (k8sResource instanceof V1Deployment) {
      V1Deployment v1Deployment = (V1Deployment) k8sResource;
      notNullCheck(MISSING_DEPLOYMENT_SPEC_MSG, v1Deployment.getSpec());
      return v1Deployment.getSpec().getReplicas();
    } else if (k8sResource instanceof V1StatefulSet) {
      V1StatefulSet v1StatefulSet = (V1StatefulSet) k8sResource;
      notNullCheck(MISSING_STATEFULSET_SPEC_MSG, v1StatefulSet.getSpec());
      return v1StatefulSet.getSpec().getReplicas();
    } else {
      throw new InvalidRequestException(
          format("Unhandled Kubernetes resource %s while getting replicaCount", this.resourceId.getKind()));
    }
  }

  public boolean isService() {
    return StringUtils.equals(Kind.Service.name(), this.getResourceId().getKind());
  }

  public boolean isPrimaryService() {
    if (StringUtils.equals(Kind.Service.name(), this.getResourceId().getKind())) {
      return hasMetadataAnnotation(HarnessAnnotations.primaryService);
    }
    return false;
  }

  public boolean isSkipPruning() {
    return hasMetadataAnnotation(HarnessAnnotations.skipPruning);
  }

  public boolean isStageService() {
    if (StringUtils.equals(Kind.Service.name(), this.getResourceId().getKind())) {
      return hasMetadataAnnotation(HarnessAnnotations.stageService);
    }
    return false;
  }

  public boolean isLoadBalancerService() {
    if (!StringUtils.equals(Kind.Service.name(), this.getResourceId().getKind())) {
      return false;
    }

    try {
      Object k8sResource = getK8sResource();
      V1Service v1Service = (V1Service) k8sResource;
      notNullCheck("Service does not have spec", v1Service.getSpec());
      return StringUtils.equals(v1Service.getSpec().getType(), "LoadBalancer");
    } catch (KubernetesYamlException ex) {
      log.warn("Error loading YAML while checking if the service is of kind LoadBalancer. "
              + "Ignoring this exception as kubectl apply is already successful and "
              + "pipeline execution should not be marked as failed because of this. Spec: {}",
          this.getSpec(), ex);
    }
    return false;
  }

  public KubernetesResource addColorSelectorInService(String color) {
    Object k8sResource = getK8sResource();
    V1Service v1Service = (V1Service) k8sResource;

    notNullCheck("Service does not have spec", v1Service.getSpec());
    Map<String, String> selectors = v1Service.getSpec().getSelector();
    if (selectors == null) {
      selectors = new HashMap<>();
    }

    selectors.put(HarnessLabels.color, String.valueOf(color));
    v1Service.getSpec().setSelector(selectors);

    try {
      org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml(
          new Yaml.CustomConstructor(Object.class, new LoaderOptions()), new BooleanPatchedRepresenter());
      this.spec = yaml.dump(k8sResource);
      this.value = readYaml(this.spec).get(0);
    } catch (IOException e) {
      // do nothing
      noop();
    }

    return this;
  }

  public KubernetesResource transformName(UnaryOperator<Object> transformer) {
    Object k8sResource = getK8sResource();
    updateName(k8sResource, transformer);
    try {
      org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml(
          new Yaml.CustomConstructor(Object.class, new LoaderOptions()), new BooleanPatchedRepresenter());
      this.spec = yaml.dump(k8sResource);
      this.value = readYaml(this.spec).get(0);
    } catch (IOException e) {
      // do nothing
      noop();
    }
    return this;
  }

  private void updateName(Object k8sResource, UnaryOperator<Object> transformer) {
    String newName;

    Kind kind = Kind.valueOf(this.resourceId.getKind());
    switch (kind) {
      case Deployment:
        V1Deployment v1Deployment = (V1Deployment) k8sResource;
        notNullCheck("Deployment does not have metadata", v1Deployment.getMetadata());
        newName = (String) transformer.apply(v1Deployment.getMetadata().getName());
        v1Deployment.getMetadata().setName(newName);
        this.resourceId.setName(newName);
        break;

      case StatefulSet:
        V1StatefulSet v1StatefulSet = (V1StatefulSet) k8sResource;
        notNullCheck("StatefulSet does not have metadata", v1StatefulSet.getMetadata());
        newName = (String) transformer.apply(v1StatefulSet.getMetadata().getName());
        v1StatefulSet.getMetadata().setName(newName);
        this.resourceId.setName(newName);
        break;

      case ConfigMap:
        V1ConfigMap v1ConfigMap = (V1ConfigMap) k8sResource;
        notNullCheck("ConfigMap does not have metadata", v1ConfigMap.getMetadata());
        newName = (String) transformer.apply(v1ConfigMap.getMetadata().getName());
        v1ConfigMap.getMetadata().setName(newName);
        this.resourceId.setName(newName);
        break;

      case Secret:
        V1Secret v1Secret = (V1Secret) k8sResource;
        notNullCheck("Secret does not have metadata", v1Secret.getMetadata());
        newName = (String) transformer.apply(v1Secret.getMetadata().getName());
        v1Secret.getMetadata().setName(newName);
        this.resourceId.setName(newName);
        break;

      case Service:
        V1Service v1Service = (V1Service) k8sResource;
        notNullCheck("Service does not have metadata", v1Service.getMetadata());
        newName = (String) transformer.apply(v1Service.getMetadata().getName());
        v1Service.getMetadata().setName(newName);
        this.resourceId.setName(newName);
        break;

      case DeploymentConfig:
        DeploymentConfig deploymentConfig = (DeploymentConfig) k8sResource;
        notNullCheck("Deployment Config does not have metadata", deploymentConfig.getMetadata());
        newName = (String) transformer.apply(deploymentConfig.getMetadata().getName());
        deploymentConfig.getMetadata().setName(newName);
        this.resourceId.setName(newName);
        break;

      default:
        unhandled(this.resourceId.getKind());
    }
  }

  public KubernetesResource appendSuffixInName(String suffix) {
    UnaryOperator<Object> addSuffix = t -> t + suffix;
    this.transformName(addSuffix);
    return this;
  }

  public KubernetesResource addLabelsInPodSpec(Map<String, String> labels) {
    Object k8sResource = getK8sResource();
    V1PodTemplateSpec v1PodTemplateSpec = getV1PodTemplateSpec(k8sResource);
    if (v1PodTemplateSpec == null) {
      return this;
    }

    if (v1PodTemplateSpec.getMetadata() == null) {
      v1PodTemplateSpec.setMetadata(new V1ObjectMeta());
    }

    notNullCheck("PodTemplateSpec does not have metadata", v1PodTemplateSpec.getMetadata());
    Map<String, String> podLabels = v1PodTemplateSpec.getMetadata().getLabels();
    if (podLabels == null) {
      podLabels = new HashMap<>();
    }

    podLabels.putAll(labels);

    v1PodTemplateSpec.getMetadata().setLabels(podLabels);

    try {
      org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml(
          new Yaml.CustomConstructor(Object.class, new LoaderOptions()), new BooleanPatchedRepresenter());
      this.spec = yaml.dump(k8sResource);
      this.value = readYaml(this.spec).get(0);
    } catch (IOException e) {
      // do nothing
      noop();
    }
    return this;
  }

  public KubernetesResource transformConfigMapAndSecretRef(
      UnaryOperator<Object> configMapRefTransformer, UnaryOperator<Object> secretRefTransformer) {
    Object k8sResource = getK8sResource();

    updateConfigMapRef(k8sResource, configMapRefTransformer);
    updateSecretRef(k8sResource, secretRefTransformer);

    try {
      org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml(
          new Yaml.CustomConstructor(Object.class, new LoaderOptions()), new BooleanPatchedRepresenter());
      this.spec = yaml.dump(k8sResource);
      this.value = readYaml(this.spec).get(0);
    } catch (IOException e) {
      // do nothing
      noop();
    }
    return this;
  }

  public static String redactSecretValues(String spec) {
    String result = "Error in redactSecretValues. skipped.\n";

    try {
      V1Secret v1Secret = Yaml.loadAs(spec, V1Secret.class);

      final String redacted = "***";
      if (isNotEmpty(v1Secret.getData())) {
        for (Entry e : v1Secret.getData().entrySet()) {
          e.setValue(redacted);
        }
      }

      if (isNotEmpty(v1Secret.getStringData())) {
        for (Entry e : v1Secret.getStringData().entrySet()) {
          e.setValue(redacted);
        }
      }
      org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml(
          new Yaml.CustomConstructor(Object.class, new LoaderOptions()), new BooleanPatchedRepresenter());
      result = yaml.dump(v1Secret);
    } catch (Exception e) {
      // do nothing
      noop();
    }
    return result;
  }

  private V1PodTemplateSpec getV1PodTemplateSpec(Object resource) {
    Kind kind = Kind.valueOf(this.resourceId.getKind());

    switch (kind) {
      case Deployment:
        notNullCheck(MISSING_DEPLOYMENT_SPEC_MSG, ((V1Deployment) resource).getSpec());
        return ((V1Deployment) resource).getSpec().getTemplate();
      case DaemonSet:
        notNullCheck("DaemonSet does not have spec", ((V1DaemonSet) resource).getSpec());
        return ((V1DaemonSet) resource).getSpec().getTemplate();
      case StatefulSet:
        notNullCheck("StatefulSet does not have spec", ((V1StatefulSet) resource).getSpec());
        return ((V1StatefulSet) resource).getSpec().getTemplate();
      case Job:
        notNullCheck("Job does not have spec", ((V1Job) resource).getSpec());
        return ((V1Job) resource).getSpec().getTemplate();
      case DeploymentConfig:
        notNullCheck(MISSING_DEPLOYMENT_CONFIG_SPEC_MSG, ((DeploymentConfig) resource).getSpec());
        return ((DeploymentConfig) resource).getSpec().getTemplate();
      default:
        unhandled(this.resourceId.getKind());
    }

    return null;
  }

  private V1PodSpec getV1PodSpec(Object resource) {
    Kind kind = Kind.valueOf(this.resourceId.getKind());

    switch (kind) {
      case Deployment:
        notNullCheck(MISSING_DEPLOYMENT_SPEC_MSG, ((V1Deployment) resource).getSpec());
        return ((V1Deployment) resource).getSpec().getTemplate().getSpec();
      case DaemonSet:
        notNullCheck("DaemonSet does not have spec", ((V1DaemonSet) resource).getSpec());
        return ((V1DaemonSet) resource).getSpec().getTemplate().getSpec();
      case StatefulSet:
        notNullCheck("StatefulSet does not have spec", ((V1StatefulSet) resource).getSpec());
        return ((V1StatefulSet) resource).getSpec().getTemplate().getSpec();
      case Job:
        notNullCheck("Job does not have spec", ((V1Job) resource).getSpec());
        return ((V1Job) resource).getSpec().getTemplate().getSpec();
      case Pod:
        notNullCheck("Pod does not have spec", ((V1Pod) resource).getSpec());
        return ((V1Pod) resource).getSpec();
      case DeploymentConfig:
        notNullCheck(MISSING_DEPLOYMENT_CONFIG_SPEC_MSG, ((DeploymentConfig) resource).getSpec());
        return ((DeploymentConfig) resource).getSpec().getTemplate().getSpec();
      case CronJob:
        notNullCheck(MISSING_CRON_JOB_SPEC_MSG, ((V1beta1CronJob) resource).getSpec());
        return ((V1beta1CronJob) resource).getSpec().getJobTemplate().getSpec().getTemplate().getSpec();
      default:
        unhandled(this.resourceId.getKind());
    }

    return null;
  }

  @VisibleForTesting
  Object getK8sResource() {
    try {
      Kind kind = Kind.valueOf(this.resourceId.getKind());

      switch (kind) {
        case Deployment:
          return Yaml.loadAs(this.spec, V1Deployment.class);
        case DaemonSet:
          return Yaml.loadAs(this.spec, V1DaemonSet.class);
        case StatefulSet:
          return Yaml.loadAs(this.spec, V1StatefulSet.class);
        case Job:
          return Yaml.loadAs(this.spec, V1Job.class);
        case Service:
          return Yaml.loadAs(this.spec, V1Service.class);
        case Secret:
          return Yaml.loadAs(this.spec, V1Secret.class);
        case ConfigMap:
          return Yaml.loadAs(this.spec, V1ConfigMap.class);
        case Pod:
          return Yaml.loadAs(this.spec, V1Pod.class);
        case DeploymentConfig:
          return Yaml.loadAs(this.spec, DeploymentConfig.class);
        case CronJob:
          return Yaml.loadAs(this.spec, V1beta1CronJob.class);
        default:
          unhandled(this.resourceId.getKind());
          throw new KubernetesYamlException("Unhandled Kubernetes resource " + this.resourceId.getKind());
      }
    } catch (Exception ex) {
      String yamlConstructionExceptionMessage =
          ex instanceof ConstructorException ? extractMessageHeaderWithProblemMarks(ex) : "";
      String exceptionMessage = format("Failed to load spec for resource kind: %s, name: %s %n",
          this.resourceId.getKind(), this.resourceId.getName());
      throw new KubernetesYamlException(exceptionMessage + yamlConstructionExceptionMessage);
    }
  }

  private String extractMessageHeaderWithProblemMarks(Exception ex) {
    String messageHeader = ex.getMessage().split("\\{")[0];
    String problemMarkLine = Arrays.stream(((ConstructorException) ex).getProblemMark().toString().split("\\n"))
                                 .filter(line -> line.contains("line"))
                                 .collect(Collectors.joining("\n"));
    return String.format(YAML_CONSTRUCTION_EXCEPTION_MESSAGE_FORMAT, messageHeader, problemMarkLine);
  }

  private void updateConfigMapRef(Object k8sResource, UnaryOperator<Object> transformer) {
    V1PodSpec v1PodSpec = getV1PodSpec(k8sResource);
    if (v1PodSpec != null) {
      updateConfigMapRefInContainers(v1PodSpec.getContainers(), transformer);
      updateConfigMapRefInContainers(v1PodSpec.getInitContainers(), transformer);
      updateConfigMapRefInVolumes(v1PodSpec, transformer);
    }

    // Handling for DeploymentConfig
    updateRefInDeploymentConfigStrategy(k8sResource, transformer, true);
  }

  private void updateRefInDeploymentConfigStrategy(
      Object k8sResource, UnaryOperator<Object> transformer, boolean isConfigMap) {
    if (!(k8sResource instanceof DeploymentConfig)) {
      return;
    }

    DeploymentConfigSpec deploymentConfigSpec = ((DeploymentConfig) k8sResource).getSpec();
    if (deploymentConfigSpec == null) {
      return;
    }

    DeploymentStrategy strategy = deploymentConfigSpec.getStrategy();
    if (strategy == null) {
      return;
    }

    CustomDeploymentStrategyParams customParams = strategy.getCustomParams();
    if (customParams != null) {
      List<V1EnvVar> environment = customParams.getEnvironment();
      if (isNotEmpty(environment)) {
        for (V1EnvVar v1EnvVar : environment) {
          if (isConfigMap) {
            updateConfigMapInEnvVars(v1EnvVar, transformer);
          } else {
            updateSecretRefInEnvVar(v1EnvVar, transformer);
          }
        }
      }
    }

    RecreateDeploymentStrategyParams recreateParams = strategy.getRecreateParams();
    if (recreateParams != null) {
      updateRefInLifecycleHook(recreateParams.getPre(), transformer, isConfigMap);
      updateRefInLifecycleHook(recreateParams.getMid(), transformer, isConfigMap);
      updateRefInLifecycleHook(recreateParams.getPost(), transformer, isConfigMap);
    }

    RollingDeploymentStrategyParams rollingParams = strategy.getRollingParams();
    if (rollingParams != null) {
      updateRefInLifecycleHook(rollingParams.getPre(), transformer, isConfigMap);
      updateRefInLifecycleHook(rollingParams.getPost(), transformer, isConfigMap);
    }
  }

  private void updateRefInLifecycleHook(
      LifecycleHook lifecycleHook, UnaryOperator<Object> transformer, boolean isConfigMap) {
    if (lifecycleHook != null) {
      ExecNewPodHook execNewPod = lifecycleHook.getExecNewPod();
      if (execNewPod != null) {
        List<V1EnvVar> env = execNewPod.getEnv();
        if (env != null) {
          for (V1EnvVar v1EnvVar : env) {
            if (isConfigMap) {
              updateConfigMapInEnvVars(v1EnvVar, transformer);
            } else {
              updateSecretRefInEnvVar(v1EnvVar, transformer);
            }
          }
        }
      }
    }
  }

  private void updateConfigMapRefInContainers(List<V1Container> containers, UnaryOperator<Object> transformer) {
    if (isNotEmpty(containers)) {
      for (V1Container v1Container : containers) {
        notNullCheck("The container or initContainer list contains empty elements. Please remove the empty elements",
            v1Container);
        if (isNotEmpty(v1Container.getEnv())) {
          for (V1EnvVar v1EnvVar : v1Container.getEnv()) {
            updateConfigMapInEnvVars(v1EnvVar, transformer);
          }
        }

        if (isNotEmpty(v1Container.getEnvFrom())) {
          for (V1EnvFromSource v1EnvFromSource : v1Container.getEnvFrom()) {
            V1ConfigMapEnvSource v1ConfigMapRef = v1EnvFromSource.getConfigMapRef();
            if (v1ConfigMapRef != null) {
              String name = v1ConfigMapRef.getName();
              v1ConfigMapRef.setName((String) transformer.apply(name));
            }
          }
        }
      }
    }
  }

  private void updateConfigMapInEnvVars(V1EnvVar v1EnvVar, UnaryOperator<Object> transformer) {
    V1EnvVarSource v1EnvVarSource = v1EnvVar.getValueFrom();
    if (v1EnvVarSource != null) {
      V1ConfigMapKeySelector v1ConfigMapKeyRef = v1EnvVarSource.getConfigMapKeyRef();
      if (v1ConfigMapKeyRef != null) {
        String name = v1ConfigMapKeyRef.getName();
        v1ConfigMapKeyRef.setName((String) transformer.apply(name));
      }
    }
  }

  private void updateConfigMapRefInVolumes(V1PodSpec v1PodSpec, UnaryOperator<Object> transformer) {
    if (isNotEmpty(v1PodSpec.getVolumes())) {
      for (V1Volume v1Volume : v1PodSpec.getVolumes()) {
        V1ConfigMapVolumeSource v1ConfigMap = v1Volume.getConfigMap();
        if (v1ConfigMap != null) {
          String name = v1ConfigMap.getName();
          v1ConfigMap.setName((String) transformer.apply(name));
        }

        if (v1Volume.getProjected() != null && v1Volume.getProjected().getSources() != null) {
          for (V1VolumeProjection v1VolumeProjection : v1Volume.getProjected().getSources()) {
            V1ConfigMapProjection v1ConfigMapProjection = v1VolumeProjection.getConfigMap();
            if (v1ConfigMapProjection != null) {
              String name = v1ConfigMapProjection.getName();
              v1ConfigMapProjection.setName((String) transformer.apply(name));
            }
          }
        }
      }
    }
  }

  private void updateSecretRef(Object k8sResource, UnaryOperator<Object> transformer) {
    V1PodSpec v1PodSpec = getV1PodSpec(k8sResource);
    if (v1PodSpec != null) {
      updateSecretRefInContainers(v1PodSpec.getContainers(), transformer);
      updateSecretRefInContainers(v1PodSpec.getInitContainers(), transformer);
      updateSecretRefInImagePullSecrets(v1PodSpec, transformer);
      updateSecretRefInVolumes(v1PodSpec, transformer);
    }

    // Handling for DeploymentConfig
    updateRefInDeploymentConfigStrategy(k8sResource, transformer, false);
  }

  private void updateSecretRefInImagePullSecrets(V1PodSpec v1PodSpec, UnaryOperator<Object> transformer) {
    if (isNotEmpty(v1PodSpec.getImagePullSecrets())) {
      for (V1LocalObjectReference v1ImagePullSecret : v1PodSpec.getImagePullSecrets()) {
        String name = v1ImagePullSecret.getName();
        v1ImagePullSecret.setName((String) transformer.apply(name));
      }
    }
  }

  private void updateSecretRefInVolumes(V1PodSpec v1PodSpec, UnaryOperator<Object> transformer) {
    if (isNotEmpty(v1PodSpec.getVolumes())) {
      for (V1Volume v1Volume : v1PodSpec.getVolumes()) {
        V1SecretVolumeSource v1Secret = v1Volume.getSecret();
        if (v1Secret != null) {
          String name = v1Secret.getSecretName();
          v1Secret.setSecretName((String) transformer.apply(name));
        }

        if (v1Volume.getProjected() != null && v1Volume.getProjected().getSources() != null) {
          for (V1VolumeProjection v1VolumeProjection : v1Volume.getProjected().getSources()) {
            V1SecretProjection v1SecretProjection = v1VolumeProjection.getSecret();
            if (v1SecretProjection != null) {
              String name = v1SecretProjection.getName();
              v1SecretProjection.setName((String) transformer.apply(name));
            }
          }
        }
      }
    }
  }

  private void updateSecretRefInContainers(List<V1Container> containers, UnaryOperator<Object> transformer) {
    if (isNotEmpty(containers)) {
      for (V1Container v1Container : containers) {
        if (isNotEmpty(v1Container.getEnv())) {
          for (V1EnvVar v1EnvVar : v1Container.getEnv()) {
            updateSecretRefInEnvVar(v1EnvVar, transformer);
          }
        }

        if (isNotEmpty(v1Container.getEnvFrom())) {
          for (V1EnvFromSource v1EnvFromSource : v1Container.getEnvFrom()) {
            V1SecretEnvSource v1SecretRef = v1EnvFromSource.getSecretRef();
            if (v1SecretRef != null) {
              String name = v1SecretRef.getName();
              v1SecretRef.setName((String) transformer.apply(name));
            }
          }
        }
      }
    }
  }

  private void updateSecretRefInEnvVar(V1EnvVar v1EnvVar, UnaryOperator<Object> transformer) {
    V1EnvVarSource v1EnvVarSource = v1EnvVar.getValueFrom();
    if (v1EnvVarSource != null) {
      V1SecretKeySelector v1SecretKeyRef = v1EnvVarSource.getSecretKeyRef();
      if (v1SecretKeyRef != null) {
        String name = v1SecretKeyRef.getName();
        v1SecretKeyRef.setName((String) transformer.apply(name));
      }
    }
  }

  public KubernetesResource addAnnotations(Map newAnnotations) {
    Map annotations = (Map) this.getField("metadata.annotations");
    if (annotations == null) {
      annotations = new HashMap();
    }

    annotations.putAll(newAnnotations);
    return this.setField("metadata.annotations", annotations);
  }

  public KubernetesResource addLabels(Map newLabels) {
    Map labels = (Map) this.getField("metadata.labels");
    if (labels == null) {
      labels = new HashMap();
    }

    labels.putAll(newLabels);
    return this.setField("metadata.labels", labels);
  }

  public boolean isDirectApply() {
    return hasMetadataAnnotation(HarnessAnnotations.directApply);
  }

  public boolean isManagedWorkload() {
    return hasMetadataAnnotation(HarnessAnnotations.managedWorkload);
  }

  public boolean isManaged() {
    return hasMetadataAnnotation(HarnessAnnotations.managed);
  }

  private boolean hasMetadataAnnotation(String harnessAnnotation) {
    String isManaged = getMetadataAnnotationValue(harnessAnnotation);
    return StringUtils.equalsIgnoreCase(isManaged, "true");
  }

  public String getMetadataAnnotationValue(String harnessAnnotation) {
    Object o = this.getField("metadata.annotations." + encodeDot(harnessAnnotation));
    return nonNull(o) ? String.valueOf(o) : null;
  }

  /* Issue https://github.com/kubernetes/kubernetes/pull/66165 was fixed in 1.11.2.
  The issue didn't allow update to stateful set which has empty/null fields in its spec. */
  public String getSpec() {
    if (!Kind.StatefulSet.name().equals(resourceId.getKind())) {
      return spec;
    }

    try {
      org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml(
          new Yaml.CustomConstructor(Object.class, new LoaderOptions()), new BooleanPatchedRepresenter());
      return ResourceUtils.removeEmptyOrNullFields(yaml.dump(Yaml.loadAs(this.spec, V1StatefulSet.class)));
    } catch (IOException e) {
      // Return original spec
      return spec;
    }
  }
}
