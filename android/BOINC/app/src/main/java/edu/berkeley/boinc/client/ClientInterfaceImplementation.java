package edu.berkeley.boinc.client;

import android.util.Log;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.collections.api.block.predicate.Predicate;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;

import edu.berkeley.boinc.rpc.AccountIn;
import edu.berkeley.boinc.rpc.AccountManager;
import edu.berkeley.boinc.rpc.AccountOut;
import edu.berkeley.boinc.rpc.AcctMgrRPCReply;
import edu.berkeley.boinc.rpc.GlobalPreferences;
import edu.berkeley.boinc.rpc.Message;
import edu.berkeley.boinc.rpc.Project;
import edu.berkeley.boinc.rpc.ProjectAttachReply;
import edu.berkeley.boinc.rpc.ProjectConfig;
import edu.berkeley.boinc.rpc.ProjectInfo;
import edu.berkeley.boinc.rpc.RpcClient;
import edu.berkeley.boinc.rpc.Transfer;
import edu.berkeley.boinc.utils.BOINCErrors;
import edu.berkeley.boinc.utils.ECLists;
import edu.berkeley.boinc.utils.Logging;

/**
 * Class implements RPC commands with the client
 * extends RpcClient with polling, re-try and other mechanisms
 * Most functions can block executing thread, do not call them from UI thread!
 */
public class ClientInterfaceImplementation extends RpcClient {

    // interval between polling retries in ms
    private final Integer minRetryInterval = 1000;

    /**
     * Reads authentication key from specified file path and authenticates GUI for advanced RPCs with the client
     *
     * @param authFilePath absolute path to file containing gui authentication key
     * @return success
     */
    public Boolean authorizeGuiFromFile(String authFilePath) {
        String authToken = readAuthToken(authFilePath);
        return authorize(authToken);
    }

    /**
     * Sets run mode of BOINC client
     *
     * @param mode see class BOINCDefs
     * @return success
     */
    public Boolean setRunMode(Integer mode) {
        return setRunMode(mode, 0);
    }

    /**
     * Sets network mode of BOINC client
     *
     * @param mode see class BOINCDefs
     * @return success
     */
    public Boolean setNetworkMode(Integer mode) {
        return setNetworkMode(mode, 0);
    }

    /**
     * Writes the given GlobalPreferences via RPC to the client. After writing, the active preferences are read back and written to ClientStatus.
     *
     * @param prefs new target preferences for the client
     * @return success
     */
    public Boolean setGlobalPreferences(GlobalPreferences prefs) {

        // try to get current client status from monitor
        ClientStatus status;
        try {
            status = Monitor.getClientStatus();
        } catch (Exception e) {
            if (Logging.WARNING) {
                Log.w(Logging.TAG, "Monitor.setGlobalPreferences: Could not load data, clientStatus not initialized.");
            }
            return false;
        }

        Boolean retval1 = setGlobalPrefsOverrideStruct(prefs); //set new override settings
        Boolean retval2 = readGlobalPrefsOverride(); //trigger reload of override settings
        if (!retval1 || !retval2) {
            return false;
        }
        GlobalPreferences workingPrefs = getGlobalPrefsWorkingStruct();
        if (workingPrefs != null) {
            status.setPrefs(workingPrefs);
            return true;
        }
        return false;
    }

    /**
     * Reads authentication token for GUI RPC authentication from file
     *
     * @param authFilePath absolute path to file containing GUI RPC authentication
     * @return GUI RPC authentication code
     */
    String readAuthToken(String authFilePath) {
        String authKey = "";
        try (BufferedReader br = new BufferedReader(new FileReader(new File(authFilePath)))) {
            authKey = br.readLine();
        } catch (FileNotFoundException fnfe) {
            if (Logging.ERROR) {
                Log.e(Logging.TAG, "Auth file not found: ", fnfe);
            }
        } catch (IOException ioe) {
            if (Logging.ERROR) {
                Log.e(Logging.TAG, "IOException: ", ioe);
            }
        }

        if (Logging.DEBUG) {
            Log.d(Logging.TAG, "Authentication key acquired. length: " + StringUtils.length(authKey));
        }
        return authKey;
    }

