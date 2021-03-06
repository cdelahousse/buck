/*
 * Copyright 2017-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.facebook.buck.jvm.java;

import com.facebook.buck.core.rulekey.AddToRuleKey;
import com.facebook.buck.core.rulekey.AddsToRuleKey;
import com.facebook.buck.core.rules.BuildRule;
import com.facebook.buck.core.util.immutables.BuckStyleImmutable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.immutables.value.Value;

/**
 * Information for annotation processing.
 *
 * <p>Annotation processing involves a set of processors, their classpath(s), and a few other
 * command-line options for javac. We want to be able to specify all this various information in a
 * BUCK configuration file and use it when we generate the javac command. This facilitates threading
 * the information through buck in a more descriptive package rather than passing all the components
 * separately.
 */
@Value.Immutable
@BuckStyleImmutable
abstract class AbstractAnnotationProcessingParams implements AddsToRuleKey {
  public static final AnnotationProcessingParams EMPTY = builder().build();

  @AddToRuleKey
  protected abstract ImmutableList<ResolvedJavacPluginProperties> getModernProcessors();

  @Value.NaturalOrder
  @AddToRuleKey
  public abstract ImmutableSortedSet<String> getParameters();

  @Value.Default
  @AddToRuleKey
  protected boolean getProcessOnly() {
    return false;
  }

  public boolean isEmpty() {
    return getModernProcessors().isEmpty() && getParameters().isEmpty();
  }

  public static Builder builder() {
    return new Builder();
  }

  public AnnotationProcessingParams withAbiProcessorsOnly() {
    return AnnotationProcessingParams.builder()
        .setModernProcessors(
            getModernProcessors()
                .stream()
                .filter(processor -> !processor.getDoesNotAffectAbi())
                .collect(ImmutableList.toImmutableList()))
        .setParameters(getParameters())
        .setProcessOnly(getProcessOnly())
        .build();
  }

  /** A customized Builder for AnnotationProcessingParams. */
  @org.immutables.builder.Builder.AccessibleFields
  public static class Builder extends AnnotationProcessingParams.Builder {
    private Set<String> legacyAnnotationProcessorNames = new LinkedHashSet<>();
    private List<BuildRule> legacyAnnotationProcessorDeps = new ArrayList<>();

    private List<ResolvedJavacPluginProperties> resolveLegacyProcessors() {
      if (getLegacyProcessors().isEmpty()) {
        return ImmutableList.of();
      }

      return getLegacyProcessors()
          .stream()
          .map(AbstractJavacPluginProperties::resolve)
          .collect(ImmutableList.toImmutableList());
    }

    private ImmutableList<JavacPluginProperties> getLegacyProcessors() {
      JavacPluginProperties.Builder legacySafeProcessorsBuilder =
          JavacPluginProperties.builder()
              .setCanReuseClassLoader(true)
              .setDoesNotAffectAbi(false)
              .setSupportsAbiGenerationFromSource(false)
              .setProcessorNames(legacyAnnotationProcessorNames);

      for (BuildRule dep : legacyAnnotationProcessorDeps) {
        legacySafeProcessorsBuilder.addDep(dep);
      }

      JavacPluginProperties legacySafeProcessors = legacySafeProcessorsBuilder.build();

      ImmutableList.Builder<JavacPluginProperties> resultBuilder = ImmutableList.builder();
      if (!legacySafeProcessors.isEmpty()) {
        resultBuilder.add(legacySafeProcessors);
      }

      return resultBuilder.build();
    }

    @Override
    public AnnotationProcessingParams build() {
      addAllModernProcessors(resolveLegacyProcessors());
      return super.build();
    }

    public Builder setLegacyAnnotationProcessorNames(Collection<String> annotationProcessors) {
      legacyAnnotationProcessorNames = ImmutableSet.copyOf(annotationProcessors);
      return this;
    }

    public Builder addLegacyAnnotationProcessorDeps(BuildRule dep) {
      legacyAnnotationProcessorDeps.add(dep);
      return this;
    }
  }
}
