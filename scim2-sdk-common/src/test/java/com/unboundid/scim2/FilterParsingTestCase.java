/*
 * Copyright 2015 UnboundID Corp.
 * All Rights Reserved.
 */

package com.unboundid.scim2;

import com.unboundid.scim2.filters.Filter;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;
import static com.unboundid.scim2.filters.Filter.*;



/**
 * Test coverage for the {@code Filter} class.
 */
public class FilterParsingTestCase
{
  /**
   * Retrieves a set of valid filter strings.
   *
   * @return  A set of valid filter strings.
   */
  @DataProvider(name = "testValidFilterStrings")
  public Object[][] getTestValidFilterStrings()
  {
    return new Object[][]
        {
            new Object[] { "userName Eq \"john\"", eq("userName", "john") },
            new Object[] { "Username eq \"john\"", eq("Username", "john") },
            new Object[] { "userName eq \"bjensen\"",
                eq("userName", "bjensen") },
            new Object[] { "userName ne \"bjensen\"",
                ne("userName", "bjensen") },
            new Object[] { "userName co \"jensen\"", co("userName", "jensen") },
            new Object[] { "userName sw \"J\"", sw("userName", "J") },
            new Object[] { "userName ew \"sen\"", ew("userName", "sen") },
            new Object[] { "title pr", pr("title") },
            new Object[] { "meta.lastModified gt \"2011-05-13T04:42:34Z\"",
                gt("meta.lastModified", "2011-05-13T04:42:34Z") },
            new Object[] { "meta.lastModified ge \"2011-05-13T04:42:34Z\"",
                ge("meta.lastModified", "2011-05-13T04:42:34Z") },
            new Object[] { "meta.lastModified lt \"2011-05-13T04:42:34Z\"",
                lt("meta.lastModified", "2011-05-13T04:42:34Z") },
            new Object[] { "meta.lastModified le \"2011-05-13T04:42:34Z\"",
                le("meta.lastModified", "2011-05-13T04:42:34Z") },
            new Object[] { " title  pr  and  userType  eq  \"Employee\" ",
                and(pr("title"), eq("userType", "Employee")) },
            new Object[] { "title pr or userType eq \"Intern\"",
                or(pr("title"), eq("userType", "Intern")) },
            new Object[] { "not(userName ew \"sen\")",
                not(ew("userName", "sen")) },
            new Object[] { "not ( userName ew \"sen\" ) ",
                not(ew("userName", "sen")) },
            new Object[] { "userType eq \"Employee\" and " +
                "(email co \"example.com\" " +
                "or email co \"example.org\")",
                and(eq("userType", "Employee"), or(
                    co("email", "example.com"), co("email", "example.org"))) },
            new Object[] { "userName co \"\\ufe00\\\"\\n\\t\\\\\"",
                co("userName", "\ufe00\"\n\t\\") },
            new Object[] { "urn:extension:members eq 25",
                eq("urn:extension:members", 25) },
            new Object[] { "urn:extension:members eq 25.52",
                eq("urn:extension:members", 25.52) },
            new Object[] { "urn:extension:isActive eq true",
                eq("urn:extension:isActive", true) },
            new Object[] { "urn:extension:isActive eq false",
                eq("urn:extension:isActive", false) },
            new Object[] { "addresses[zipcode eq 88283 and city ne \"Austin\"]",
                hasComplexValue("addresses",
                    and(eq("zipcode", 88283), ne("city", "Austin"))) },
            new Object[] { "not(addresses[city ne \"Austin\"])",
                not(hasComplexValue("addresses", ne("city", "Austin"))) },

            // Precedence tests
            new Object[] { "title pr and email pr or userType pr",
                or(and(pr("title"), pr("email")), pr("userType")) },
            new Object[] { "(title pr and email pr) or userType pr",
                or(and(pr("title"), pr("email")), pr("userType")) },
            new Object[] { "title pr and email pr or not (userType pr)",
                or(and(pr("title"), pr("email")), not(pr("userType"))) },
            new Object[] { "title pr or email pr and userType pr",
                or(pr("title"), and(pr("email"), pr("userType"))) },
            new Object[] { "title pr or (email pr and userType pr)",
                or(pr("title"), and(pr("email"), pr("userType"))) },
            new Object[] { "title pr or email pr and not (userType pr)",
                or(pr("title"), and(pr("email"), not(pr("userType")))) },
            new Object[] { "title pr and (email pr or userType pr)",
                and(pr("title"), or(pr("email"), pr("userType"))) },
            new Object[] { "title pr and not (email pr or userType pr)",
                and(pr("title"), not(or(pr("email"), pr("userType")))) },
            new Object[] { "(title pr or email pr) and userType pr",
                and(or(pr("title"), pr("email")), pr("userType")) },
            new Object[] { "not (title pr or email pr) and userType pr",
                and(not(or(pr("title"), pr("email"))), pr("userType")) },
        };
  }



