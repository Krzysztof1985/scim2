/*
 * Copyright 2015 UnboundID Corp.
 * All Rights Reserved.
 */

package com.unboundid.scim2.filters;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ValueNode;
import com.unboundid.scim2.Path;

/**
 * Attribute comparison filter.
 */
public abstract class ComparisonFilter extends Filter
{
  private final Path filterAttribute;

  private final ValueNode filterValue;

  /**
   * Create a new attribute comparison filter.
   *
   * @param filterAttribute The path to the attribute to compare.
   * @param filterValue The comparison value.
   */
  ComparisonFilter(final Path filterAttribute, final ValueNode filterValue)
  {
    this.filterAttribute = filterAttribute;
    if (filterValue == null)
    {
      this.filterValue = JsonNodeFactory.instance.nullNode();
    } else
    {
      this.filterValue = filterValue;
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Path getAttributePath()
  {
    return filterAttribute;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ValueNode getComparisonValue()
  {
    return filterValue;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isComparisonFilter()
  {
    return true;
  }

  /**
   * Append the string representation of the filter to the provided buffer.
   *
   * @param builder The buffer to which the string representation of the
   *                filter is to be appended.
   */
  public void toString(final StringBuilder builder)
  {
    builder.append(filterAttribute);
    builder.append(' ');
    builder.append(getFilterType().getStringValue());
    builder.append(' ');
    builder.append(filterValue.toString());
  }
}