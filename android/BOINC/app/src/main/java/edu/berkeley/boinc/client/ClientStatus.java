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
package edu.berkeley.boinc.client;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.wifi.WifiManager;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.text.format.DateUtils;
import android.util.Log;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.berkeley.boinc.R;
import edu.berkeley.boinc.rpc.AcctMgrInfo;
import edu.berkeley.boinc.rpc.CcStatus;
import edu.berkeley.boinc.rpc.GlobalPreferences;
import edu.berkeley.boinc.rpc.HostInfo;
import edu.berkeley.boinc.rpc.ImageWrapper;
import edu.berkeley.boinc.rpc.Notice;
import edu.berkeley.boinc.rpc.Project;
import edu.berkeley.boinc.rpc.Result;
import edu.berkeley.boinc.rpc.Transfer;
import edu.berkeley.boinc.utils.BOINCDefs;
import edu.berkeley.boinc.utils.BOINCUtils;
import edu.berkeley.boinc.utils.ECLists;
import edu.berkeley.boinc.utils.Logging;

/*
 * Singleton that holds the client status data, as determined by the Monitor.
 * To get instance call Monitor.getClientStatus()
 */
public class ClientStatus {
    private Context ctx; // application context in order to fire broadcast events

    // CPU WakeLock
    private WakeLock wakeLock;
    // WiFi lock
    private WifiManager.WifiLock wifiLock;

    //RPC wrapper
    private CcStatus status;
    private MutableList<Result> results;
    private MutableList<Project> projects;
    private List<Transfer> transfers;
    private GlobalPreferences prefs;
    private HostInfo hostinfo;
    private AcctMgrInfo acctMgrInfo;

    // setup status
    public Integer setupStatus = 0;
    // 0 = client is in setup routine (default)
    public static final int SETUP_STATUS_LAUNCHING = 0;
    // 1 = client is launched and available for RPC (connected and authorized)
    public static final int SETUP_STATUS_AVAILABLE = 1;
    // 2 = client is in a permanent error state
    public static final int SETUP_STATUS_ERROR = 2;
    // 3 = client is launched but not attached to a project (login)
    public static final int SETUP_STATUS_NOPROJECT = 3;
    private Boolean setupStatusParseError = false;

    // computing status
    public Integer computingStatus = 2;
    public static final int COMPUTING_STATUS_NEVER = 0;
    public static final int COMPUTING_STATUS_SUSPENDED = 1;
    public static final int COMPUTING_STATUS_IDLE = 2;
    public static final int COMPUTING_STATUS_COMPUTING = 3;
    // reason why computing got suspended, only if COMPUTING_STATUS_SUSPENDED
    public Integer computingSuspendReason = 0;
    // indicates that status could not be parsed and is therefore invalid
    private Boolean computingParseError = false;

    // network status
    public Integer networkStatus = 2;
    public static final int NETWORK_STATUS_NEVER = 0;
    public static final int NETWORK_STATUS_SUSPENDED = 1;
    public static final int NETWORK_STATUS_AVAILABLE = 2;
    // reason why network activity got suspended, only if NETWORK_STATUS_SUSPENDED
    public Integer networkSuspendReason = 0;
    // indicates that status could not be parsed and is therefore invalid
    private Boolean networkParseError = false;

    // notices
    private List<Notice> rssNotices = new ArrayList<>();
    private List<Notice> serverNotices = new ArrayList<>();
    private int mostRecentNoticeSeqNo = 0;

    public ClientStatus(Context ctx) {
        this.ctx = ctx;

        // set up CPU wakelock
        // see documentation at http://developer.android.com/reference/android/os/PowerManager.html
        PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, Logging.WAKELOCK);
        // "one call to release() is sufficient to undo the effect of all previous calls to acquire()"
        wakeLock.setReferenceCounted(false);

