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
package edu.berkeley.boinc;

import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.impl.list.mutable.FastList;

import java.util.List;

import edu.berkeley.boinc.adapter.TasksListAdapter;
import edu.berkeley.boinc.rpc.Result;
import edu.berkeley.boinc.rpc.RpcClient;
import edu.berkeley.boinc.utils.BOINCDefs;
import edu.berkeley.boinc.utils.ECLists;
import edu.berkeley.boinc.utils.Logging;

public class TasksFragment extends Fragment {
    private TasksListAdapter listAdapter;
    private MutableList<TaskData> data = new FastList<>();

    private BroadcastReceiver mClientStatusChangeRec = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(Logging.VERBOSE) {
                Log.d(Logging.TAG, "TasksActivity onReceive");
            }
            loadData();
        }
    };
    private IntentFilter ifcsc = new IntentFilter("edu.berkeley.boinc.clientstatuschange");

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if(Logging.DEBUG) {
            Log.d(Logging.TAG, "TasksFragment onCreateView");
        }
        // Inflate the layout for this fragment
        View layout = inflater.inflate(R.layout.tasks_layout, container, false);
        ListView lv = layout.findViewById(R.id.tasksList);
        listAdapter = new TasksListAdapter(getActivity(), R.id.tasksList, data);
        lv.setAdapter(listAdapter);
        lv.setOnItemClickListener(itemClickListener);
        return layout;
    }

    @Override
    public void onResume() {
        super.onResume();
        //register noisy clientStatusChangeReceiver here, so only active when Activity is visible
        if(Logging.DEBUG) {
            Log.d(Logging.TAG, "TasksFragment register receiver");
        }
        getActivity().registerReceiver(mClientStatusChangeRec, ifcsc);
        loadData();
    }

    @Override
    public void onPause() {
        //unregister receiver, so there are not multiple intents flying in
        if(Logging.DEBUG) {
            Log.d(Logging.TAG, "TasksFragment remove receiver");
        }
        getActivity().unregisterReceiver(mClientStatusChangeRec);
        super.onPause();
    }

    private void loadData() {
        // try to get current client status from monitor
        List<Result> tmpA;
        try {
            tmpA = BOINCActivity.monitor.getTasks();
        }
        catch(Exception e) {
            if(Logging.WARNING) {
                Log.w(Logging.TAG, "TasksActivity: Could not load data, clientStatus not initialized.");
            }
            return;
        }
        //setup list and adapter
        if(tmpA != null) { //can be null before first monitor status cycle (e.g. when not logged in or during startup)
            //deep copy, so ArrayList adapter actually recognizes the difference
            updateData(tmpA);

            listAdapter.notifyDataSetChanged(); //force list adapter to refresh
        }
        else {
            if(Logging.WARNING) {
                Log.w(Logging.TAG, "loadData: array is null, rpc failed");
            }
        }
    }

    private void updateData(List<Result> newData) {
        //loop through all received Result items to add new results
        for(Result rpcResult : newData) {
            //check whether this Result is new
            int index = data.detectIndex(item -> item.id.equals(rpcResult.getName()));
            if(index == -1) { // result is new, add
                if(Logging.DEBUG) {
                    Log.d(Logging.TAG, "new result found, id: " + rpcResult.getName());
                }
                data.add(new TaskData(rpcResult));
            }
            else { // result was present before, update its data
                data.get(index).updateResultData(rpcResult);
            }
        }

        //loop through the list adapter to find removed (ready/aborted) Results
        data.removeIf(listItem -> ECLists.immutable.ofAll(newData)
                                                   .noneSatisfy(rpcResult -> listItem.id.equals(rpcResult.getName())));
    }

    public class TaskData {
        public Result result;
        public boolean expanded;
        public String id;
        public int nextState = -1;
        public int loopCounter = 0;
        public int transitionTimeout; // amount of refresh, until transition times out

        public TaskData(Result result) {
            this.result = result;
            this.expanded = false;
            this.id = result.getName();
            this.transitionTimeout =
                    getResources().getInteger(R.integer.tasks_transistion_timeout_number_monitor_loops);
        }

        public void updateResultData(Result data) {
            this.result = data;
            int currentState = determineState();
            if(nextState == -1) {
                return;
            }
            if(currentState == nextState) {
                if(Logging.DEBUG) {
                    Log.d(Logging.TAG, "nextState met! " + nextState);
                }
                nextState = -1;
                loopCounter = 0;
            }
            else {
                if(loopCounter < transitionTimeout) {
                    if(Logging.DEBUG) {
                        Log.d(Logging.TAG,
                              "nextState not met yet! " + nextState + " vs " + currentState + " loopCounter: " +
                              loopCounter);
                    }
                    loopCounter++;
                }
                else {
                    if(Logging.DEBUG) {
                        Log.d(Logging.TAG,
                              "transition timed out! " + nextState + " vs " + currentState + " loopCounter: " +
                              loopCounter);
                    }
                    nextState = -1;
                    loopCounter = 0;
                }
            }
        }

        public final OnClickListener iconClickListener = view -> {
            try {
                final Integer operation = (Integer) view.getTag();
                switch(operation) {
                    case RpcClient.RESULT_SUSPEND:
                        nextState = BOINCDefs.RESULT_SUSPENDED_VIA_GUI;
                        new ResultOperationAsync().execute(result.getProjectURL(), result.getName(),
                                                           operation.toString());
                        break;
                    case RpcClient.RESULT_RESUME:
                        nextState = BOINCDefs.PROCESS_EXECUTING;
                        new ResultOperationAsync().execute(result.getProjectURL(), result.getName(),
                                                           operation.toString());
                        break;
                    case RpcClient.RESULT_ABORT:
                        final Dialog dialog = new Dialog(getActivity());
                        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                        dialog.setContentView(R.layout.dialog_confirm);
                        Button confirm = dialog.findViewById(R.id.confirm);
                        TextView tvTitle = dialog.findViewById(R.id.title);
                        TextView tvMessage = dialog.findViewById(R.id.message);

                        tvTitle.setText(R.string.confirm_abort_task_title);
                        tvMessage.setText(getString(R.string.confirm_abort_task_message, result.getName()));
                        confirm.setText(R.string.confirm_abort_task_confirm);
                        confirm.setOnClickListener(view1 -> {
                            nextState = BOINCDefs.RESULT_ABORTED;
                            new ResultOperationAsync().execute(result.getProjectURL(), result.getName(),
                                                               operation.toString());
                            dialog.dismiss();
                        });
                        Button cancel = dialog.findViewById(R.id.cancel);
                        cancel.setOnClickListener(view1 -> dialog.dismiss());
                        dialog.show();
                        break;
                    default:
                        if(Logging.WARNING) {
                            Log.w(Logging.TAG, "could not map operation tag");
                        }
                }
                listAdapter.notifyDataSetChanged(); //force list adapter to refresh
            }
            catch(Exception e) {
                if(Logging.WARNING) {
                    Log.w(Logging.TAG, "failed parsing view tag");
                }
            }
        };

        public int determineState() {
            if(result.isSuspendedViaGUI()) {
                return BOINCDefs.RESULT_SUSPENDED_VIA_GUI;
            }
            if(result.isProjectSuspendedViaGUI()) {
                return BOINCDefs.RESULT_PROJECT_SUSPENDED;
            }
            if(result.isReadyToReport() && result.getState() != BOINCDefs.RESULT_ABORTED &&
               result.getState() != BOINCDefs.RESULT_COMPUTE_ERROR) {
                return BOINCDefs.RESULT_READY_TO_REPORT;
            }
            if(result.isActiveTask()) {
                return result.getActiveTaskState();
            }
            else {
                return result.getState();
            }
        }

        public boolean isTaskActive() {
            return result.isActiveTask();
        }
    }

    public final OnItemClickListener itemClickListener = (adapterView, view, position, arg3) -> {
        TaskData task = listAdapter.getItem(position);
        task.expanded = !task.expanded;
        listAdapter.notifyDataSetChanged();
    };

    private final class ResultOperationAsync extends AsyncTask<String, Void, Boolean> {

        @Override
        protected Boolean doInBackground(String... params) {
            try {
                String url = params[0];
                String name = params[1];
                int operation = Integer.parseInt(params[2]);
                if(Logging.DEBUG) {
                    Log.d(Logging.TAG, "url: " + url + " Name: " + name + " operation: " + operation);
                }

                return BOINCActivity.monitor.resultOp(operation, url, name);
            }
            catch(Exception e) {
                if(Logging.WARNING) {
                    Log.w(Logging.TAG, "SuspendResultAsync error in do in background", e);
                }
            }
            return false;
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if(Boolean.TRUE.equals(success)) {
                try {
                    BOINCActivity.monitor.forceRefresh();
                }
                catch(RemoteException e) {
                    if(Logging.ERROR) {
                        Log.e(Logging.TAG, "TasksFragment.ResultOperationAsync.onPostExecute() error: ", e);
                    }
                }
            }
            else if(Logging.WARNING) {
                Log.w(Logging.TAG, "SuspendResultAsync failed.");
            }
        }
    }
}
