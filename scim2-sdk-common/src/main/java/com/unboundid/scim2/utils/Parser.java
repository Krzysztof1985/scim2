/*
 * Copyright 2015 UnboundID Corp.
 * All Rights Reserved.
 */

package com.unboundid.scim2.utils;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ValueNode;
import com.unboundid.scim2.Path;
import com.unboundid.scim2.exceptions.SCIMException;
import com.unboundid.scim2.filters.Filter;
import com.unboundid.scim2.filters.FilterType;

import java.io.IOException;
import java.io.Reader;
import java.util.LinkedList;
import java.util.Stack;



/**
 * A parser for SCIM filter expressions.
 */
public class Parser
{

  private static final class StringReader extends Reader
  {
    private final String string;
    private int pos;
    private int mark;

    /**
     * Create a new reader.
     *
     * @param string The string to read from.
     */
    private StringReader(final String string)
    {
      this.string = string;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int read()
    {
      if(pos >= string.length())
      {
        return -1;
      }
      return string.charAt(pos++);
    }

    /**
     * Move the current read position back one character.
     */
    public void unread()
    {
      pos--;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean ready()
    {
      return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean markSupported()
    {
      return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void mark(final int readAheadLimit)
    {
      mark = pos;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void reset()
    {
      pos = mark;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long skip(final long n)
    {
      long chars = Math.min(string.length() - pos, n);
      pos += chars;
      return chars;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int read(final char[] cbuf, final int off, final int len)
    {
      if(pos  >= string.length())
      {
        return -1;
      }
      int chars = Math.min(string.length() - pos, len);
      System.arraycopy(string.toCharArray(), pos, cbuf, off, chars);
      pos += chars;
      return chars;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close()
    {
      // do nothing.
    }
  }

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  /**
   * Parse a filter string.
   *
   * @param filterString   The filter string to parse.
   *
   * @return A parsed SCIM filter.
   * @throws  SCIMException  If the filter string could not be parsed.
   */
  public static Filter parseFilter(final String filterString)
      throws SCIMException
  {
    //  try
    //{
    return readFilter(new StringReader(filterString.trim()), false);
    // }
    //  catch (Exception e)
    //  {
    //   Debug.debugException(e);
    //    throw SCIMException.createException(
    //       400, MessageFormat.format("Invalid filter ''{0}'': {1}",
    //           reader.string, e.getMessage()));
    // }
  }

  /**
   * Parse a path string.
   *
   * @param pathString   The path string to parse.
   *
   * @return A parsed SCIM path.
   */
  public static Path parsePath(final String pathString)
  {
    return readPath(new StringReader(pathString.trim()));
  }

  /**
   * Read a path token. A token is either:
   * <ul>
   *   <li>
   *     An attribute name terminated by a period.
   *   </li>
   *   <li>
   *     An attribute name terminated by an opening brace.
   *   </li>
   *   <li>
   * </ul>
   *
   * @param reader The reader to read from.
   *
   * @return The token at the current position, or {@code null} if the end of
   *         the input has been reached.
   */
  private static String readPathToken(final StringReader reader)
  {
    reader.mark(0);
    int c = reader.read();

    StringBuilder b = new StringBuilder();
    while(c > 0)
    {
      if (c == '.' && !b.toString().endsWith(":"))
      {
        if(reader.pos >= reader.string.length())
        {
          // There is nothing after the period.
          final String msg = String.format(
              "Unexpected end of path string");
          throw new IllegalArgumentException(msg);
        }
        // Terminating period. Consume it and return token.
        return b.toString();
      }
      if (c == '[')
      {
        // Terminating opening brace. Consume it and return token.
        b.append((char)c);
        return b.toString();
      }
      if (c == '-' || c == '_' || c == ':' || Character.isLetterOrDigit(c))
      {
        b.append((char)c);
      }
      else
      {
        final String msg = String.format(
            "Unexpected character '%s' at position %d for token starting at %d",
            (char)c, reader.pos - 1, reader.mark);
        throw new IllegalArgumentException(msg);
      }
      c = reader.read();
    }

    if(b.length() > 0)
    {
      return b.toString();
    }
    return null;
  }

  /**
   * Read a path from the reader.
   *
   * @param reader The reader to read the path from.
   *
   * @return The parsed path.
   */
  private static Path readPath(final StringReader reader)
  {
    Path path = null;

    String token;

    while ((token = readPathToken(reader)) != null)
    {
      if (token.isEmpty())
      {
        // the only time this is allowed to occur is if the previous attribute
        // had a value filter, in which case, consume the token and move on.
        if(path == null || path.getElements().isEmpty() ||
            path.getElements().get(
                path.getElements().size()-1).getValueFilter() == null)
        {
          final String msg = String.format(
              "Attribute name expected at position %d", reader.mark);
          throw new IllegalArgumentException(msg);
        }
      }
      else
      {
        String schemaUrn = null;
        String attributeName = token;
        Filter valueFilter = null;
        if(path == null &&
            attributeName.toLowerCase().startsWith("urn:"))
        {
          // The attribute name is prefixed with the schema URN.

          // Find the last ":". Everything to the left is the schema URN,
          // everything on the right is the attribute name.
          int i = token.lastIndexOf(':');
          schemaUrn = token.substring(0, i++);
          attributeName = token.substring(i, token.length());
          if(attributeName.isEmpty())
          {
            // The trailing colon signifies that this is an extension root.
            return Path.root(schemaUrn);
          }
        }
        if (attributeName.endsWith("["))
        {
          // There is a value path.
          attributeName =
              attributeName.substring(0, attributeName.length() - 1);
          valueFilter = readFilter(reader, true);
        }
        try
        {
          if (path == null)
          {
            path = Path.attribute(schemaUrn, attributeName, valueFilter);
          }
          else
          {
            path = path.sub(attributeName, valueFilter);
          }
        }
        catch(Exception e)
        {
          Debug.debugException(e);
          final String msg = String.format(
              "Invalid attribute name starting at position %d: %s",
              reader.mark, e.getMessage());
          throw new IllegalArgumentException(msg);
        }
      }
    }

    if(path == null)
    {
      return Path.root();
    }
    return path;
  }

  /**
   * Read a filter token. A token is either:
   * <ul>
   *   <li>
   *     An attribute path terminated by a space or an opening parenthesis.
   *   </li>
   *   <li>
   *     An attribute path terminated by an opening brace.
   *   </li>
   *   <li>
   *     An operator terminated by a space or an opening parenthesis.
   *   </li>
   *   <li>
   *     An opening parenthesis.
   *   </li>
   *   <li>
   *     An closing parenthesis.
   *   </li>
   *   <li>
   *     An closing brace.
   *   </li>
   *   <li>
   *
   *   </li>
   * </ul>
   *
   * @param reader The reader to read from.
   * @param isValueFilter Whether to read the token for a value filter.
   *
   * @return The token at the current position, or {@code null} if the end of
   *         the input has been reached.
   */
  private static String readFilterToken(final StringReader reader,
                                        final boolean isValueFilter)
  {
    int c;
    do
    {
      // Skip over any leading spaces.
      reader.mark(0);
      c = reader.read();
    }
    while(c == ' ');

    StringBuilder b = new StringBuilder();
    while(c > 0)
    {
      if (c == ' ')
      {
        // Terminating space. Consume it and return token.
        return b.toString();
      }
      if (c == '(' || c == ')')
      {
        if(b.length() > 0)
        {
          // Do not consume the parenthesis.
          reader.unread();
        }
        else
        {
          b.append((char)c);
        }
        return b.toString();
      }
      if (!isValueFilter && c == '[')
      {
        // Terminating opening brace. Consume it and return token.
        b.append((char)c);
        return b.toString();
      }
      if (isValueFilter && c == ']')
      {
        if(b.length() > 0)
        {
          // Do not consume the closing brace.
          reader.unread();
        }
        else
        {
          b.append((char)c);
        }
        return b.toString();
      }
      if (c == '-' || c == '_' || c == '.' || c == ':' ||
          Character.isLetterOrDigit(c))
      {
        b.append((char)c);
      }
      else
      {
        final String msg = String.format(
            "Unexpected character '%s' at position %d for token starting at %d",
            (char)c, reader.pos - 1, reader.mark);
        throw new IllegalArgumentException(msg);
      }
      c = reader.read();
    }

    if(b.length() > 0)
    {
      return b.toString();
    }
    return null;
  }

  /**
   * Read a filter from the reader.
   *
   * @param reader The reader to read the filter from.
   * @param isValueFilter Whether to read the filter as a value filter.
   * @return The parsed filter.
   */
  private static Filter readFilter(final StringReader reader,
                                   final boolean isValueFilter)
  {
    final Stack<Filter> outputStack = new Stack<Filter>();
    final Stack<String> precedenceStack = new Stack<String>();

    String token;
    String previousToken = null;

    while((token = readFilterToken(reader, isValueFilter)) != null)
    {
      if(token.equals("(") && expectsNewFilter(previousToken))
      {
        precedenceStack.push(token);
      }
      else if(token.equalsIgnoreCase(FilterType.NOT.getStringValue()) &&
          expectsNewFilter(previousToken))
      {
        // "not" should be followed by an (
        String nextToken = readFilterToken(reader, isValueFilter);
        if(nextToken == null)
        {
          final String msg = String.format(
              "Unexpected end of filter string");
          throw new IllegalArgumentException(msg);
        }
        if(!nextToken.equals("("))
        {
          final String msg = String.format(
              "Expected '(' at position %d", reader.mark);
          throw new IllegalArgumentException(msg);
        }
        precedenceStack.push(token);
      }
      else if(token.equals(")") && !expectsNewFilter(previousToken))
      {
        String operator = closeGrouping(precedenceStack, outputStack, false);
        if(operator == null)
        {
          final String msg =
              String.format("No opening parenthesis matching closing " +
                  "parenthesis at position %d", reader.mark);
          throw new IllegalArgumentException(msg);
        }
        if (operator.equalsIgnoreCase(FilterType.NOT.getStringValue()))
        {
          // Treat "not" the same as "(" except wrap everything in a not filter.
          outputStack.push(Filter.not(outputStack.pop()));
        }
      }
      else if(token.equalsIgnoreCase(FilterType.AND.getStringValue()) &&
          !expectsNewFilter(previousToken))
      {
        // and has higher precedence than or.
        precedenceStack.push(token);
      }
      else if(token.equalsIgnoreCase(FilterType.OR.getStringValue()) &&
          !expectsNewFilter(previousToken))
      {
        // pop all the pending ands first before pushing or.
        LinkedList<Filter> andComponents = new LinkedList<Filter>();
        while (!precedenceStack.isEmpty())
        {
          if (precedenceStack.peek().equalsIgnoreCase(
              FilterType.AND.getStringValue()))
          {
            precedenceStack.pop();
            andComponents.addFirst(outputStack.pop());
          }
          else
          {
            break;
          }
          if(!andComponents.isEmpty())
          {
            andComponents.addFirst(outputStack.pop());
            outputStack.push(Filter.and(andComponents));
          }
        }

        precedenceStack.push(token);
      }
      else if(token.endsWith("[") && expectsNewFilter(previousToken))
      {
        // This is a complex value filter.
        final Path filterAttribute;
        try
        {
          filterAttribute = Path.fromString(
              token.substring(0, token.length() - 1));
        }
        catch (final Exception e)
        {
          Debug.debugException(e);
          final String msg = String.format(
              "Expected an attribute reference at position %d: %s",
              reader.mark, e.getMessage());
          throw new IllegalArgumentException(msg);
        }

        outputStack.push(Filter.hasComplexValue(
            filterAttribute, readFilter(reader, true)));
      }
      else if(isValueFilter && token.equals("]") &&
          !expectsNewFilter(previousToken))
      {
        break;
      }
      else if(expectsNewFilter(previousToken))
      {
        // This must be an attribute path followed by operator and maybe value.
        final Path filterAttribute;
        try
        {
          filterAttribute = Path.fromString(token);
        }
        catch (final Exception e)
        {
          Debug.debugException(e);
          final String msg = String.format(
              "Invalid attribute path at position %d: %s",
              reader.mark, e.getMessage());
          throw new IllegalArgumentException(msg);
        }

        String op = readFilterToken(reader, isValueFilter);

        if(op == null)
        {
          final String msg = String.format(
              "Unexpected end of filter string");
          throw new IllegalArgumentException(msg);
        }

        if (op.equalsIgnoreCase(FilterType.PRESENT.getStringValue()))
        {
          outputStack.push(Filter.pr(filterAttribute));
        }
        else
        {
          ValueNode valueNode;
          try
          {
            // Mark the beginning of the JSON value so we can later reset back
            // to this position and skip the actual chars that were consumed
            // by Jackson. The Jackson parser is buffered and reads everything
            // until the end of string.
            reader.mark(0);
            JsonParser parser =
                OBJECT_MAPPER.getJsonFactory().createJsonParser(reader);
            // The object mapper will return a Java null for JSON null.
            // Have to distinguish between reading a JSON null and encountering
            // the end of string.
            if (parser.getCurrentToken() == null && parser.nextToken() == null)
            {
              // End of string.
              valueNode = null;
            }
            else
            {
              valueNode = OBJECT_MAPPER.readValue(parser, ValueNode.class);

              // This is actually a JSON null. Use NullNode.
              if(valueNode == null)
              {
                valueNode = OBJECT_MAPPER.getNodeFactory().nullNode();
              }
            }
            // Reset back to the beginning of the JSON value.
            reader.reset();
            // Skip the number of chars consumed by JSON parser + 1.
            reader.skip(parser.getCurrentLocation().getCharOffset() + 1);
          }
          catch (IOException e)
          {
            final String msg = String.format(
                "Invalid comparison value at position %d: %s",
                reader.mark, e.getMessage());
            throw new IllegalArgumentException(msg);
          }

          if (valueNode == null)
          {
            final String msg = String.format(
                "Unexpected end of filter string");
            throw new IllegalArgumentException(msg);
          }

          if (op.equalsIgnoreCase(FilterType.EQUAL.getStringValue()))
          {
            outputStack.push(Filter.eq(filterAttribute, valueNode));
          } else if (op.equalsIgnoreCase(
              FilterType.NOT_EQUAL.getStringValue()))
          {
            outputStack.push(Filter.ne(filterAttribute, valueNode));
          } else if (op.equalsIgnoreCase(
              FilterType.CONTAINS.getStringValue()))
          {
            outputStack.push(Filter.co(filterAttribute, valueNode));
          } else if (op.equalsIgnoreCase(
              FilterType.STARTS_WITH.getStringValue()))
          {
            outputStack.push(Filter.sw(filterAttribute, valueNode));
          } else if (op.equalsIgnoreCase(
              FilterType.ENDS_WITH.getStringValue()))
          {
            outputStack.push(Filter.ew(filterAttribute, valueNode));
          } else if (op.equalsIgnoreCase(
              FilterType.GREATER_THAN.getStringValue()))
          {
            outputStack.push(Filter.gt(filterAttribute, valueNode));
          } else if (op.equalsIgnoreCase(
              FilterType.GREATER_OR_EQUAL.getStringValue()))
          {
            outputStack.push(Filter.ge(filterAttribute, valueNode));
          } else if (op.equalsIgnoreCase(
              FilterType.LESS_THAN.getStringValue()))
          {
            outputStack.push(Filter.lt(filterAttribute, valueNode));
          } else if (op.equalsIgnoreCase(
              FilterType.LESS_OR_EQUAL.getStringValue()))
          {
            outputStack.push(Filter.le(filterAttribute, valueNode));
          } else
          {
            final String msg = String.format(
                "Unrecognized attribute operator '%s' at position %d. " +
                    "Expected: eq,ne,co,sw,ew,pr,gt,ge,lt,le", op, reader.mark);
            throw new IllegalArgumentException(msg);
          }
        }
      }
      else
      {
        final String msg = String.format(
            "Unexpected character '%s' at position %d", token,
            reader.mark);
        throw new IllegalArgumentException(msg);
      }
      previousToken = token;
    }

    closeGrouping(precedenceStack, outputStack, true);

    if(outputStack.isEmpty())
    {
      final String msg = String.format(
          "Unexpected end of filter string");
      throw new IllegalArgumentException(msg);
    }
    return outputStack.pop();
  }

  /**
   * Close a grouping of filters enclosed by parenthesis.
   *
   * @param operators The stack of operators tokens.
   * @param output The stack of output tokens.
   * @param isAtTheEnd Whether the end of the filter string was reached.
   * @return The last operator encountered that signaled the end of the group.
   */
  private static String closeGrouping(final Stack<String> operators,
                                      final Stack<Filter> output,
                                      final boolean isAtTheEnd)
  {
    String operator = null;
    String repeatingOperator = null;
    LinkedList<Filter> components = new LinkedList<Filter>();

    // Iterate over the logical operators on the stack until either there are
    // no more operators or an opening parenthesis or not is found.
    while (!operators.isEmpty())
    {
      operator = operators.pop();
      if(operator.equals("(") ||
          operator.equalsIgnoreCase(FilterType.NOT.getStringValue()))
      {
        if(isAtTheEnd)
        {
          final String msg = String.format(
              "Unexpected end of filter string");
          throw new IllegalArgumentException(msg);
        }
        break;
      }
      if(repeatingOperator == null)
      {
        repeatingOperator = operator;
      }
      if(!operator.equals(repeatingOperator))
      {
        if(output.isEmpty())
        {
          final String msg = String.format(
              "Unexpected end of filter string");
          throw new IllegalArgumentException(msg);
        }
        components.addFirst(output.pop());
        if(repeatingOperator.equalsIgnoreCase(FilterType.AND.getStringValue()))
        {
          output.push(Filter.and(components));
        }
        else
        {
          output.push(Filter.or(components));
        }
        components.clear();
        repeatingOperator = operator;
      }
      if(output.isEmpty())
      {
        final String msg = String.format(
            "Unexpected end of filter string");
        throw new IllegalArgumentException(msg);
      }
      components.addFirst(output.pop());
    }

    if(repeatingOperator != null && !components.isEmpty())
    {
      if(output.isEmpty())
      {
        final String msg = String.format(
            "Unexpected end of filter string");
        throw new IllegalArgumentException(msg);
      }
      components.addFirst(output.pop());
      if(repeatingOperator.equalsIgnoreCase(FilterType.AND.getStringValue()))
      {
        output.push(Filter.and(components));
      }
      else
      {
        output.push(Filter.or(components));
      }
    }

    return operator;
  }

  /**
   * Whether a new filter token is expected given the previous token.
   *
   * @param previousToken The previous filter token.
   * @return Whether a new filter token is expected.
   */
  private static boolean expectsNewFilter(final String previousToken)
  {
    return previousToken == null ||
        previousToken.equals("(") ||
        previousToken.equalsIgnoreCase(FilterType.NOT.getStringValue()) ||
        previousToken.equalsIgnoreCase(FilterType.AND.getStringValue()) ||
        previousToken.equalsIgnoreCase(FilterType.OR.getStringValue());
  }
}