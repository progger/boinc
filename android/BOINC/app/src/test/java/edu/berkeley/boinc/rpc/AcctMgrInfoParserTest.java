/*
 * This file is part of BOINC.
 * http://boinc.berkeley.edu
 * Copyright (C) 2020 University of California
 *
 * BOINC is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * BOINC is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with BOINC.  If not, see <http://www.gnu.org/licenses/>.
 */
package edu.berkeley.boinc.rpc;

import android.util.Log;
import android.util.Xml;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import edu.berkeley.boinc.utils.Logging;
import kotlin.UninitializedPropertyAccessException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.powermock.api.mockito.PowerMockito.doThrow;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

@RunWith(PowerMockRunner.class)
@PrepareForTest({Log.class, Xml.class})
public class AcctMgrInfoParserTest {
    private static final String ACCT_MGR_NAME = "Account Manager Name";
    private static final String ACCT_MGR_URL = "Account Manager URL";

    private AcctMgrInfoParser acctMgrInfoParser;
    private AcctMgrInfo expected;

    @Before
    public void setUp() {
        acctMgrInfoParser = new AcctMgrInfoParser();
        expected = new AcctMgrInfo();
    }

    @Test(expected = UninitializedPropertyAccessException.class)
    public void testParse_whenRpcStringIsNull_thenExpectUninitializedPropertyAccessException() {
        mockStatic(Xml.class);

        AcctMgrInfoParser.parse(null);
    }

    @Test(expected = UninitializedPropertyAccessException.class)
    public void testParse_whenRpcStringIsEmpty_thenExpectUninitializedPropertyAccessException() {
        mockStatic(Xml.class);

        AcctMgrInfoParser.parse("");
    }

    @Test
    public void testParse_whenSAXExceptionIsThrown_thenExpectNull() throws Exception {
        mockStatic(Log.class);
        mockStatic(Xml.class);

        Logging.setLogLevel(2);
        doThrow(new SAXException()).when(Xml.class, "parse", anyString(), any(ContentHandler.class));

        assertNull(AcctMgrInfoParser.parse(""));
    }

    @Test
    public void testParser_whenOnlyStartElementIsRun_thenExpectElementStarted() throws SAXException {
        acctMgrInfoParser.startElement(null, "", null, null);

        assertTrue(acctMgrInfoParser.mElementStarted);
    }

    @Test
    public void testParser_whenBothStartElementAndEndElementAreRun_thenExpectElementNotStarted() throws SAXException {
        acctMgrInfoParser.startElement(null, AcctMgrInfoParser.ACCT_MGR_INFO_TAG, null, null);
        acctMgrInfoParser.endElement(null, AcctMgrInfoParser.ACCT_MGR_INFO_TAG, null);

        assertFalse(acctMgrInfoParser.mElementStarted);
    }

    @Test(expected = UninitializedPropertyAccessException.class)
    public void testParser_whenLocalNameIsEmpty_thenExpectNullAccountManagerInfo() throws SAXException {
        acctMgrInfoParser.startElement(null, "", null, null);

        acctMgrInfoParser.getAccountMgrInfo();
    }

    @Test
    public void testParser_whenXmlAccountManagerInfoHasNoElements_thenExpectDefaultAccountManagerInfo()
            throws SAXException {
        acctMgrInfoParser.startElement(null, AcctMgrInfoParser.ACCT_MGR_INFO_TAG, null, null);
        acctMgrInfoParser.endElement(null, AcctMgrInfoParser.ACCT_MGR_INFO_TAG, null);

        assertEquals(expected, acctMgrInfoParser.getAccountMgrInfo());
    }

    @Test
    public void testParser_whenXmlAccountManagerInfoHasOnlyName_thenExpectMatchingAccountManagerInfo()
            throws SAXException {
        acctMgrInfoParser.startElement(null, AcctMgrInfoParser.ACCT_MGR_INFO_TAG, null, null);
        acctMgrInfoParser.startElement(null, AcctMgrInfo.Fields.ACCT_MGR_NAME, null, null);
        acctMgrInfoParser.characters(ACCT_MGR_NAME.toCharArray(), 0, ACCT_MGR_NAME.length());
        acctMgrInfoParser.endElement(null, AcctMgrInfo.Fields.ACCT_MGR_NAME, null);
        acctMgrInfoParser.endElement(null, AcctMgrInfoParser.ACCT_MGR_INFO_TAG, null);

        expected.setAcctMgrName(ACCT_MGR_NAME);

        assertEquals(expected, acctMgrInfoParser.getAccountMgrInfo());
    }