    /**
     * Reads project configuration for specified master URL.
     *
     * @param url master URL of the project
     * @return project configuration information
     */
    public ProjectConfig getProjectConfigPolling(String url) {
        ProjectConfig config = null;

        Boolean success = getProjectConfig(url); //asynchronous call
        if (success) { //only continue if attach command did not fail
            // verify success of getProjectConfig with poll function
            Boolean loop = true;
            while (loop) {
                loop = false;
                try {
                    Thread.sleep(minRetryInterval);
                } catch (Exception ignored) {
                }
                config = getProjectConfigPoll();
                if (config == null) {
                    if (Logging.ERROR) {
                        Log.e(Logging.TAG, "ClientInterfaceImplementation.getProjectConfigPolling: returned null.");
                    }
                    return null;
                }
                if (config.getErrorNum() == BOINCErrors.ERR_IN_PROGRESS) {
                    loop = true; //no result yet, keep looping
                } else {
                    //final result ready
                    if (config.getErrorNum() == 0) {
                        if (Logging.DEBUG) {
                            Log.d(Logging.TAG,
                                    "ClientInterfaceImplementation.getProjectConfigPolling: ProjectConfig retrieved: " +
                                            config.getName());
                        }
                    } else {
                        if (Logging.DEBUG) {
                            Log.d(Logging.TAG,
                                    "ClientInterfaceImplementation.getProjectConfigPolling: final result with error_num: " +
                                            config.getErrorNum());
                        }
                    }
                }
            }
        }
        return config;
    }

    /**
     * Attaches project, requires authenticator
     *
     * @param url           URL of project to be attached, either masterUrl(HTTP) or webRpcUrlBase(HTTPS)
     * @param projectName   name of project as shown in the manager
     * @param authenticator user authentication key, has to be obtained first
     * @return success
     */

    public Boolean attachProject(String url, String projectName, String authenticator) {
        Boolean success = projectAttach(url, authenticator, projectName); //asynchronous call to attach project
        if (success) {
            // verify success of projectAttach with poll function
            ProjectAttachReply reply = projectAttachPoll();
            while (reply != null && reply.getErrorNum() ==
                                    BOINCErrors.ERR_IN_PROGRESS) { // loop as long as reply.error_num == BOINCErrors.ERR_IN_PROGRESS
                try {
                    Thread.sleep(minRetryInterval);
                } catch (Exception ignored) {
                }
                reply = projectAttachPoll();
            }
            return (reply != null && reply.getErrorNum() == BOINCErrors.ERR_OK);
        } else if (Logging.DEBUG) {
            Log.d(Logging.TAG, "rpc.projectAttach failed.");
        }
        return false;
    }

    /**
     * Checks whether project of given master URL is currently attached to BOINC client
     *
     * @param url master URL of the project
     * @return true if attached
     */

    public Boolean checkProjectAttached(String url) {
        try {
            List<Project> attachedProjects = getProjectStatus();
            for (Project project : attachedProjects) {
                if (Logging.DEBUG) {
                    Log.d(Logging.TAG, project.getMasterURL() + " vs " + url);
                }
                if (project.getMasterURL().equals(url)) {
                    return true;
                }
            }
        } catch (Exception e) {
            if (Logging.ERROR) {
                Log.e(Logging.TAG, "ClientInterfaceImplementation.checkProjectAttached() error: ", e);
            }
        }
        return false;
    }

    /**
     * Looks up account credentials for given user data.
     * Contains authentication key for project attachment.
     *
     * @param credentials account credentials
     * @return account credentials
     */

