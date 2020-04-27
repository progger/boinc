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

import android.util.Xml;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.powermock.api.mockito.PowerMockito.doThrow;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

@RunWith(PowerMockRunner.class)
@PrepareForTest(Xml.class)
public class AppVersionsParserTest {
    private static final String APP_NAME = "App Name";

    private AppVersionsParser appVersionsParser;
    private AppVersion expected;

    @Before
    public void setUp() {
        appVersionsParser = new AppVersionsParser();
        expected = new AppVersion();
    }

    @Test
    public void testParse_whenRpcStringIsNull_thenExpectEmptyList() {
        mockStatic(Xml.class);

        assertTrue(AppVersionsParser.parse(null).isEmpty());
    }

    @Test
    public void testParse_whenSAXExceptionIsThrown_thenExpectEmptyList() throws Exception {
        mockStatic(Xml.class);

        doThrow(new SAXException()).when(Xml.class, "parse", anyString(), any(ContentHandler.class));

        assertTrue(AppVersionsParser.parse("").isEmpty());
    }

    @Test
    public void testParser_whenLocalNameIsEmpty_thenExpectEmptyList() throws SAXException {
        appVersionsParser.startElement(null, "", null, null);

        assertTrue(appVersionsParser.getAppVersions().isEmpty());
    }

    @Test
    public void testParser_whenXmlAppVersionHasNoElements_thenExpectEmptyList()
            throws SAXException {
        appVersionsParser.startElement(null, AppVersionsParser.APP_VERSION_TAG, null, null);
        appVersionsParser.endElement(null, AppVersionsParser.APP_VERSION_TAG, null);

        assertTrue(appVersionsParser.getAppVersions().isEmpty());
    }

    @Test
    public void testParser_whenXmlAppVersionHasOnlyAppName_thenExpectMatchingAppVersion()
            throws SAXException {
        appVersionsParser.startElement(null, AppVersionsParser.APP_VERSION_TAG, null, null);
        appVersionsParser.startElement(null, AppVersion.Fields.APP_NAME, null, null);
        appVersionsParser.characters(APP_NAME.toCharArray(), 0, APP_NAME.length());
        appVersionsParser.endElement(null, AppVersion.Fields.APP_NAME, null);
        appVersionsParser.endElement(null, AppVersionsParser.APP_VERSION_TAG, null);

        expected.setAppName(APP_NAME);

        assertEquals(Collections.singletonList(expected), appVersionsParser.getAppVersions());
    }

    @Test
    public void testParser_whenXmlAppVersionHasAppNameAndInvalidVersionNum_thenExpectAppVersionWithoutVersionNum()
            throws SAXException {
        appVersionsParser.startElement(null, AppVersionsParser.APP_VERSION_TAG, null, null);
        appVersionsParser.startElement(null, AppVersion.Fields.APP_NAME, null, null);
        appVersionsParser.characters(APP_NAME.toCharArray(), 0, APP_NAME.length());
        appVersionsParser.endElement(null, AppVersion.Fields.APP_NAME, null);
        appVersionsParser.startElement(null, AppVersion.Fields.VERSION_NUM, null, null);
        appVersionsParser.characters("One".toCharArray(), 0, 3);
        appVersionsParser.endElement(null, AppVersion.Fields.VERSION_NUM, null);
        appVersionsParser.endElement(null, AppVersionsParser.APP_VERSION_TAG, null);

        expected.setAppName(APP_NAME);

        assertEquals(Collections.singletonList(expected), appVersionsParser.getAppVersions());
    }

    @Test
    public void testParser_whenXmlAppVersionHasAppNameAndVersionNum_thenExpectMatchingAppVersion()
            throws SAXException {
        appVersionsParser.startElement(null, AppVersionsParser.APP_VERSION_TAG, null, null);
        appVersionsParser.startElement(null, AppVersion.Fields.APP_NAME, null, null);
        appVersionsParser.characters(APP_NAME.toCharArray(), 0, APP_NAME.length());
        appVersionsParser.endElement(null, AppVersion.Fields.APP_NAME, null);
        appVersionsParser.startElement(null, AppVersion.Fields.VERSION_NUM, null, null);
        appVersionsParser.characters("1".toCharArray(), 0, 1);
        appVersionsParser.endElement(null, AppVersion.Fields.VERSION_NUM, null);
        appVersionsParser.endElement(null, AppVersionsParser.APP_VERSION_TAG, null);

        expected.setAppName(APP_NAME);
        expected.setVersionNum(1);

        assertEquals(Collections.singletonList(expected), appVersionsParser.getAppVersions());
    }

    @Test
    public void testParser_whenTwoXmlAppVersionsHaveAppNameAndVersionNum_thenExpectTwoMatchingAppVersions()
            throws SAXException {
        appVersionsParser.startElement(null, AppVersionsParser.APP_VERSION_TAG, null, null);
        appVersionsParser.startElement(null, AppVersion.Fields.APP_NAME, null, null);
        appVersionsParser.characters((APP_NAME + " 1").toCharArray(), 0, (APP_NAME + " 1").length());
        appVersionsParser.endElement(null, AppVersion.Fields.APP_NAME, null);
        appVersionsParser.startElement(null, AppVersion.Fields.VERSION_NUM, null, null);
        appVersionsParser.characters("1".toCharArray(), 0, 1);
        appVersionsParser.endElement(null, AppVersion.Fields.VERSION_NUM, null);
        appVersionsParser.endElement(null, AppVersionsParser.APP_VERSION_TAG, null);

        appVersionsParser.startElement(null, AppVersionsParser.APP_VERSION_TAG, null, null);
        appVersionsParser.startElement(null, AppVersion.Fields.APP_NAME, null, null);
        appVersionsParser.characters((APP_NAME + " 2").toCharArray(), 0, (APP_NAME + " 2").length());
        appVersionsParser.endElement(null, AppVersion.Fields.APP_NAME, null);
        appVersionsParser.startElement(null, AppVersion.Fields.VERSION_NUM, null, null);
        appVersionsParser.characters("1".toCharArray(), 0, 1);
        appVersionsParser.endElement(null, AppVersion.Fields.VERSION_NUM, null);
        appVersionsParser.endElement(null, AppVersionsParser.APP_VERSION_TAG, null);

        expected.setAppName(APP_NAME + " 1");
        expected.setVersionNum(1);

        List<AppVersion> appVersions =
                Arrays.asList(expected, new AppVersion(APP_NAME + " 2", 1));

        assertEquals(appVersions, appVersionsParser.getAppVersions());
    }
}
