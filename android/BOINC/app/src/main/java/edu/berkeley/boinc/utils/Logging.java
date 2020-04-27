/*
 * This file is part of BOINC.
 * http://boinc.berkeley.edu
 * Copyright (C) 2012 University of California
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
package edu.berkeley.boinc.utils;

public class Logging {
    private Logging() {}

    public static final String TAG = "BOINC_GUI";
    public static final String WAKELOCK = TAG + ":MyPowerLock";

    public static int LOGLEVEL = -1;
    public static boolean ERROR = false;
    public static boolean WARNING = false;
    public static boolean INFO = false;
    public static boolean DEBUG = false;
    public static boolean VERBOSE = false;
    public static boolean RPC_PERFORMANCE = false;
    public static boolean RPC_DATA = false;

    public static void setLogLevel(int logLevel) {
        LOGLEVEL = logLevel;
        ERROR = LOGLEVEL > 0;
        WARNING = LOGLEVEL > 1;
        INFO = LOGLEVEL > 2;
        DEBUG = LOGLEVEL > 3;
        VERBOSE = LOGLEVEL > 4;
    }
}
