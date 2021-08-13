// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2021 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.util.smg;

import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSortedMap;
import java.math.BigInteger;
import java.util.Comparator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import org.sosy_lab.common.collect.PathCopyingPersistentTreeMap;
import org.sosy_lab.common.collect.PersistentMap;
import org.sosy_lab.cpachecker.cpa.smg.util.PersistentSet;
import org.sosy_lab.cpachecker.util.smg.graph.SMGDoublyLinkedListSegment;
import org.sosy_lab.cpachecker.util.smg.graph.SMGHasValueEdge;
import org.sosy_lab.cpachecker.util.smg.graph.SMGObject;
import org.sosy_lab.cpachecker.util.smg.graph.SMGPointsToEdge;
import org.sosy_lab.cpachecker.util.smg.graph.SMGTargetSpecifier;
import org.sosy_lab.cpachecker.util.smg.graph.SMGValue;
import org.sosy_lab.cpachecker.util.smg.util.SMGandValue;

/**
 * Class to represent a immutable bipartite symbolic memory graph. Manipulating methods return a
 * modified copy but do not modify a certain instance. Consists of (SMG-)objects, values, edges from
 * the objects to the values (has-value edges), edges from the values to objects (points-to edges)
 * and labelling functions (to get the kind, nesting level, size etc. of objects etc.)
 */
public class SMG {
  // TODO I don't like using utility implementations of the old SMG analysis
  private final PersistentMap<SMGObject, Boolean> smgObjects;
  private final PersistentSet<SMGValue> smgValues;
  private final PersistentMap<SMGObject, PersistentSet<SMGHasValueEdge>> hasValueEdges;
  private final PersistentMap<SMGValue, SMGPointsToEdge> pointsToEdges;

  /** Creates a new, empty SMG */
  public SMG() {
    hasValueEdges = PathCopyingPersistentTreeMap.of();
    smgValues = PersistentSet.of(SMGValue.zeroValue());
    PersistentMap<SMGObject, Boolean> smgObjectsTmp = PathCopyingPersistentTreeMap.of();
    smgObjects = smgObjectsTmp.putAndCopy(SMGObject.nullInstance(), false);
    SMGPointsToEdge nullPointer =
        new SMGPointsToEdge(getNullObject(), BigInteger.ZERO, SMGTargetSpecifier.IS_REGION);
    PersistentMap<SMGValue, SMGPointsToEdge> pointsToEdgesTmpMap = PathCopyingPersistentTreeMap.of();
    pointsToEdges = pointsToEdgesTmpMap.putAndCopy(SMGValue.zeroValue(), nullPointer);

  }

  private SMG(
      PersistentMap<SMGObject, Boolean> pSmgObjects,
      PersistentSet<SMGValue> pSmgValues,
      PersistentMap<SMGObject, PersistentSet<SMGHasValueEdge>> pHasValueEdges,
      PersistentMap<SMGValue, SMGPointsToEdge> pPointsToEdges) {
    smgObjects = pSmgObjects;
    smgValues = pSmgValues;
    hasValueEdges = pHasValueEdges;
    pointsToEdges = pPointsToEdges;
  }

  /**
   * Creates a copy of the SMG an adds the given object.
   *
   * @param pObject - the object to be added
   * @return a modified copy of the SMG
   */
  public SMG copyAndAddObject(SMGObject pObject) {
    return new SMG(smgObjects.putAndCopy(pObject, true), smgValues, hasValueEdges, pointsToEdges);
  }

  /**
   * Creates a copy of the SMG an adds the given value.
   *
   * @param pValue - the object to be added
   * @return a modified copy of the SMG
   */
  public SMG copyAndAddValue(SMGValue pValue) {
    return new SMG(smgObjects, smgValues.addAndCopy(pValue), hasValueEdges, pointsToEdges);
  }

  /**
   * Creates a copy of the SMG an adds the given has value edge.
   *
   * @param edge - the edge to be added
   * @param source - the source object
   * @return a modified copy of the SMG
   */
  public SMG copyAndAddHVEdge(SMGHasValueEdge edge, SMGObject source) {

    if (hasValueEdges.containsKey(source) && hasValueEdges.get(source).contains(edge)) {
      return this;
    }

    PersistentSet<SMGHasValueEdge> edges = hasValueEdges.getOrDefault(source, PersistentSet.of());
    edges = edges.addAndCopy(edge);
    return new SMG(smgObjects, smgValues, hasValueEdges.putAndCopy(source, edges), pointsToEdges);
  }