    public AccountOut lookupCredentials(AccountIn credentials) {
        AccountOut auth = null;
        Boolean success = lookupAccount(credentials); //asynch
        if (success) {
            // get authentication token from lookupAccountPoll
            Boolean loop = true;
            while (loop) {
                loop = false;
                try {
                    Thread.sleep(minRetryInterval);
                } catch (Exception ignored) {
                }
                auth = lookupAccountPoll();
                if (auth == null) {
                    if (Logging.ERROR) {
                        Log.e(Logging.TAG, "ClientInterfaceImplementation.lookupCredentials: returned null.");
                    }
                    return null;
                }
                if (auth.getErrorNum() == BOINCErrors.ERR_IN_PROGRESS) {
                    loop = true; //no result yet, keep looping
                } else {
                    //final result ready
                    if (auth.getErrorNum() == 0) {
                        if (Logging.DEBUG) {
                            Log.d(Logging.TAG, "ClientInterfaceImplementation.lookupCredentials: authenticator retrieved.");
                        }
                    } else {
                        if (Logging.DEBUG) {
                            Log.d(Logging.TAG,
                                    "ClientInterfaceImplementation.lookupCredentials: final result with error_num: " +
                                    auth.getErrorNum());
                        }
                    }
                }
            }
        } else if (Logging.DEBUG) {
            Log.d(Logging.TAG, "rpc.lookupAccount failed.");
        }
        return auth;
    }

    /**
     * Runs transferOp for a list of given transfers.
     * E.g. batch pausing of transfers
     *
     * @param transfers list of transfered operation gets executed for
     * @param operation see BOINCDefs
     * @return success
     */

    boolean transferOperation(List<Transfer> transfers, int operation) {
        boolean success = true;
        for (Transfer transfer : transfers) {
            success = success && transferOp(operation, transfer.getProjectUrl(), transfer.getName());
            if (Logging.DEBUG) Log.d(Logging.TAG, "transfer: " + transfer.getName() + " " +
                                                  success);
        }
        return success;
    }

    /**
     * Creates account for given user information and returns account credentials if successful.
     *
     * @param information account credentials
     * @return account credentials (see status inside, to check success)
     */

    public AccountOut createAccountPolling(AccountIn information) {
        AccountOut auth = null;

        Boolean success = createAccount(information); //asynchronous call to attach project
        if (success) {
            Boolean loop = true;
            while (loop) {
                loop = false;
                try {
                    Thread.sleep(minRetryInterval);
                } catch (Exception ignored) {
                }
                auth = createAccountPoll();
                if (auth == null) {
                    if (Logging.ERROR)
                        Log.e(Logging.TAG, "ClientInterfaceImplementation.createAccountPolling: returned null.");
                    return null;
                }
                if (auth.getErrorNum() == BOINCErrors.ERR_IN_PROGRESS) {
                    loop = true; //no result yet, keep looping
                } else {
                    //final result ready
                    if (auth.getErrorNum() == 0) {
                        if (Logging.DEBUG)
                            Log.d(Logging.TAG, "ClientInterfaceImplementation.createAccountPolling: authenticator retrieved.");
                    } else {
                        if (Logging.DEBUG)
                            Log.d(Logging.TAG, "ClientInterfaceImplementation.createAccountPolling: final result with error_num: "
                                               + auth.getErrorNum());
                    }
                }
            }
        } else {
            if (Logging.DEBUG) Log.d(Logging.TAG, "rpc.createAccount returned false.");
        }
        return auth;
    }

    /**
     * Adds account manager to BOINC client.
     * There can only be a single acccount manager be active at a time.
     *
     * @param url      URL of account manager
     * @param userName user name
     * @param pwd      password
     * @return status of attachment
     */