  /**
   * Retrieves a set of invalid filter strings.
   *
   * @return  A set of invalid filter strings.
   */
  @DataProvider(name = "testInvalidFilterStrings")
  public Object[][] getTestInvalidFilterStrings()
  {
    return new Object[][]
        {
            new Object[] { "" },
            new Object[] { "(" },
            new Object[] { ")" },
            new Object[] { "()" },
            new Object[] { "foo" },
            new Object[] { "( title pr ) eq " },
            new Object[] { "username pr \"bjensen\"" },
            new Object[] { "meta.lastModified lte \"2011-05-13T04:42:34Z\"" },
            new Object[] { "username eq" },
            new Object[] { "title pr and userType eq \"Employee\" eq" },
            new Object[] { "title pr and userType eq true eq" },
            new Object[] { "title pr and userType eq 12345.23 eq" },
            new Object[] { "userName eq 'bjensen'" },
            new Object[] { "userName eq \"bjensen" },
            new Object[] { "userName eq \"bjensen\\" },
            new Object[] { "userName eq \"\\a\"" },
            new Object[] { "userName eq bjensen" },
            new Object[] { "userName co \"\\ufe\" or userName co \"a\""},
            new Object[] { "userName bad \"john\"" },
            new Object[] { "userName eq (\"john\")" },
            new Object[] { "(userName eq \"john\"" },
            new Object[] { "userName eq \"john\")" },
            new Object[] { "userName eq \"john\" userName pr" },
            new Object[] { "userName pr and" },
            new Object[] { "and or" },
            new Object[] { "not ( and )" },
            new Object[] { "not userName pr" },
            new Object[] { "userName pr ()" },
            new Object[] { "() userName pr" },
            new Object[] { "(userName pr)()" },
            new Object[] { "()(userName pr)" },
            new Object[] { "userName[])" },
            new Object[] { "userName pr[)" },
            new Object[] { "userName pr [bar pr])" },
            new Object[] { "userName[userName pr)" },
            new Object[] { "userName[userName[bar pr]])" },
            new Object[] { "userName[userName pr]])" },
        };
  }



  /**
   * Tests the {@code fromString} method with a valid filter string.
   *
   * @param filterString  The string representation of the filter to
   *                      fromString.
   * @param expectedFilter The expected parsed filter instance.
   *
   * @throws Exception  If the test fails.
   */
  @Test(dataProvider = "testValidFilterStrings")
  public void testParseValidFilter(final String filterString,
                                   final Filter expectedFilter)
      throws Exception
  {
    final Filter parsedFilter = Filter.fromString(filterString);
    assertEquals(parsedFilter, expectedFilter);
//    System.out.println("Parse filter string: " + filterString);
//    System.out.println("Parsed filter: " + filter);
  }



  /**
   * Tests the {@code fromString} method with an invalid filter string.
   *
   * @param  filterString  The string representation of the filter to
   *                       fromString.
   *
   * @throws Exception If the test fails.
   */
  @Test(dataProvider = "testInvalidFilterStrings")
  public void testParseInvalidFilter(final String filterString)
      throws Exception
  {
    try
    {
      Filter.fromString(filterString);
      fail("Unexpected successful fromString of invalid filter: " +
          filterString);
    }
    catch (IllegalArgumentException e)
    {
//      System.out.println("Parse invalid filter: " + filterString);
//      System.out.println("Error message: " + e.getMessage());
    }
  }
}