    @Test
    public void testParser_whenXmlAccountManagerInfoHasOnlyNameAndUrl_thenExpectMatchingAccountManagerInfo()
            throws SAXException {
        acctMgrInfoParser.startElement(null, AcctMgrInfoParser.ACCT_MGR_INFO_TAG, null, null);
        acctMgrInfoParser.startElement(null, AcctMgrInfo.Fields.ACCT_MGR_NAME, null, null);
        acctMgrInfoParser.characters(ACCT_MGR_NAME.toCharArray(), 0, ACCT_MGR_NAME.length());
        acctMgrInfoParser.endElement(null, AcctMgrInfo.Fields.ACCT_MGR_NAME, null);
        acctMgrInfoParser.startElement(null, AcctMgrInfo.Fields.ACCT_MGR_URL, null, null);
        acctMgrInfoParser.characters(ACCT_MGR_URL.toCharArray(), 0, ACCT_MGR_URL.length());
        acctMgrInfoParser.endElement(null, AcctMgrInfo.Fields.ACCT_MGR_URL, null);
        acctMgrInfoParser.endElement(null, AcctMgrInfoParser.ACCT_MGR_INFO_TAG, null);

        expected.setAcctMgrName(ACCT_MGR_NAME);
        expected.setAcctMgrUrl(ACCT_MGR_URL);

        assertEquals(expected, acctMgrInfoParser.getAccountMgrInfo());
    }

    @Test
    public void testParser_whenXmlAccountManagerInfoHasOnlyNameUrlAndCookieFailureUrl_thenExpectMatchingAccountManagerInfo()
            throws SAXException {
        acctMgrInfoParser.startElement(null, AcctMgrInfoParser.ACCT_MGR_INFO_TAG, null, null);
        acctMgrInfoParser.startElement(null, AcctMgrInfo.Fields.ACCT_MGR_NAME, null, null);
        acctMgrInfoParser.characters(ACCT_MGR_NAME.toCharArray(), 0, ACCT_MGR_NAME.length());
        acctMgrInfoParser.endElement(null, AcctMgrInfo.Fields.ACCT_MGR_NAME, null);
        acctMgrInfoParser.startElement(null, AcctMgrInfo.Fields.ACCT_MGR_URL, null, null);
        acctMgrInfoParser.characters(ACCT_MGR_URL.toCharArray(), 0, ACCT_MGR_URL.length());
        acctMgrInfoParser.endElement(null, AcctMgrInfo.Fields.ACCT_MGR_URL, null);
        acctMgrInfoParser.endElement(null, AcctMgrInfoParser.ACCT_MGR_INFO_TAG, null);

        expected.setAcctMgrName(ACCT_MGR_NAME);
        expected.setAcctMgrUrl(ACCT_MGR_URL);

        assertEquals(expected, acctMgrInfoParser.getAccountMgrInfo());
    }

    @Test
    public void testParser_whenXmlAccountManagerInfoHasAllAttributes_thenExpectMatchingAccountManagerInfo()
            throws SAXException {
        acctMgrInfoParser.startElement(null, AcctMgrInfoParser.ACCT_MGR_INFO_TAG, null, null);
        acctMgrInfoParser.startElement(null, AcctMgrInfo.Fields.ACCT_MGR_NAME, null, null);
        acctMgrInfoParser.characters(ACCT_MGR_NAME.toCharArray(), 0, ACCT_MGR_NAME.length());
        acctMgrInfoParser.endElement(null, AcctMgrInfo.Fields.ACCT_MGR_NAME, null);
        acctMgrInfoParser.startElement(null, AcctMgrInfo.Fields.ACCT_MGR_URL, null, null);
        acctMgrInfoParser.characters(ACCT_MGR_URL.toCharArray(), 0, ACCT_MGR_URL.length());
        acctMgrInfoParser.endElement(null, AcctMgrInfo.Fields.ACCT_MGR_URL, null);
        acctMgrInfoParser.startElement(null, AcctMgrInfo.Fields.HAVING_CREDENTIALS, null, null);
        acctMgrInfoParser.characters("true".toCharArray(), 0, 4);
        acctMgrInfoParser.endElement(null, AcctMgrInfo.Fields.HAVING_CREDENTIALS, null);
        acctMgrInfoParser.endElement(null, AcctMgrInfoParser.ACCT_MGR_INFO_TAG, null);

        expected = new AcctMgrInfo(ACCT_MGR_NAME, ACCT_MGR_URL, true);

        assertEquals(expected, acctMgrInfoParser.getAccountMgrInfo());
    }
}