    public AcctMgrRPCReply addAcctMgr(String url, String userName, String pwd) {
        AcctMgrRPCReply reply = null;
        Boolean success = acctMgrRPC(url, userName, pwd);
        if (success) {
            Boolean loop = true;
            while (loop) {
                reply = acctMgrRPCPoll();
                if (reply == null || reply.getErrorNum() != BOINCErrors.ERR_IN_PROGRESS) {
                    loop = false;
                    //final result ready
                    if (reply == null) {
                        if (Logging.DEBUG)
                            Log.d(Logging.TAG, "ClientInterfaceImplementation.addAcctMgr: failed, reply null.");
                    } else {
                        if (Logging.DEBUG)
                            Log.d(Logging.TAG, "ClientInterfaceImplementation.addAcctMgr: returned "
                                               + reply.getErrorNum());
                    }
                } else {
                    try {
                        Thread.sleep(minRetryInterval);
                    } catch (Exception ignored) {
                    }
                }
            }
        } else {
            if (Logging.DEBUG) Log.d(Logging.TAG, "rpc.acctMgrRPC returned false.");
        }
        return reply;
    }


    /**
     * Synchronized BOINC client projects with information of account manager.
     * Sequence copied from BOINC's desktop manager.
     *
     * @param url URL of account manager
     * @return success
     */
    boolean synchronizeAcctMgr(String url) {
        // 1st get_project_config for account manager url
        boolean success = getProjectConfig(url);
        ProjectConfig reply;
        if (success) {
            boolean loop = true;
            while (loop) {
                loop = false;
                try {
                    Thread.sleep(minRetryInterval);
                } catch (Exception ignored) {
                }
                reply = getProjectConfigPoll();
                if (reply == null) {
                    if (Logging.ERROR)
                        Log.e(Logging.TAG, "ClientInterfaceImplementation.synchronizeAcctMgr: getProjectConfigreturned null.");
                    return false;
                }
                if (reply.getErrorNum() == BOINCErrors.ERR_IN_PROGRESS) {
                    loop = true; //no result yet, keep looping
                } else {
                    //final result ready
                    if (reply.getErrorNum() == 0) {
                        if (Logging.DEBUG)
                            Log.d(Logging.TAG, "ClientInterfaceImplementation.synchronizeAcctMgr: project config retrieved.");
                    } else {
                        if (Logging.DEBUG)
                            Log.d(Logging.TAG, "ClientInterfaceImplementation.synchronize" +
                                               "AcctMgr: final result with error_num: " + reply.getErrorNum());
                    }
                }
            }
        } else {
            if (Logging.DEBUG) Log.d(Logging.TAG, "rpc.getProjectConfig returned false.");
        }

        // 2nd acct_mgr_rpc with <use_config_file/>
        AcctMgrRPCReply reply2;
        success = acctMgrRPC(); //asynchronous call to synchronize account manager
        if (success) {
            boolean loop = true;
            while (loop) {
                loop = false;
                try {
                    Thread.sleep(minRetryInterval);
                } catch (Exception ignored) {
                }
                reply2 = acctMgrRPCPoll();
                if (reply2 == null) {
                    if (Logging.ERROR)
                        Log.e(Logging.TAG, "ClientInterfaceImplementation.synchronizeAcctMgr: acctMgrRPCPoll returned null.");
                    return false;
                }
                if (reply2.getErrorNum() == BOINCErrors.ERR_IN_PROGRESS) {
                    loop = true; //no result yet, keep looping
                } else {
                    //final result ready
                    if (reply2.getErrorNum() == 0) {
                        if (Logging.DEBUG)
                            Log.d(Logging.TAG, "ClientInterfaceImplementation.synchronizeAcctMgr: acct mngr reply retrieved.");
                    } else {
                        if (Logging.DEBUG)
                            Log.d(Logging.TAG, "ClientInterfaceImplementation.synchronizeAcctMgr: final result with error_num: " + reply2.getErrorNum());
                    }
                }
            }
        } else {
            if (Logging.DEBUG) Log.d(Logging.TAG, "rpc.acctMgrRPC returned false.");
        }

        return true;
    }

    @Override
    public boolean setCcConfig(String ccConfig) {
        // set CC config and trigger re-read.
        super.setCcConfig(ccConfig);
        return super.readCcConfig();
    }

    /**
     * Returns List of event log messages
     *
     * @param seqNo  lower bound of sequence number
     * @param number number of messages returned max, can be less
     * @return list of messages
     */