  /**
   * Creates a copy of the SMG an adds the given points to edge.
   *
   * @param edge - the edge to be added
   * @param source - the source value
   * @return a modified copy of the SMG
   */
  public SMG copyAndAddPTEdge(SMGPointsToEdge edge, SMGValue source) {

    if (pointsToEdges.containsKey(source) && pointsToEdges.get(source).equals(edge)) {
      return this;
    }

    return new SMG(smgObjects, smgValues, hasValueEdges, pointsToEdges.putAndCopy(source, edge));
  }

  /**
   * Creates a copy of the SMG an adds the given has value edges.
   *
   * @param edges - the edges to be added
   * @param source - the source object
   * @return a modified copy of the SMG
   */
  public SMG copyAndSetHVEdges(PersistentSet<SMGHasValueEdge> edges, SMGObject source) {

    return new SMG(smgObjects, smgValues, hasValueEdges.putAndCopy(source, edges), pointsToEdges);
  }

  /**
   * Creates a copy of the SMG and adds the given points to edge.
   *
   * @param edge - the edge to be added
   * @param source - the source value
   * @return a modified copy of the SMG
   */
  public SMG copyAndSetPTEdges(SMGPointsToEdge edge, SMGValue source) {
    return new SMG(smgObjects, smgValues, hasValueEdges, pointsToEdges.putAndCopy(source, edge));
  }

  /**
   * Creates a copy of the SMG and replaces a given edge with an other.
   *
   * @param pSmgObject the source
   * @param pOldEdge to be replaces
   * @param pNewEdge replacement
   * @return a copy of the graph with the replaced edge
   */
  public SMG copyAndReplaceHVEdge(
      SMGObject pSmgObject,
      SMGHasValueEdge pOldEdge,
      SMGHasValueEdge pNewEdge) {
    PersistentSet<SMGHasValueEdge> objEdges =
        hasValueEdges.get(pSmgObject).removeAndCopy(pOldEdge).addAndCopy(pNewEdge);
    return copyAndSetHVEdges(objEdges, pSmgObject);
  }

  /**
   * Creates a copy of the SMG and replaces given object by a given new.
   *
   * @param pOldObject - the object to be replaced
   * @param pNewObject - the replacement
   * @return a modified copy
   */
  public SMG copyAndReplaceObject(SMGObject pOldObject, SMGObject pNewObject) {
    PersistentSet<SMGHasValueEdge> edges = hasValueEdges.get(pOldObject);
    // replace has value edges
    PersistentMap<SMGObject, PersistentSet<SMGHasValueEdge>> newHVEdges =
        hasValueEdges.removeAndCopy(pOldObject).putAndCopy(pNewObject, edges);
    // replace points to edges
    PersistentMap<SMGValue, SMGPointsToEdge> newPointsToEdges = pointsToEdges;

    for (Map.Entry<SMGValue, SMGPointsToEdge> oldEntry : pointsToEdges.entrySet()) {
      if (pOldObject.equals(oldEntry.getValue().pointsTo())) {
        SMGPointsToEdge newEdge =
            new SMGPointsToEdge(pNewObject, oldEntry.getValue().getOffset(), oldEntry.getValue().targetSpecifier());
        newPointsToEdges = pointsToEdges.putAndCopy(oldEntry.getKey(), newEdge);
      }
    }

    //replace object
    PersistentMap<SMGObject, Boolean> newObjects =
        smgObjects.removeAndCopy(pOldObject).putAndCopy(pNewObject, true);

    return new SMG(newObjects, smgValues, newHVEdges, newPointsToEdges);
  }


  public SMG copyAndInvalidateObject(SMGObject pObject) {
    PersistentMap<SMGObject, Boolean> newObjects = smgObjects.putAndCopy(pObject, false);
    PersistentMap<SMGObject, PersistentSet<SMGHasValueEdge>> newHVEdges =
        hasValueEdges.removeAndCopy(pObject);
    return new SMG(newObjects, smgValues, newHVEdges, pointsToEdges);
  }

  /**
   * Returns the static null object.
   *
   * @return The null SMGObject.
   */
  public SMGObject getNullObject() {
    return SMGObject.nullInstance();
  }

  /**
   * Returns all SMGObjects associated with this SMG in a set.
   *
   * @return The set of SMGObjects associated with this SMG.
   */
  public Set<SMGObject> getObjects() {
    return smgObjects.keySet();
  }

