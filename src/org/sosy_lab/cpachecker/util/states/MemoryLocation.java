// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.util.states;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Splitter;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Ordering;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
* This class describes a location in the memory.
*/
public class MemoryLocation implements Comparable<MemoryLocation>, Serializable {

  private static final long serialVersionUID = -8910967707373729034L;
  private final String functionName;
  private final String identifier;
  private final @Nullable Long offset;

  private MemoryLocation(String pFunctionName, String pIdentifier, @Nullable Long pOffset) {
    checkNotNull(pFunctionName);
    checkNotNull(pIdentifier);

    functionName = pFunctionName;
    identifier = pIdentifier;
    offset = pOffset;
  }

  protected MemoryLocation(String pIdentifier, @Nullable Long pOffset) {
    checkNotNull(pIdentifier);

    int separatorIndex = pIdentifier.indexOf("::");
    if (separatorIndex >= 0) {
      functionName = pIdentifier.substring(0, separatorIndex);
      identifier = pIdentifier.substring(separatorIndex + 2);
    } else {
      functionName = null;
      identifier = pIdentifier;
    }
    offset = pOffset;
  }

  @Override
  public boolean equals(Object other) {

    if (this == other) {
      return true;
    }

    if (!(other instanceof MemoryLocation)) {
      return false;
    }

    MemoryLocation otherLocation = (MemoryLocation) other;

    return Objects.equals(functionName, otherLocation.functionName)
        && Objects.equals(identifier, otherLocation.identifier)
        && Objects.equals(offset, otherLocation.offset);
  }

  @Override
  public int hashCode() {
    return Objects.hash(functionName, identifier, offset);
  }

  public static MemoryLocation valueOf(String pFunctionName, String pIdentifier) {
    return new MemoryLocation(pFunctionName, pIdentifier, null);
  }

  public static MemoryLocation valueOf(String pFunctionName, String pIdentifier, long pOffset) {
    return new MemoryLocation(pFunctionName, pIdentifier, pOffset);
  }

  public static MemoryLocation valueOf(String pIdentifier, long pOffset) {
    return new MemoryLocation(pIdentifier, pOffset);
  }

  public static MemoryLocation valueOf(String pIdentifier, OptionalLong pOffset) {
    return new MemoryLocation(pIdentifier, pOffset.isPresent() ? pOffset.orElseThrow() : null);
  }

  /** Create an instance from a string that was produced by {@link #getExtendedQualifiedName()}. */
  public static MemoryLocation parseExtendedQualifiedName(String pVariableName) {

    List<String> nameParts = Splitter.on("::").splitToList(pVariableName);
    List<String> offsetParts = Splitter.on('/').splitToList(pVariableName);

    boolean isScoped = nameParts.size() == 2;
    boolean hasOffset = offsetParts.size() == 2;

    @Nullable Long offset = hasOffset ? Long.parseLong(offsetParts.get(1)) : null;

    if (isScoped) {
      String functionName = nameParts.get(0);
      String varName = nameParts.get(1);
      if (hasOffset) {
        varName = varName.replace("/" + offset, "");
      }
      return new MemoryLocation(functionName, varName, offset);

    } else {
      String varName = nameParts.get(0);
      if (hasOffset) {
        varName = varName.replace("/" + offset, "");
      }
      return new MemoryLocation(varName.replace("/" + offset, ""), offset);
    }
  }

  /**
   * Return a string that represents the full information of this class. This string should be used
   * as an opaque identifier and only be passed to {@link #parseExtendedQualifiedName(String)}.
   */
  public String getExtendedQualifiedName() {
    String variableName = isOnFunctionStack() ? (functionName + "::" + identifier) : identifier;
    if (offset == null) {
      return variableName;
    }
    return variableName + "/" + offset;
  }

  public String serialize() {
    return getExtendedQualifiedName();
  }

  public boolean isOnFunctionStack() {
    return functionName != null;
  }

  public boolean isOnFunctionStack(String pFunctionName) {
    return functionName != null && pFunctionName.equals(functionName);
  }

  public String getFunctionName() {
    return checkNotNull(functionName);
  }

  public String getIdentifier() {
    return identifier;
  }

  public boolean isReference() {
    return offset != null;
  }

  /**
   * Gets the offset of a reference. Only valid for references.
   * See {@link MemoryLocation#isReference()}.
   *
   * @return the offset of a reference.
   */
  public long getOffset() {
    checkState(offset != null, "memory location '%s' has no offset", this);
    return offset;
  }

  /** Return new instance without offset. */
  public MemoryLocation getReferenceStart() {
    checkState(isReference(), "Memory location is no reference: %s", this);
    if (functionName != null) {
      return new MemoryLocation(functionName, identifier, null);
    } else {
      return new MemoryLocation(identifier, null);
    }
  }

  @Override
  public String toString() {
    return getExtendedQualifiedName();
  }

  @Override
  public int compareTo(MemoryLocation other) {
    return ComparisonChain.start()
        .compare(functionName, other.functionName, Ordering.natural().nullsFirst())
        .compare(identifier, other.identifier)
        .compare(offset, other.offset, Ordering.natural().nullsFirst())
        .result();
  }
}