    // returns given number of client messages, older than provided seqNo
    // if seqNo <= 0 initial data retrieval
    List<Message> getEventLogMessages(int seqNo, int number) {
        // determine oldest message seqNo for data retrieval
        int lowerBound;
        if (seqNo > 0)
            lowerBound = seqNo - number - 2;
        else
            lowerBound = getMessageCount() - number - 1; // can result in >number results, if client writes message btwn. here and rpc.getMessages!

        // less than desired number of messsages available, adapt lower bound
        if (lowerBound < 0)
            lowerBound = 0;

        // returns every message with seqNo > lowerBound
        MutableList<Message> messages = ECLists.mutable.ofAll(getMessages(lowerBound));

        if (seqNo > 0) {
            // remove messages that are >= seqNo
            messages.removeIf(message -> message.getSeqno() >= seqNo);
        }

        if(!messages.isEmpty() && Logging.DEBUG) {
            Log.d(Logging.TAG, "getEventLogMessages: returning array with " + messages.size()
                               + " entries. for lowerBound: " + lowerBound + " at 0: "
                               + messages.get(0).getSeqno() + " at " + (messages.size() - 1) + ": "
                               + messages.getLast().getSeqno());
        }
        return messages;
    }

    /**
     * Returns list of projects from all_projects_list.xml that...
     * - support Android
     * - support CPU architecture
     * - are not yet attached
     *
     * @return list of attachable projects
     */
    List<ProjectInfo> getAttachableProjects(String boincPlatformName, String boincAltPlatformName) {
        if (Logging.DEBUG)
            Log.d(Logging.TAG, "getAttachableProjects for platform: " + boincPlatformName + " or " + boincAltPlatformName);

        // currently attached projects
        final ImmutableList<Project> attachedProjects = ECLists.immutable.ofAll(getState().getProjects());

        // filter out projects that are already attached
        final ImmutableList<ProjectInfo> filteredProjectsList = getAllProjectsList() // all_projects_list.xml
                .select(candidate -> attachedProjects
                        .noneSatisfy(attachedProject ->
                                             attachedProject.getMasterURL().equals(candidate.getUrl())));

        final Predicate<String> supportedPlatformPredicate = supportedPlatform -> supportedPlatform.contains(boincPlatformName) ||
                                                                                  (!boincAltPlatformName.isEmpty()
                                                                                   && supportedPlatform.contains(boincAltPlatformName));
        final List<ProjectInfo> attachableProjects = filteredProjectsList
                //filter out projects that do not support Android
                .select(candidate -> ECLists.immutable.ofAll(candidate.getPlatforms())
                                                      // project is not yet attached, check whether it supports CPU architecture
                                                      .anySatisfy(supportedPlatformPredicate))
                .distinct().toList();

        if (Logging.DEBUG)
            Log.d(Logging.TAG, "getAttachableProjects: number of candidates found: "
                               + attachableProjects.size());
        return attachableProjects;
    }

    /**
     * Returns list of account managers from all_projects_list.xml
     *
     * @return list of account managers
     */
    List<AccountManager> getAccountManagers() {
        List<AccountManager> accountManagers = getAccountManagersList(); // from all_projects_list.xml

        if (Logging.DEBUG)
            Log.d(Logging.TAG, "getAccountManagers: number of account managers found: "
                               + accountManagers.size());
        return accountManagers;
    }

    ProjectInfo getProjectInfo(String url) {
        // all_projects_list.xml
        ProjectInfo projectInfo = getAllProjectsList().detect(tmp -> tmp.getUrl().equals(url));

        if (projectInfo == null && Logging.ERROR)
            Log.e(Logging.TAG, "getProjectInfo: could not find info for: " + url);

        return projectInfo;
    }

    boolean setDomainName(String deviceName) {
        boolean success = setDomainNameRpc(deviceName);
        if (Logging.DEBUG)
            Log.d(Logging.TAG, "setDomainName: success " + success);
        return success;
    }
}