  /**
   * Returns all SMGValues associated with this SMG in a set.
   *
   * @return The set of SMGValues associated with this SMG.
   */
  public Set<SMGValue> getValues() {
    return smgValues;
  }

  /**
   * Returned the set of SMGHasValueEdges associated with the region that is specified by the
   * entered SMGObject. The region is an interval [object.offset, object.offset + object.size).
   *
   * @param pRegion SMGObject for whos region one wants the SMGHasValueEdges.
   * @return The set of SMGHasValueEdges associated with the region.
   */
  public Set<SMGHasValueEdge> getEdges(SMGObject pRegion) {
    return hasValueEdges.getOrDefault(pRegion, PersistentSet.of());
  }

  /**
   * This is a general method to get a single SMGHasValueEdges by object and a filter predicate.
   * Examples:
   *
   * <p>{@code Predicate<SMGHasValueEdge> filterOffset = o -> o.getOffset().equals(offset);} Returns
   * a possible SMGHasValueEdge with the offset entered.
   *
   * <p>{@code o -> o.getOffset().equals(offset) && o.getSizeInBits().equals(sizeInBits);} Returns a
   * possible SMGHasValueEdge with the offset and size entered.
   *
   * @param object SMGObject for which the SMGHasValueEdge are searched.
   * @param filter The filter predicate for SMGHasValueEdges.
   * @return Either an empty Optional if there is no such SMGHasValueEdge, or an Optional with some
   *     edge for the entered filter.
   */
  public Optional<SMGHasValueEdge> getHasValueEdgeByPredicate(
      SMGObject object, Predicate<SMGHasValueEdge> filter) {
    // TODO: Are multiple values possible for the same filter? If yes, create another method to
    // return all of them.
    return hasValueEdges.get(object).stream().filter(filter).findAny();
  }

  /**
   * This is a general method to get a all SMGHasValueEdges by object and a filter predicate.
   * Examples:
   *
   * <p>
   * {@code Predicate<SMGHasValueEdge> filterOffset = o -> o.getOffset().equals(offset);} Returns
   * all existing SMGHasValueEdge with the offset entered.
   *
   * <p>
   * {@code o -> o.getOffset().equals(offset) && o.getSizeInBits().equals(sizeInBits);} Returns all
   * existing SMGHasValueEdge with the offset and size entered.
   *
   * @param object SMGObject for which the SMGHasValueEdge are searched.
   * @param filter The filter predicate for SMGHasValueEdges.
   * @return A FluentIterable with all edges matching the specified filter
   */
  public FluentIterable<SMGHasValueEdge>
      getHasValueEdgesByPredicate(SMGObject object, Predicate<SMGHasValueEdge> filter) {
    return FluentIterable.from(hasValueEdges.get(object)).filter(filter);
  }

  /**
   * Read a value of an object in the field specified by offset and size. This returns a read
   * re-interpretation of the field, which means it returns either the symbolic value that is
   * present, 0 if the field is covered with nullified blocks or an unknown value. This is not
   * guaranteed to be completely accurate! TODO: Do we have to check for nullified blocks even if a
   * value is defined?
   *
   * @param object The object from which is to be read.
   * @param offset The offset from which on the field in the object is to be read.
   * @param sizeInBits Size in bits, specifying the size to be read from the offset.
   * @return A updated SMG and the SMTValue that is a read re-interpretation of the field in the
   *     object. May be 0, a symbolic value or a new unknown symbolic value.
   */
  public SMGandValue readValue(SMGObject object, BigInteger offset, BigInteger sizeInBits) {
    // Check that our field is inside the object: offset + sizeInBits <= size(object)
    assert (offset.add(sizeInBits).compareTo(object.getSize()) <= 0);

    // let v := H(o, of, t)
    // TODO: Currently getHasValueEdgeByOffsetAndSize returns any edge it finds.
    // Check if multiple edges may exists for the same offset and size!
    Predicate<SMGHasValueEdge> filterByOffsetAndSize =
        o -> o.getOffset().equals(offset) && o.getSizeInBits().equals(sizeInBits);
    Optional<SMGHasValueEdge> maybeValue =
        this.getHasValueEdgeByPredicate(object, filterByOffsetAndSize);

    // if v != undefined then return (smg, v)
    if (maybeValue.isPresent()) {
      return new SMGandValue(this, maybeValue.orElseThrow().hasValue());
    }

    // if the field to be read is covered by nullified blocks, i.e. if
    // forall . of <= i < of +  size(t) exists . e element H(o, of, t): i element I(e),
    // let v := 0. Otherwise extend V by a fresh value node v.
    if (this.isCoveredByNullifiedBlocks(object, offset, sizeInBits)) {
      return new SMGandValue(this, SMGValue.zeroValue());
    }
    int nestingLevel = object.getNestingLevel();
    SMGValue newValue = SMGValue.of(nestingLevel);
    SMG newSMG = this.copyAndAddValue(newValue);
    // Extend H by the has-value edge o -> v with the offset and size and return (smg,v) based on
    // the newly obtained SMG.
    SMGHasValueEdge newHVEdge = new SMGHasValueEdge(newValue, sizeInBits, offset);
    newSMG = newSMG.copyAndAddHVEdge(newHVEdge, object);
    return new SMGandValue(newSMG, newValue);
  }