        // Set up WiFi wakelock
        // On versions prior to Android N (24), initializing the WifiManager via Context#getSystemService
        // can cause a memory leak if the context is not the application context.
        // You should consider using context.getApplicationContext().getSystemService() rather than
        // context.getSystemService()
        WifiManager wm = (WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL, "MyWifiLock");
        wifiLock.setReferenceCounted(false);
    }

    // call to acquire or release resources held by the WakeLock.
    // acquisition: every time the Monitor loop calls setClientStatus and computingStatus == COMPUTING_STATUS_COMPUTING
    // release: every time acquisition criteria is not met , and in Monitor.onDestroy()
    @SuppressLint("WakelockTimeout") // To run BOINC in the background, wakeLock has to be used without timeout.
    public void setWakeLock(Boolean acquire) {
        try {
            if(wakeLock.isHeld() == acquire) {
                return; // wakeLock already in desired state
            }

            if(acquire) { // acquire wakeLock
                wakeLock.acquire();
                if(Logging.DEBUG) {
                    Log.d(Logging.TAG, "wakeLock acquired");
                }
            }
            else { // release wakeLock
                wakeLock.release();
                if(Logging.DEBUG) {
                    Log.d(Logging.TAG, "wakeLock released");
                }
            }
        }
        catch(Exception e) {
            if(Logging.WARNING) {
                Log.w(Logging.TAG, "Exception during setWakeLock " + acquire, e);
            }
        }
    }

    // call to acquire or release resources held by the WifiLock.
    // acquisition: every time the Monitor loop calls setClientStatus: computingStatus == COMPUTING_STATUS_COMPUTING || COMPUTING_STATUS_IDLE
    // release: every time acquisition criteria is not met , and in Monitor.onDestroy()
    public void setWifiLock(Boolean acquire) {
        try {
            if(wifiLock.isHeld() == acquire) {
                return; // wifiLock already in desired state
            }

            if(acquire) { // acquire wakeLock
                wifiLock.acquire();
                if(Logging.DEBUG) {
                    Log.d(Logging.TAG, "wifiLock acquired");
                }
            }
            else { // release wakeLock
                wifiLock.release();
                if(Logging.DEBUG) {
                    Log.d(Logging.TAG, "wifiLock released");
                }
            }
        }
        catch(Exception e) {
            if(Logging.WARNING) {
                Log.w(Logging.TAG, "Exception during setWifiLock " + acquire, e);
            }
        }
    }

    /*
     * fires "clientstatuschange" broadcast, so registered Activities can update their model.
     */
    public synchronized void fire() {
        if(ctx != null) {
            Intent clientChanged = new Intent();
            clientChanged.setAction("edu.berkeley.boinc.clientstatuschange");
            ctx.sendBroadcast(clientChanged, null);
        }
        else {
            if(Logging.DEBUG) {
                Log.d(Logging.TAG, "ClientStatus cant fire, not context set!");
            }
        }
    }

    /*
     * called frequently by Monitor to set the RPC data. These objects are used to determine the client status and parse it in the data model of this class.
     */
    synchronized void setClientStatus(CcStatus status, List<Result> results, List<Project> projects, List<Transfer> transfers, HostInfo hostinfo, AcctMgrInfo acctMgrInfo, List<Notice> newNotices) {
        this.status = status;
        this.results = ECLists.mutable.ofAll(results);
        this.projects = ECLists.mutable.ofAll(projects);
        this.transfers = transfers;
        this.hostinfo = hostinfo;
        this.acctMgrInfo = acctMgrInfo;
        parseClientStatus();
        appendNewNotices(newNotices);
        if(Logging.VERBOSE) {
            Log.v(Logging.TAG,
                  "setClientStatus: #results:" + results.size() + " #projects:" + projects.size() + " #transfers:" +
                  transfers.size() + " // computing: " + computingParseError + computingStatus +
                  computingSuspendReason + " - network: " + networkParseError + networkStatus + networkSuspendReason);
        }
        if(!computingParseError && !networkParseError && !setupStatusParseError) {
            fire(); // broadcast that status has changed
        }
        else {
            if(Logging.DEBUG) {
                Log.d(Logging.TAG,
                      "ClientStatus discard status change due to parse error" + computingParseError + computingStatus +
                      computingSuspendReason + "-" + networkParseError + networkStatus + networkSuspendReason + "-" +
                      setupStatusParseError);
            }
        }
    }

    /*
     * called when setup status needs to be manipulated by Java routine
     * either during setup or closing of client.
     * this function does not effect the state of the client!
     */
    public synchronized void setSetupStatus(Integer newStatus, Boolean fireStatusChangeEvent) {
        setupStatus = newStatus;
        if(fireStatusChangeEvent) {
            fire();
        }
    }

    /*
     * called after reading global preferences, e.g. during ClientStartAsync
     */
    public synchronized void setPrefs(GlobalPreferences prefs) {
        this.prefs = prefs;
    }

    public int getMostRecentNoticeSeqNo() {
        return mostRecentNoticeSeqNo;
    }

    public synchronized List<Notice> getRssNotices() {
        return rssNotices;
    }

    public synchronized List<Notice> getServerNotices() {
        return serverNotices;
    }

    public synchronized CcStatus getClientStatus() {
        if(results == null) { //check in case monitor is not set up yet (e.g. while logging in)
            if(Logging.DEBUG) {
                Log.d(Logging.TAG, "state is null");
            }
            return null;
        }
        return status;
    }

    public synchronized List<Result> getTasks() {
        if(results == null) { //check in case monitor is not set up yet (e.g. while logging in)
            if(Logging.DEBUG) {
                Log.d(Logging.TAG, "state is null");
            }
            return Collections.emptyList();
        }
        return results;
    }

    public synchronized List<Transfer> getTransfers() {
        if(transfers == null) { //check in case monitor is not set up yet (e.g. while logging in)
            if(Logging.DEBUG) {
                Log.d(Logging.TAG, "transfers is null");
            }
            return Collections.emptyList();
        }
        return transfers;
    }

    public synchronized GlobalPreferences getPrefs() {
        if(prefs == null) { //check in case monitor is not set up yet (e.g. while logging in)
            if(Logging.DEBUG) {
                Log.d(Logging.TAG, "prefs is null");
            }
            return null;
        }
        return prefs;
    }

    public synchronized List<Project> getProjects() {
        if(projects == null) { //check in case monitor is not set up yet (e.g. while logging in)
            if(Logging.DEBUG) {
                Log.d(Logging.TAG, "getProject() state is null");
            }
            return Collections.emptyList();
        }
        return projects;
    }

    synchronized String getProjectStatus(String masterURL) {
        StringBuffer sb = new StringBuffer();
        final ImmutableList<Project> filteredProjects = projects
                .select(project -> project.getMasterURL().equals(masterURL)).toImmutable();
        for(Project project : filteredProjects) {
            if(project.getSuspendedViaGUI()) {
                appendToStatus(sb, ctx.getResources().getString(R.string.projects_status_suspendedviagui));
            }
            if(project.getDoNotRequestMoreWork()) {
                appendToStatus(sb, ctx.getResources().getString(R.string.projects_status_dontrequestmorework));
            }
            if(project.getEnded()) {
                appendToStatus(sb, ctx.getResources().getString(R.string.projects_status_ended));
            }
            if(project.getDetachWhenDone()) {
                appendToStatus(sb, ctx.getResources().getString(R.string.projects_status_detachwhendone));
            }
            if(project.getScheduledRPCPending() > 0) {
                appendToStatus(sb, ctx.getResources().getString(R.string.projects_status_schedrpcpending));
                appendToStatus(sb, BOINCUtils.translateRPCReason(ctx, project.getScheduledRPCPending()));
            }
            if(project.getSchedulerRPCInProgress()) {
                appendToStatus(sb, ctx.getResources().getString(R.string.projects_status_schedrpcinprogress));
            }
            if(project.getTrickleUpPending()) {
                appendToStatus(sb, ctx.getResources().getString(R.string.projects_status_trickleuppending));
            }

            Calendar minRPCTime = Calendar.getInstance();
            Calendar now = Calendar.getInstance();
            minRPCTime.setTimeInMillis((long) project.getMinRPCTime() * 1000);
            if(minRPCTime.compareTo(now) > 0) {
                appendToStatus(
                        sb,
                        ctx.getResources().getString(R.string.projects_status_backoff) + " " +
                        DateUtils.formatElapsedTime((minRPCTime.getTimeInMillis() - now.getTimeInMillis()) / 1000)
                );
            }
        }
        return sb.toString();
    }

    private void appendToStatus(StringBuffer existing, String additional) {
        if(existing.length() == 0) {
            existing.append(additional);
        }
        else {
            existing.append(", ");
            existing.append(additional);
        }
    }

    public synchronized HostInfo getHostInfo() {
        if(hostinfo == null) {
            if(Logging.DEBUG) {
                Log.d(Logging.TAG, "getHostInfo() state is null");
            }
            return null;
        }
        return hostinfo;
    }

    public synchronized AcctMgrInfo getAcctMgrInfo() {
        return acctMgrInfo; // can be null
    }

    // returns all slideshow images for given project
    // images: 126 * 290 pixel from /projects/PNAME/slideshow_appname_n
    // not aware of application!
    synchronized List<ImageWrapper> getSlideshowForProject(String masterURL) {
        List<ImageWrapper> images = new ArrayList<>();
        final ImmutableList<Project> filteredProjects = projects
                .select(project -> project.getMasterURL().equals(masterURL)).toImmutable();
        for(Project project : filteredProjects) {
            // get file paths of soft link files
            File dir = new File(project.getProjectDir());

            // prevent NPE
            File[] foundFiles = ArrayUtils.nullToEmpty(dir.listFiles((dir1, name) -> name.startsWith("slideshow_")
                                                                                     && !name.endsWith(".png")),
                                                       File[].class);

            // check whether path is not empty, and avoid duplicates (slideshow images can
            // re-occur for multiple apps. Since we do not distinguish between apps, skip
            // duplicates.
            ImmutableList<String> allImagePaths = ECLists.immutable.of(foundFiles)
                                                      .collect(file -> parseSoftLinkToAbsPath(file.getAbsolutePath(),
                                                                                              project.getProjectDir()))
                                                      .select(StringUtils::isNotEmpty).distinct();

            // load images from paths
            for(String filePath : allImagePaths) {
                Bitmap tmp = BitmapFactory.decodeFile(filePath);
                if(tmp != null) {
                    images.add(new ImageWrapper(tmp, project.getProjectName(), filePath));
                }
                else if(Logging.DEBUG) {
                    Log.d(Logging.TAG, "loadSlideshowImagesFromFile(): null for path: " + filePath);
                }
            }
        }
        return images;
    }

    // returns project icon for given master url
    // bitmap: 40 * 40 pixel, symbolic link in /projects/PNAME/stat_icon
    public synchronized Bitmap getProjectIcon(String masterURL) {
        if(Logging.VERBOSE) {
            Log.v(Logging.TAG, "getProjectIcon for: " + masterURL);
        }
        try {
            final ImmutableList<Project> filteredProjects = projects
                    .select(project -> project.getMasterURL().equals(masterURL)).toImmutable();
            // loop through filtered projects
            for(Project project : filteredProjects) {
                // read file name of icon
                String iconAbsPath =
                        parseSoftLinkToAbsPath(project.getProjectDir() + "/stat_icon",
                                               project.getProjectDir());
                if(iconAbsPath == null) {
                    if(Logging.VERBOSE) {
                        Log.v(Logging.TAG, "getProjectIcon could not parse sym link for project: " +
                                           masterURL);
                    }
                    return null;
                }
                return BitmapFactory.decodeFile(iconAbsPath);
            }
        }
        catch(Exception e) {
            if(Logging.WARNING) {
                Log.w(Logging.TAG, "getProjectIcon failed", e);
            }
        }
        if(Logging.WARNING) {
            Log.w(Logging.TAG, "getProjectIcon: project not found.");
        }
        return null;
    }

    // returns project icon for given project name
    // bitmap: 40 * 40 pixel, symbolic link in /projects/PNAME/stat_icon
    public synchronized Bitmap getProjectIconByName(String projectName) {
        if(Logging.VERBOSE) {
            Log.v(Logging.TAG, "getProjectIconByName for: " + projectName);
        }
        try {
            final ImmutableList<Project> filteredProjects = projects
                    .select(project -> project.getProjectName().equals(projectName)).toImmutable();
            // loop through filtered projects
            for(Project project : filteredProjects) {
                // read file name of icon
                String iconAbsPath =
                        parseSoftLinkToAbsPath(project.getProjectDir() + "/stat_icon",
                                               project.getProjectDir());
                if(iconAbsPath == null) {
                    if(Logging.VERBOSE) {
                        Log.v(Logging.TAG,
                              "getProjectIconByName could not parse sym link for project: " +
                              projectName);
                    }
                    return null;
                }
                return BitmapFactory.decodeFile(iconAbsPath);
            }
        }
        catch(Exception e) {
            if(Logging.WARNING) {
                Log.w(Logging.TAG, "getProjectIconByName failed", e);
            }
        }
        if(Logging.WARNING) {
            Log.w(Logging.TAG, "getProjectIconByName: project not found.");
        }
        return null;
    }

    List<Result> getExecutingTasks() {
        return results.select(Result::isActiveTask)
                      .select(result -> result.getActiveTaskState() == BOINCDefs.PROCESS_EXECUTING);
    }

    public String getCurrentStatusTitle() {
        String statusTitle = "";
        try {
            switch(setupStatus) {
                case SETUP_STATUS_AVAILABLE:
                    switch(computingStatus) {
                        case COMPUTING_STATUS_COMPUTING:
                            statusTitle = ctx.getString(R.string.status_running);
                            break;
                        case COMPUTING_STATUS_IDLE:
                            statusTitle = ctx.getString(R.string.status_idle);
                            break;
                        case COMPUTING_STATUS_SUSPENDED:
                            statusTitle = ctx.getString(R.string.status_paused);
                            break;
                        case COMPUTING_STATUS_NEVER:
                            statusTitle = ctx.getString(R.string.status_computing_disabled);
                            break;
                    }
                    break;
                case SETUP_STATUS_LAUNCHING:
                    statusTitle = ctx.getString(R.string.status_launching);
                    break;
                case SETUP_STATUS_NOPROJECT:
                    statusTitle = ctx.getString(R.string.status_noproject);
                    break;
            }
        }
        catch(Exception e) {
            if(Logging.WARNING) {
                Log.w(Logging.TAG, "error parsing setup status string", e);
            }
        }
        return statusTitle;
    }

    public String getCurrentStatusDescription() {
        String statusString = "";
        try {
            switch(computingStatus) {
                case COMPUTING_STATUS_COMPUTING:
                    statusString = ctx.getString(R.string.status_running_long);
                    break;
                case COMPUTING_STATUS_IDLE:
                    if(networkSuspendReason == BOINCDefs.SUSPEND_REASON_WIFI_STATE) {
                        // Network suspended due to wifi state
                        statusString = ctx.getString(R.string.suspend_wifi);
                    }
                    else if(networkSuspendReason == BOINCDefs.SUSPEND_REASON_NETWORK_QUOTA_EXCEEDED) {
                        // network suspend due to traffic quota
                        statusString = ctx.getString(R.string.suspend_network_quota);
                    }
                    else {
                        statusString = ctx.getString(R.string.status_idle_long);
                    }
                    break;
                case COMPUTING_STATUS_SUSPENDED:
                    switch(computingSuspendReason) {
                        case BOINCDefs.SUSPEND_REASON_USER_REQ:
                            // restarting after user has previously manually suspended computation
                            statusString = ctx.getString(R.string.suspend_user_req);
                            break;
                        case BOINCDefs.SUSPEND_REASON_BENCHMARKS:
                            statusString = ctx.getString(R.string.status_benchmarking);
                            break;
                        case BOINCDefs.SUSPEND_REASON_BATTERIES:
                            statusString = ctx.getString(R.string.suspend_batteries);
                            break;
                        case BOINCDefs.SUSPEND_REASON_BATTERY_CHARGING:
                            statusString = ctx.getString(R.string.suspend_battery_charging);
                            try {
                                double minCharge = prefs.getBatteryChargeMinPct();
                                int currentCharge = Monitor.getDeviceStatus().getStatus().getBatteryChargePct();
                                statusString = ctx.getString(R.string.suspend_battery_charging_long) + " " +
                                               (int) minCharge
                                               + "% (" + ctx.getString(R.string.suspend_battery_charging_current) +
                                               " " + currentCharge + "%) "
                                               + ctx.getString(R.string.suspend_battery_charging_long2);
                            }
                            catch(Exception e) {
                                if(Logging.ERROR) {
                                    Log.e(Logging.TAG, "ClientStatus.getCurrentStatusDescription error: ", e);
                                }
                            }
                            break;
                        case BOINCDefs.SUSPEND_REASON_BATTERY_OVERHEATED:
                            statusString = ctx.getString(R.string.suspend_battery_overheating);
                            break;
                        case BOINCDefs.SUSPEND_REASON_USER_ACTIVE:
                            boolean suspendDueToScreenOn = Monitor.getAppPrefs().getSuspendWhenScreenOn();
                            if(suspendDueToScreenOn) {
                                statusString = ctx.getString(R.string.suspend_screen_on);
                            }
                            else {
                                statusString = ctx.getString(R.string.suspend_useractive);
                            }
                            break;
                        case BOINCDefs.SUSPEND_REASON_TIME_OF_DAY:
                            statusString = ctx.getString(R.string.suspend_tod);
                            break;
                        case BOINCDefs.SUSPEND_REASON_DISK_SIZE:
                            statusString = ctx.getString(R.string.suspend_disksize);
                            break;
                        case BOINCDefs.SUSPEND_REASON_CPU_THROTTLE:
                            statusString = ctx.getString(R.string.suspend_cputhrottle);
                            break;
                        case BOINCDefs.SUSPEND_REASON_NO_RECENT_INPUT:
                            statusString = ctx.getString(R.string.suspend_noinput);
                            break;
                        case BOINCDefs.SUSPEND_REASON_INITIAL_DELAY:
                            statusString = ctx.getString(R.string.suspend_delay);
                            break;
                        case BOINCDefs.SUSPEND_REASON_EXCLUSIVE_APP_RUNNING:
                            statusString = ctx.getString(R.string.suspend_exclusiveapp);
                            break;
                        case BOINCDefs.SUSPEND_REASON_CPU_USAGE:
                            statusString = ctx.getString(R.string.suspend_cpu);
                            break;
                        case BOINCDefs.SUSPEND_REASON_NETWORK_QUOTA_EXCEEDED:
                            statusString = ctx.getString(R.string.suspend_network_quota);
                            break;
                        case BOINCDefs.SUSPEND_REASON_OS:
                            statusString = ctx.getString(R.string.suspend_os);
                            break;
                        case BOINCDefs.SUSPEND_REASON_WIFI_STATE:
                            statusString = ctx.getString(R.string.suspend_wifi);
                            break;
                        default:
                            statusString = ctx.getString(R.string.suspend_unknown);
                            break;
                    }
                    break;
                case COMPUTING_STATUS_NEVER:
                    statusString = ctx.getString(R.string.status_computing_disabled_long);
                    break;
            }
        }
        catch(Exception e) {
            if(Logging.WARNING) {
                Log.w(Logging.TAG, "error parsing setup status string", e);
            }
        }
        return statusString;
    }

    /*
     * parses RPC data to ClientStatus data model.
     */
    private void parseClientStatus() {
        parseComputingStatus();
        parseProjectStatus();
        parseNetworkStatus();
    }

    private void parseProjectStatus() {
        try {
            if(!projects.isEmpty()) {
                setupStatus = SETUP_STATUS_AVAILABLE;
                setupStatusParseError = false;
            }
            else { //not projects attached
                setupStatus = SETUP_STATUS_NOPROJECT;
                setupStatusParseError = false;
            }
        }
        catch(Exception e) {
            setupStatusParseError = true;
            if(edu.berkeley.boinc.utils.Logging.LOGLEVEL <= 4) {
                Log.e(Logging.TAG, "parseProjectStatus - Exception", e);
            }
            if(Logging.DEBUG) {
                Log.d(Logging.TAG, "error parsing setup status (project state)");
            }
        }
    }

    private void parseComputingStatus() {
        computingParseError = true;
        try {
            if(status.getTaskMode() == BOINCDefs.RUN_MODE_NEVER) {
                computingStatus = COMPUTING_STATUS_NEVER;
                computingSuspendReason = status.getTaskSuspendReason(); // = 4 - SUSPEND_REASON_USER_REQ????
                computingParseError = false;
                return;
            }
            if((status.getTaskMode() == BOINCDefs.RUN_MODE_AUTO) &&
               (status.getTaskSuspendReason() != BOINCDefs.SUSPEND_NOT_SUSPENDED) &&
               (status.getTaskSuspendReason() != BOINCDefs.SUSPEND_REASON_CPU_THROTTLE)) {
                // do not expose cpu throttling as suspension to UI
                computingStatus = COMPUTING_STATUS_SUSPENDED;
                computingSuspendReason = status.getTaskSuspendReason();
                computingParseError = false;
                return;
            }
            if((status.getTaskMode() == BOINCDefs.RUN_MODE_AUTO) &&
               ((status.getTaskSuspendReason() == BOINCDefs.SUSPEND_NOT_SUSPENDED) ||
                (status.getTaskSuspendReason() == BOINCDefs.SUSPEND_REASON_CPU_THROTTLE))) {
                // treat cpu throttling as if client was active (either idle, or computing, depending on tasks)
                //figure out whether we have an active task
                boolean activeTask = false;
                if(results != null) {
                    // this result has corresponding "active task" in RPC XML
                    activeTask = results.anySatisfy(Result::isActiveTask);
                }

                if(activeTask) { // client is currently computing
                    computingStatus = COMPUTING_STATUS_COMPUTING;
                    computingSuspendReason = status.getTaskSuspendReason(); // = 0 - SUSPEND_NOT_SUSPENDED
                    computingParseError = false;
                }
                else { // client "is able but idle"
                    computingStatus = COMPUTING_STATUS_IDLE;
                    computingSuspendReason = status.getTaskSuspendReason(); // = 0 - SUSPEND_NOT_SUSPENDED
                    computingParseError = false;
                }
            }
        }
        catch(Exception e) {
            if(edu.berkeley.boinc.utils.Logging.LOGLEVEL <= 4) {
                Log.e(Logging.TAG, "ClientStatus parseComputingStatus - Exception", e);
            }
            if(Logging.DEBUG) {
                Log.d(Logging.TAG, "ClientStatus error - client computing status");
            }
        }
    }

    private void parseNetworkStatus() {
        networkParseError = true;
        try {
            if(status.getNetworkMode() == BOINCDefs.RUN_MODE_NEVER) {
                networkStatus = NETWORK_STATUS_NEVER;
                networkSuspendReason = status.getNetworkSuspendReason(); // = 4 - SUSPEND_REASON_USER_REQ????
                networkParseError = false;
                return;
            }
            if((status.getNetworkMode() == BOINCDefs.RUN_MODE_AUTO) &&
               (status.getNetworkSuspendReason() != BOINCDefs.SUSPEND_NOT_SUSPENDED)) {
                networkStatus = NETWORK_STATUS_SUSPENDED;
                networkSuspendReason = status.getNetworkSuspendReason();
                networkParseError = false;
                return;
            }
            if((status.getNetworkMode() == BOINCDefs.RUN_MODE_AUTO) &&
               (status.getNetworkSuspendReason() == BOINCDefs.SUSPEND_NOT_SUSPENDED)) {
                networkStatus = NETWORK_STATUS_AVAILABLE;
                networkSuspendReason = status.getNetworkSuspendReason(); // = 0 - SUSPEND_NOT_SUSPENDED
                networkParseError = false;
            }
        }
        catch(Exception e) {
            if(edu.berkeley.boinc.utils.Logging.LOGLEVEL <= 4) {
                Log.e(Logging.TAG, "ClientStatus parseNetworkStatus - Exception", e);
            }
            if(Logging.DEBUG) {
                Log.d(Logging.TAG, "ClientStatus error - client network status");
            }
        }
    }

    private void appendNewNotices(List<Notice> newNotices) {
        for(Notice newNotice : newNotices) {
            if(Logging.DEBUG) {
                Log.d(Logging.TAG, "ClientStatus.appendNewNotices new notice with seq number: " +
                                   newNotice.getSeqno() + " is server notice: " +
                                   newNotice.isServerNotice());
            }
            if(newNotice.getSeqno() > mostRecentNoticeSeqNo) {
                if(!newNotice.isClientNotice() && !newNotice.isServerNotice()) {
                    rssNotices.add(newNotice);
                }
                if(newNotice.isServerNotice()) {
                    serverNotices.add(newNotice);
                }
                mostRecentNoticeSeqNo = newNotice.getSeqno();
            }
        }
    }

    // helper method for loading images from file
    // reads the symbolic link provided in pathOfSoftLink file
    // and returns absolute path to an image file.
    private String parseSoftLinkToAbsPath(String pathOfSoftLink, String projectDir) {
        // setup file
        File softLink = new File(pathOfSoftLink);
        if(!softLink.exists()) {
            return null; // return if file does not exist
        }

        // reading text of symbolic link
        String softLinkContent = "";
        try {
            try(FileInputStream stream = new FileInputStream(softLink)) {
                FileChannel fc = stream.getChannel();
                MappedByteBuffer bb = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
                /* Instead of using default, pass in a decoder. */
                softLinkContent = Charset.defaultCharset().decode(bb).toString();
            }
            catch(IOException e) {
                if(Logging.WARNING) {
                    Log.w(Logging.TAG, "IOException in parseIconFileName()", e);
                }
            }
        }
        catch(Exception e) {
            // probably FileNotFoundException
            return null;
        }

        // matching relevant path of String
        // matching 1+ word characters and 0 or 1 dot . and 0+ word characters
        // e.g. "icon.png", "icon", "icon.bmp"
        Pattern statIconPattern = Pattern.compile("/(\\w+?\\.?\\w*?)</soft_link>");
        Matcher m = statIconPattern.matcher(softLinkContent);
        if(!m.find()) {
            if(Logging.WARNING) {
                Log.w(Logging.TAG,
                      "parseSoftLinkToAbsPath() could not match pattern in soft link file: " + pathOfSoftLink);
            }
            return null;
        }
        String fileName = m.group(1);

        return projectDir + "/" + fileName;
    }
}