  /**
   * TODO: Check this method again once we can test the entire system! Why? Because in my opinion
   * one can interpret the specification of this method in 2 ways: 1. The field to be checked
   * (offset + size) has to be covered by a SINGLE nullObject. 2. The field to be checked has to be
   * covered by nullObjects (1 or multiple), such that it is covered entirely. (2. is the current
   * implementation)
   *
   * <p>This Method checks for the entered SMGObject if there exists SMGHasValueEdges such that the
   * field [offset; offset + size) is covered by nullObjects. Important: One may not take
   * SMGHasValueEdges into account which lay outside of the SMGObject! Else it would be possible to
   * read potentially invalid memory!
   *
   * @param object The SMGObject in which a field is to be checked for nullified blocks.
   * @param offset The offset (=start) of the field. Has to be inside of the object.
   * @param size The size in bits of the field. Has to be larger than the offset but still inside
   *     the field.
   * @return True if the field is indeed covered by nullified blocks. False else.
   */
  @SuppressWarnings("unused")
  private boolean isCoveredByNullifiedBlocks(SMGObject object, BigInteger offset, BigInteger size) {
    NavigableMap<BigInteger, BigInteger> nullEdgesRangeMap = getNullEdgesForObject(object);
    // We start at the beginning of the object itself, as the null edges may be larger than our
    // field.
    BigInteger currentMax = nullEdgesRangeMap.firstKey();
    // The first edge offset can't cover the entire field if it begins after the obj offset!
    if (currentMax.compareTo(offset) > 0) {
      return false;
    }
    BigInteger offsetPlusSize = offset.add(size);
    // TreeMaps keySet is ordered!
    for (Map.Entry<BigInteger, BigInteger> entry : nullEdgesRangeMap.entrySet()) {
      // The max encountered yet has to be bigger or eq to the next key.
      if (currentMax.compareTo(entry.getKey()) > 0) {
        return false;
      }
      currentMax = currentMax.max(entry.getValue());
      // If there are no gaps,
      // the max encountered has to be >= offset + size at some point.
      if (currentMax.compareTo(offsetPlusSize) >= 0) {
        return true;
      }
    }
    // The max encountered did not cover the entire field.
    return false;
  }

  /**
   * Returns the sorted Map<offset, max size> of SMGHasValueEdge of NullObjects that cover the
   * entered SMGObject somewhere. Only edges that do not exceed the boundries of the object are
   * used. It always defaults to the max size, such that no smaller size for a offset exists.
   * Example: <0, 16> and <0, 24> would result in <0, 24>.
   *
   * @param smgObject The SMGObject one wants to check for covering NullObjects.
   * @return TreeMap<offset, max size> of covering edges.
   */
  private ImmutableSortedMap<BigInteger, BigInteger> getNullEdgesForObject(SMGObject smgObject) {
    BigInteger offset = smgObject.getOffset();
    BigInteger offsetPlusSize = smgObject.getSize().add(offset);
    // Both inequalities have to hold, else one may read invalid memory outside of the object!
    // ObjectOffset <= HasValueEdgeOffset
    // HasValueEdgeOffset + HasValueEdgeSize <= ObjectOffset + ObjectSize
    return hasValueEdges
        .get(SMGObject.nullInstance())
        .stream()
        .filter(
            n ->
                offset.compareTo(n.getOffset()) <= 0
                    && offsetPlusSize.compareTo(n.getOffset().add(n.getSizeInBits())) >= 0)
        .collect(
            ImmutableSortedMap.toImmutableSortedMap(
                Comparator.naturalOrder(),
                SMGHasValueEdge::getOffset,
                SMGHasValueEdge::getSizeInBits,
                BigInteger::max));
  }

  /**
   * Returns a Set of all SMGDoublyLinkedListSegments of this SMG.
   *
   * @return The Set of all SMGDoublyLinkedListSegments.
   */
  public FluentIterable<SMGDoublyLinkedListSegment> getDLLs() {
    return FluentIterable.from(smgObjects.keySet()).filter(SMGDoublyLinkedListSegment.class);
  }

  /**
   * Returns all SMGHasValueEdges for this SMG in a Set.
   *
   * @return Set of all SMGHasValueEdges of this SMG.
   */
  public FluentIterable<SMGHasValueEdge> getHVEdges() {
    return FluentIterable.from(hasValueEdges.values())
        .transformAndConcat(edges -> FluentIterable.from(edges));
  }

  /**
   * Returns all SMGPointsToEdges for this SMG in a Collection.
   *
   * @return Collection of all SMGPointsToEdges of this SMG.
   */
  public FluentIterable<SMGPointsToEdge> getPTEdges() {
    return FluentIterable.from(pointsToEdges.values());
  }

  /**
   * Returns all SMGPointsToEdges that points to a specific SMGObject.
   *
   * @param pointingTo the required target
   * @return Collection of all SMGPointsToEdges with the specified target.
   */
  public FluentIterable<SMGPointsToEdge> getPTEdgesByTarget(SMGObject pointingTo) {
    return getPTEdges().filter(ptEdge -> ptEdge.pointsTo().equals(pointingTo));
  }


  public PersistentMap<SMGValue, SMGPointsToEdge> getPTEdgeMapping() {
    return pointsToEdges;
  }


  /**
   * Returns the SMGPointsToEdge associated with the entered SMGValue.
   *
   * @param value The SMGValue for which the edge is to be returned.
   * @return The SMGPointsToEdge for the entered value.
   */
  public Optional<SMGPointsToEdge> getPTEdge(SMGValue value) {
    return Optional.ofNullable(pointsToEdges.get(value));
  }

  /**
   * Checks whether a given value is a pointer address.
   *
   * @param pValue to be checked
   * @return true if pValue is a pointer.
   */
  public boolean isPointer(SMGValue pValue) {
    return pointsToEdges.get(pValue) != null;
  }

  /**
   * Checks whether there exists an other edge for a given SMGObject, that overlaps with the
   * provided edge.
   *
   * @param pHValueEdge - the provided edge
   * @param pObject - the given SMGObject
   * @return true if there exists an overlapping edge with the provided edge.
   */
  public boolean hasOverlappingEdge(SMGHasValueEdge pHValueEdge, SMGObject pObject) {
    return getEdges(pObject).stream().anyMatch(other -> {
      BigInteger otherStart = other.getOffset();
      BigInteger otherEnd = otherStart.add(other.getSizeInBits());
      BigInteger pStart = pHValueEdge.getOffset();
      BigInteger pEnd = pStart.add(pHValueEdge.getSizeInBits());

      // pStart greater
      if (pStart.compareTo(otherStart) > 0) {
        return pStart.compareTo(otherEnd) < 0;
      }
      // pStart less
      if (pStart.compareTo(otherStart) < 0) {
        return pEnd.compareTo(otherStart) > 0;
      }

      return true;
    });
  }

  /**
   * Finds a pointer address to given pointer attributes.
   *
   * @param targetObject the wanted pointer
   * @param pOffset of the wanted pointer
   * @param pTargetSpecifier of the wanted pointer
   * @return Optional empty, if there is no such pointer or the address of a matching pointer.
   */
  public Optional<SMGValue> findAddressForEdge(
      SMGObject targetObject,
      BigInteger pOffset,
      SMGTargetSpecifier pTargetSpecifier) {
    return pointsToEdges.entrySet().stream().filter(entry -> {
      SMGPointsToEdge edge = entry.getValue();
      return edge.getOffset().equals(pOffset)
          && edge.targetSpecifier().equals(pTargetSpecifier)
          && edge.pointsTo().equals(targetObject);
    }).findAny().map(entry -> entry.getKey());
  }

  /**
   * Checks whether a given SMGObject is valid.
   *
   * @param pObject to be checked
   * @return true if pObject is valid, false if pObject was freed.
   */
  public boolean isValid(SMGObject pObject) {
    return smgObjects.getOrDefault(pObject, false);
  }

}
