/*
 * This file is part of BOINC.
 * http://boinc.berkeley.edu
 * Copyright (C) 2016 University of California
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
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.impl.list.mutable.FastList;

import java.text.NumberFormat;
import java.util.Arrays;
import java.util.List;

import edu.berkeley.boinc.adapter.PrefsListAdapter;
import edu.berkeley.boinc.adapter.PrefsListItemWrapper;
import edu.berkeley.boinc.adapter.PrefsListItemWrapperBool;
import edu.berkeley.boinc.adapter.PrefsListItemWrapperNumber;
import edu.berkeley.boinc.adapter.PrefsListItemWrapperText;
import edu.berkeley.boinc.adapter.PrefsSelectionDialogListAdapter;
import edu.berkeley.boinc.adapter.SelectionDialogOption;
import edu.berkeley.boinc.rpc.GlobalPreferences;
import edu.berkeley.boinc.rpc.HostInfo;
import edu.berkeley.boinc.utils.ECLists;
import edu.berkeley.boinc.utils.Logging;

public class PrefsFragment extends Fragment {
    private static final String VALUE_LOG = " value: ";

    private ListView lv;
    private PrefsListAdapter listAdapter;

    // Data for the PrefsListAdapter. This should be HashMap!
    private MutableList<PrefsListItemWrapper> data = new FastList<>();
    // Android specific preferences of the client, read on every onResume via RPC
    private GlobalPreferences clientPrefs = null;
    private HostInfo hostinfo = null;

    private boolean layoutSuccessful = false;

    private BroadcastReceiver mClientStatusChangeRec = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(Logging.VERBOSE) {
                Log.d(Logging.TAG, "PrefsFragment ClientStatusChange - onReceive()");
            }
            try {
                if(!layoutSuccessful) {
                    populateLayout();
                }
            }
            catch(RemoteException e) {
                if(Logging.ERROR) {
                    Log.e(Logging.TAG, "PrefsFragment.BroadcastReceiver: onReceive() error: ", e);
                }
            }
        }
    };
    private IntentFilter ifcsc = new IntentFilter("edu.berkeley.boinc.clientstatuschange");

    // fragment lifecycle: 2.
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if(Logging.VERBOSE) {
            Log.d(Logging.TAG, "ProjectsFragment onCreateView");
        }

        // Inflate the layout for this fragment
        View layout = inflater.inflate(R.layout.prefs_layout, container, false);
        lv = layout.findViewById(R.id.listview);
        listAdapter = new PrefsListAdapter(getActivity(), this, R.id.listview, data);
        lv.setAdapter(listAdapter);
        return layout;
    }

    // fragment lifecycle: 1.
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    // fragment lifecycle: 3.
    @Override
    public void onResume() {
        try {
            populateLayout();
        }
        catch(RemoteException e) {
            if(Logging.ERROR) {
                Log.e(Logging.TAG, "PrefsFragment.onResume error: ", e);
            }
        }
        getActivity().registerReceiver(mClientStatusChangeRec, ifcsc);
        super.onResume();
    }

    @Override
    public void onPause() {
        getActivity().unregisterReceiver(mClientStatusChangeRec);
        super.onPause();
    }

    private Boolean getPrefs() {
        // Try to get current client status from monitor
        try {
            clientPrefs = BOINCActivity.monitor.getPrefs(); // Read preferences from client via rpc
        }
        catch(Exception e) {
            if(Logging.WARNING) {
                Log.w(Logging.TAG, "PrefsActivity: Could not load data, clientStatus not initialized.");
            }
            e.printStackTrace();
            return false;
        }

        if(clientPrefs == null) {
            if(Logging.DEBUG) {
                Log.d(Logging.TAG, "readPrefs: null, return false");
            }
            return false;
        }

        return true;
    }

    private Boolean getHostInfo() {
        // Try to get current client status from monitor
        try {
            hostinfo = BOINCActivity.monitor.getHostInfo(); // Get the hostinfo from client via rpc
        }
        catch(Exception e) {
            if(Logging.WARNING) {
                Log.w(Logging.TAG, "PrefsActivity: Could not load data, clientStatus not initialized.");
            }
            e.printStackTrace();
            return false;
        }

        if(hostinfo == null) {
            if(Logging.DEBUG) {
                Log.d(Logging.TAG, "getHostInfo: null, return false");
            }
            return false;
        }
        return true;
    }

    private void populateLayout() throws RemoteException {

        if(!getPrefs() || BOINCActivity.monitor == null || !getHostInfo()) {
            if(Logging.ERROR) {
                Log.e(Logging.TAG, "PrefsFragment.populateLayout returns, data is not present");
            }
            return;
        }

        data.clear();

        boolean advanced = BOINCActivity.monitor.getShowAdvanced();
        boolean stationaryDeviceMode = BOINCActivity.monitor.getStationaryDeviceMode();
        boolean stationaryDeviceSuspected = BOINCActivity.monitor.isStationaryDeviceSuspected();

        // The order is important, the GUI will be displayed in the same order as the data is added.
        // General
        data.add(new PrefsListItemWrapper(getActivity(), R.string.prefs_category_general, true));
        data.add(new PrefsListItemWrapperBool(getActivity(), R.string.prefs_autostart_header,
                                              BOINCActivity.monitor.getAutostart()));
        data.add(new PrefsListItemWrapperBool(getActivity(), R.string.prefs_show_notification_notices_header,
                                              BOINCActivity.monitor.getShowNotificationForNotices()));
        data.add(new PrefsListItemWrapperBool(getActivity(), R.string.prefs_show_advanced_header,
                                              BOINCActivity.monitor.getShowAdvanced()));
        if(!stationaryDeviceMode) {
            data.add(new PrefsListItemWrapperBool(getActivity(), R.string.prefs_suspend_when_screen_on,
                                                  BOINCActivity.monitor.getSuspendWhenScreenOn()));
        }
        data.add(new PrefsListItemWrapperText(getActivity(), R.string.prefs_general_device_name_header,
                                              BOINCActivity.monitor.getHostInfo().getDomainName()));

        // Network
        data.add(new PrefsListItemWrapper(getActivity(), R.string.prefs_category_network,
                                          true));
        data.add(new PrefsListItemWrapperBool(getActivity(), R.string.prefs_network_wifi_only_header,
                                              clientPrefs.getNetworkWiFiOnly()));
        if(advanced) {
            data.add(new PrefsListItemWrapperNumber(getActivity(),
                                                    R.string.prefs_network_daily_xfer_limit_mb_header,
                                                    clientPrefs.getDailyTransferLimitMB(),
                                                    PrefsListItemWrapper.DialogButtonType.NUMBER));
        }

        // Power
        data.add(new PrefsListItemWrapper(getActivity(), R.string.prefs_category_power, true));
        if(stationaryDeviceSuspected) { // API indicates that there is no battery, offer opt-in preference for stationary device mode
            data.add(new PrefsListItemWrapperBool(getActivity(),
                                                  R.string.prefs_stationary_device_mode_header,
                                                  BOINCActivity.monitor.getStationaryDeviceMode()));
        }
        if(!stationaryDeviceMode) { // Client would compute regardless of battery preferences, so only show if that is not the case
            data.add(new PrefsListItemWrapper(getActivity(), R.string.prefs_power_source_header));
            data.add(new PrefsListItemWrapperNumber(getActivity(), R.string.battery_charge_min_pct_header,
                                                    clientPrefs.getBatteryChargeMinPct(),
                                                    PrefsListItemWrapper.DialogButtonType.SLIDER));
            if(advanced) {
                data.add(new PrefsListItemWrapperNumber(getActivity(), R.string.battery_temperature_max_header,
                                                        clientPrefs.getBatteryMaxTemperature(),
                                                        PrefsListItemWrapper.DialogButtonType.NUMBER));
            }
        }

        if(advanced) {
            // CPU
            data.add(new PrefsListItemWrapper(getActivity(), R.string.prefs_category_cpu, true));
            if(hostinfo.getNoOfCPUs() > 1) {
                data.add(new PrefsListItemWrapperNumber(getActivity(),
                                                        R.string.prefs_cpu_number_cpus_header,
                                                        pctCpuCoresToNumber(clientPrefs.getMaxNoOfCPUsPct()),
                                                        PrefsListItemWrapper.DialogButtonType.SLIDER));
            }
            data.add(new PrefsListItemWrapperNumber(getActivity(), R.string.prefs_cpu_time_max_header,
                                                    clientPrefs.getCpuUsageLimit(),
                                                    PrefsListItemWrapper.DialogButtonType.SLIDER));
            data.add(new PrefsListItemWrapperNumber(getActivity(), R.string.prefs_cpu_other_load_suspension_header,
                                                    clientPrefs.getSuspendCpuUsage(),
                                                    PrefsListItemWrapper.DialogButtonType.SLIDER));

            // Storage
            data.add(new PrefsListItemWrapper(getActivity(), R.string.prefs_category_storage, true));
            data.add(new PrefsListItemWrapperNumber(getActivity(), R.string.prefs_disk_max_pct_header,
                                                    clientPrefs.getDiskMaxUsedPct(),
                                                    PrefsListItemWrapper.DialogButtonType.SLIDER));
            data.add(new PrefsListItemWrapperNumber(getActivity(), R.string.prefs_disk_min_free_gb_header,
                                                    clientPrefs.getDiskMinFreeGB(),
                                                    PrefsListItemWrapper.DialogButtonType.NUMBER));
            data.add(new PrefsListItemWrapperNumber(getActivity(), R.string.prefs_disk_access_interval_header,
                                                    clientPrefs.getDiskInterval(),
                                                    PrefsListItemWrapper.DialogButtonType.NUMBER));

            // Memory
            data.add(new PrefsListItemWrapper(getActivity(), R.string.prefs_category_memory,
                                              true));
            data.add(new PrefsListItemWrapperNumber(getActivity(),
                                                    R.string.prefs_memory_max_idle_header,
                                                    clientPrefs.getRamMaxUsedIdleFrac(),
                                                    PrefsListItemWrapper.DialogButtonType.SLIDER));

            // Other
            data.add(new PrefsListItemWrapper(getActivity(), R.string.prefs_category_other, true));
            data.add(new PrefsListItemWrapperNumber(getActivity(),
                                                    R.string.prefs_other_store_at_least_x_days_of_work_header,
                                                    clientPrefs.getWorkBufMinDays(),
                                                    PrefsListItemWrapper.DialogButtonType.NUMBER));
            data.add(new PrefsListItemWrapperNumber(getActivity(),
                                                    R.string.prefs_other_store_up_to_an_additional_x_days_of_work_header,
                                                    clientPrefs.getWorkBufAdditionalDays(),
                                                    PrefsListItemWrapper.DialogButtonType.NUMBER));

            // Debug
            data.add(new PrefsListItemWrapper(getActivity(), R.string.prefs_category_debug,
                                              true));
            data.add(new PrefsListItemWrapper(getActivity(), R.string.prefs_client_log_flags_header));
            data.add(new PrefsListItemWrapperNumber(getActivity(), R.string.prefs_gui_log_level_header,
                                                    (double) BOINCActivity.monitor.getLogLevel(),
                                                    PrefsListItemWrapper.DialogButtonType.SLIDER));
        }

        updateLayout();
        layoutSuccessful = true;
    }

    private void updateLayout() {
        listAdapter.notifyDataSetChanged();
    }

    // Updates list item of boolean preference
    // Requires updateLayout to be called afterwards
    private void updateBoolPreference(int ID, Boolean newValue) {
        if(Logging.DEBUG) {
            Log.d(Logging.TAG, "updateBoolPreference for ID: " + ID + VALUE_LOG + newValue);
        }
        PrefsListItemWrapper itemWrapper = data.detect(item -> item.getId() == ID);
        if(itemWrapper != null) {
            ((PrefsListItemWrapperBool) itemWrapper).setStatus(newValue);
        }
    }

    // Updates list item of number preference
    // Requires updateLayout to be called afterwards
    private void updateNumberPreference(int ID, Double newValue) {
        if(Logging.DEBUG) {
            Log.d(Logging.TAG, "updateNumberPreference for ID: " + ID + VALUE_LOG + newValue);
        }
        PrefsListItemWrapper itemWrapper = data.detect(item -> item.getId() == ID);
        if(itemWrapper != null) {
            ((PrefsListItemWrapperNumber) itemWrapper).setStatus(newValue);
        }
    }

    // Updates list item of text preference
    private void updateTextPreference(int ID, String newValue) {
        if(Logging.DEBUG) {
            Log.d(Logging.TAG, "updateTextPreference for ID: " + ID + VALUE_LOG + newValue);
        }
        PrefsListItemWrapper itemWrapper = data.detect(item -> item.getId() == ID);
        if(itemWrapper != null) {
            ((PrefsListItemWrapperText) itemWrapper).setStatus(newValue);
        }
    }

    private void setupSliderDialog(PrefsListItemWrapper item, final Dialog dialog) {
        final PrefsListItemWrapperNumber prefsListItemWrapperNumber = (PrefsListItemWrapperNumber) item;
        dialog.setContentView(R.layout.prefs_layout_dialog_pct);
        SeekBar slider = dialog.findViewById(R.id.seekbar);

        if(prefsListItemWrapperNumber.getId() == R.string.battery_charge_min_pct_header ||
           prefsListItemWrapperNumber.getId() == R.string.prefs_disk_max_pct_header ||
           prefsListItemWrapperNumber.getId() == R.string.prefs_cpu_time_max_header ||
           prefsListItemWrapperNumber.getId() == R.string.prefs_cpu_other_load_suspension_header ||
           prefsListItemWrapperNumber.getId() == R.string.prefs_memory_max_idle_header) {
            double seekBarDefault = prefsListItemWrapperNumber.getStatus() / 10;
            slider.setProgress((int) seekBarDefault);
            final SeekBar.OnSeekBarChangeListener onSeekBarChangeListener = new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(final SeekBar seekBar, final int progress, final boolean fromUser) {
                    final String progressString = NumberFormat.getPercentInstance().format(progress / 10.0);
                    TextView sliderProgress = dialog.findViewById(R.id.seekbar_status);
                    sliderProgress.setText(progressString);
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            };
            slider.setOnSeekBarChangeListener(onSeekBarChangeListener);
            onSeekBarChangeListener.onProgressChanged(slider, (int) seekBarDefault, false);
        }
        else if(prefsListItemWrapperNumber.getId() == R.string.prefs_cpu_number_cpus_header) {
            if(!getHostInfo()) {
                if(Logging.WARNING) {
                    Log.w(Logging.TAG, "onItemClick missing hostInfo");
                }
                return;
            }
            slider.setMax(hostinfo.getNoOfCPUs() <= 1 ? 0 : hostinfo.getNoOfCPUs() - 1);
            final int statusValue = (int) prefsListItemWrapperNumber.getStatus();
            slider.setProgress(statusValue <= 0 ? 0 : Math.min(statusValue - 1, slider.getMax()));
            Log.d(Logging.TAG, String.format("statusValue == %,d", statusValue));
            final SeekBar.OnSeekBarChangeListener onSeekBarChangeListener = new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(final SeekBar seekBar, final int progress, final boolean fromUser) {
                    final String progressString = NumberFormat.getIntegerInstance().format(
                            progress <= 0 ? 1 : progress + 1); // do not allow 0 cpus
                    TextView sliderProgress = dialog.findViewById(R.id.seekbar_status);
                    sliderProgress.setText(progressString);
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            };
            slider.setOnSeekBarChangeListener(onSeekBarChangeListener);
            onSeekBarChangeListener.onProgressChanged(slider, statusValue - 1, false);
        }
        else if(prefsListItemWrapperNumber.getId() == R.string.prefs_gui_log_level_header) {
            slider.setMax(5);
            slider.setProgress((int) prefsListItemWrapperNumber.getStatus());
            final SeekBar.OnSeekBarChangeListener onSeekBarChangeListener = new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(final SeekBar seekBar, final int progress, final boolean fromUser) {
                    String progressString = NumberFormat.getIntegerInstance().format(progress);
                    TextView sliderProgress = dialog.findViewById(R.id.seekbar_status);
                    sliderProgress.setText(progressString);
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            };
            slider.setOnSeekBarChangeListener(onSeekBarChangeListener);
            onSeekBarChangeListener.onProgressChanged(slider, (int) prefsListItemWrapperNumber.getStatus(),
                                                      false);
        }

        setupDialogButtons(item, dialog);
    }

    private void setupSelectionListDialog(final PrefsListItemWrapper item, final Dialog dialog)
            throws RemoteException {
        dialog.setContentView(R.layout.prefs_layout_dialog_selection);

        if(item.getId() == R.string.prefs_client_log_flags_header) {
            String[] optionsStr = getResources().getStringArray(R.array.prefs_client_log_flags);
            final MutableList<SelectionDialogOption> options =
                    ECLists.mutable.of(optionsStr).collect(SelectionDialogOption::new);
            ListView lv = dialog.findViewById(R.id.selection);
            new PrefsSelectionDialogListAdapter(getActivity(), lv, R.id.selection, options);

            // Setup confirm button action
            Button confirm = dialog.findViewById(R.id.confirm);
            confirm.setOnClickListener(view -> {
                List<String> selectedOptions = options.select(SelectionDialogOption::isSelected)
                                                      .collect(SelectionDialogOption::getName);
                if(Logging.DEBUG) {
                    Log.d(Logging.TAG, selectedOptions.size() + " log flags selected");
                }
                new SetCcConfigAsync().execute(formatOptionsToCcConfig(selectedOptions));
                dialog.dismiss();
            });
        }
        else if(item.getId() == R.string.prefs_power_source_header) {
            final List<SelectionDialogOption> options = Arrays.asList(
                    new SelectionDialogOption(this, R.string.prefs_power_source_ac,
                                              BOINCActivity.monitor.getPowerSourceAc()),
                    new SelectionDialogOption(this, R.string.prefs_power_source_usb,
                                              BOINCActivity.monitor.getPowerSourceUsb()),
                    new SelectionDialogOption(this, R.string.prefs_power_source_wireless,
                                              BOINCActivity.monitor.getPowerSourceWireless()),
                    new SelectionDialogOption(this, R.string.prefs_power_source_battery,
                                              clientPrefs.getRunOnBatteryPower(),
                                              true)
            );
            ListView lv = dialog.findViewById(R.id.selection);
            new PrefsSelectionDialogListAdapter(getActivity(), lv, R.id.selection, options);

            // Setup confirm button action
            Button confirm = dialog.findViewById(R.id.confirm);
            confirm.setOnClickListener(view -> {
                try {
                    for(SelectionDialogOption option : options) {
                        switch(option.getId()) {
                            case R.string.prefs_power_source_ac:
                                BOINCActivity.monitor.setPowerSourceAc(option.isSelected());
                                break;
                            case R.string.prefs_power_source_usb:
                                BOINCActivity.monitor.setPowerSourceUsb(option.isSelected());
                                break;
                            case R.string.prefs_power_source_wireless:
                                BOINCActivity.monitor.setPowerSourceWireless(option.isSelected());
                                break;
                            case R.string.prefs_power_source_battery:
                                clientPrefs.setRunOnBatteryPower(option.isSelected());
                                new WriteClientPrefsAsync().execute(clientPrefs); //async task triggers layout update
                                break;
                        }
                    }
                    dialog.dismiss();
                }
                catch(RemoteException e) {
                    if(Logging.ERROR) {
                        Log.e(Logging.TAG, "PrefsFragment.setupSelectionListDialog.setOnClickListener: OnClick() error: ", e);
                    }
                }
            });
        }

        // Generic cancel button
        Button cancel = dialog.findViewById(R.id.cancel);
        cancel.setOnClickListener(view -> dialog.dismiss());
    }

    private void setupDialogButtons(final PrefsListItemWrapper item, final Dialog dialog) {
        // Confirm
        Button confirm = dialog.findViewById(R.id.confirm);
        confirm.setOnClickListener(view -> {
            // Sliders
            if(item.getDialogButtonType() == PrefsListItemWrapper.DialogButtonType.SLIDER) {
                SeekBar slider = dialog.findViewById(R.id.seekbar);
                int sliderProgress = slider.getProgress();
                double value;

                // Calculate value based on Slider Progress
                if(item.getId() == R.string.prefs_cpu_number_cpus_header) {
                    value = numberCpuCoresToPct(sliderProgress <= 0 ? 1 : sliderProgress + 1);
                    writeClientNumberPreference(item.getId(), value);
                }
                else if(item.getId() == R.string.prefs_gui_log_level_header) {
                    try {
                        // Monitor and UI in two different processes. set static variable in both
                        Logging.setLogLevel(sliderProgress);
                        BOINCActivity.monitor.setLogLevel(sliderProgress);
                    }
                    catch(RemoteException e) {
                        if(Logging.ERROR) {
                            Log.e(Logging.TAG,
                                  "PrefsFragment.setupSelectionListDialog.setOnClickListener: OnClick() error: ",
                                  e);
                        }
                    }
                    updateNumberPreference(item.getId(), (double) sliderProgress);
                    updateLayout();
                }
                else {
                    value = sliderProgress * 10;
                    writeClientNumberPreference(item.getId(), value);
                }
            }
            // Numbers
            else if(item.getDialogButtonType() == PrefsListItemWrapper.DialogButtonType.NUMBER) {
                EditText edit = dialog.findViewById(R.id.Input);
                String input = edit.getText().toString();
                Double valueTmp = parseInputValueToDouble(input);
                if(valueTmp == null) {
                    return;
                }
                double value = valueTmp;
                writeClientNumberPreference(item.getId(), value);
            }
            // Texts
            else if(item.getDialogButtonType() == PrefsListItemWrapper.DialogButtonType.TEXT) {
                EditText input = dialog.findViewById(R.id.Input);

                if(item.getId() == R.string.prefs_general_device_name_header) {
                    try {
                        if(!BOINCActivity.monitor.setDomainName(input.getText().toString())) {
                            if(Logging.DEBUG) {
                                Log.d(Logging.TAG, "PrefsFragment.setupDialogButtons.onClick.setDomainName(): false");
                            }
                        }
                    }
                    catch(Exception e) {
                        if(Logging.ERROR) {
                            Log.e(Logging.TAG, "PrefsFragment.setupDialogButtons.onClick(): error: " + e);
                        }
                    }
                }

                updateTextPreference(item.getId(), input.getText().toString());
            }
            dialog.dismiss();
        });

        // Cancel
        Button cancel = dialog.findViewById(R.id.cancel);
        cancel.setOnClickListener(view -> dialog.dismiss());
    }

    private void writeClientNumberPreference(int id, double value) {
        // Update preferences
        switch(id) {
            case R.string.prefs_disk_max_pct_header:
                clientPrefs.setDiskMaxUsedPct(value);
                break;
            case R.string.prefs_disk_min_free_gb_header:
                clientPrefs.setDiskMinFreeGB(value);
                break;
            case R.string.prefs_disk_access_interval_header:
                clientPrefs.setDiskInterval(value);
                break;
            case R.string.prefs_network_daily_xfer_limit_mb_header:
                clientPrefs.setDailyTransferLimitMB(value);
                // also need to set the period!
                clientPrefs.setDailyTransferPeriodDays(1);
                break;
            case R.string.battery_charge_min_pct_header:
                clientPrefs.setBatteryChargeMinPct(value);
                break;
            case R.string.battery_temperature_max_header:
                clientPrefs.setBatteryMaxTemperature(value);
                break;
            case R.string.prefs_cpu_number_cpus_header:
                clientPrefs.setMaxNoOfCPUsPct(value);
                value = pctCpuCoresToNumber(value); // Convert value back to number for layout update
                break;
            case R.string.prefs_cpu_time_max_header:
                clientPrefs.setCpuUsageLimit(value);
                break;
            case R.string.prefs_cpu_other_load_suspension_header:
                clientPrefs.setSuspendCpuUsage(value);
                break;
            case R.string.prefs_memory_max_idle_header:
                clientPrefs.setRamMaxUsedIdleFrac(value);
                break;
            case R.string.prefs_other_store_at_least_x_days_of_work_header:
                clientPrefs.setWorkBufMinDays(value);
                break;
            case R.string.prefs_other_store_up_to_an_additional_x_days_of_work_header:
                clientPrefs.setWorkBufAdditionalDays(value);
                break;
            default:
                if(Logging.DEBUG) {
                    Log.d(Logging.TAG, "onClick (dialog submit button), couldnt match ID");
                }
                Toast toast = Toast.makeText(getActivity(), "ooops! something went wrong...", Toast.LENGTH_SHORT);
                toast.show();
                return;
        }
        // Update list item
        updateNumberPreference(id, value);
        // Preferences adapted, write preferences to client
        new WriteClientPrefsAsync().execute(clientPrefs);
    }

    private double numberCpuCoresToPct(double ncpus) {
        double pct = (ncpus / (double) hostinfo.getNoOfCPUs()) * 100;
        if(Logging.DEBUG) {
            Log.d(Logging.TAG, "numberCpuCoresToPct: " + ncpus + hostinfo.getNoOfCPUs() + pct);
        }
        return pct;
    }

    private double pctCpuCoresToNumber(double pct) {
        return Math.max(1.0, (double) hostinfo.getNoOfCPUs() * (pct / 100.0));
    }

    public Double parseInputValueToDouble(String input) {
        // Parse value
        Double value;
        try {
            input = input.replaceAll(",", "."); //Replace e.g. European decimal seperator "," by "."
            value = Double.parseDouble(input);
            if(Logging.DEBUG) {
                Log.d(Logging.TAG, "parseInputValueToDouble: " + value);
            }
            return value;
        }
        catch(Exception e) {
            if(Logging.WARNING) {
                Log.w(Logging.TAG, e);
            }
            Toast toast = Toast.makeText(getActivity(), "wrong format!", Toast.LENGTH_SHORT);
            toast.show();
            return null;
        }
    }

    private String formatOptionsToCcConfig(List<String> options) {
        StringBuilder builder = new StringBuilder();
        builder.append("<cc_config>\n <log_flags>\n");
        for(String option : options) {
            builder.append("  <").append(option).append("/>\n");
        }
        builder.append(" </log_flags>\n <options>\n </options>\n</cc_config>");
        return builder.toString();
    }

    public class BoolOnClick implements OnClickListener {
        private Integer ID;
        private CheckBox cb;

        public BoolOnClick(Integer ID, CheckBox cb) {
            this.ID = ID;
            this.cb = cb;
        }

        @Override
        public void onClick(View view) {
            if(Logging.DEBUG) {
                Log.d(Logging.TAG, "onCbClick");
            }
            boolean previousState = cb.isChecked();
            cb.setChecked(!previousState);
            boolean isSet = cb.isChecked();
            try {
                switch(ID) {
                    case R.string.prefs_autostart_header: //app pref
                        BOINCActivity.monitor.setAutostart(isSet);
                        updateBoolPreference(ID, isSet);
                        updateLayout();
                        break;
                    case R.string.prefs_show_notification_notices_header: //app pref
                        BOINCActivity.monitor.setShowNotificationForNotices(isSet);
                        updateBoolPreference(ID, isSet);
                        updateLayout();
                        break;
                    case R.string.prefs_show_advanced_header: //app pref
                        BOINCActivity.monitor.setShowAdvanced(isSet);
                        // reload complete layout to remove/add advanced elements
                        populateLayout();
                        break;
                    case R.string.prefs_suspend_when_screen_on: //app pref
                        BOINCActivity.monitor.setSuspendWhenScreenOn(isSet);
                        updateBoolPreference(ID, isSet);
                        updateLayout();
                        break;
                    case R.string.prefs_network_wifi_only_header: //client pref
                        clientPrefs.setNetworkWiFiOnly(isSet);
                        updateBoolPreference(ID, isSet);
                        new WriteClientPrefsAsync().execute(clientPrefs); //async task triggers layout update
                        break;
                    case R.string.prefs_stationary_device_mode_header: //app pref
                        BOINCActivity.monitor.setStationaryDeviceMode(isSet);
                        // reload complete layout to remove/add power preference elements
                        populateLayout();
                        break;
                }
            }
            catch(RemoteException e) {
                if(Logging.ERROR) {
                    Log.e(Logging.TAG, "PrefsFragment.BoolOnClick: onClick() error: ", e);
                }
            }
        }
    }

    public class ValueOnClick implements OnClickListener {

        private PrefsListItemWrapper item;

        public ValueOnClick(PrefsListItemWrapper wrapper) {
            this.item = wrapper;
        }

        @Override
        public void onClick(View view) {
            final Dialog dialog = new Dialog(getActivity());
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

            // setup dialog layout
            switch(item.getId()) {
                case R.string.prefs_general_device_name_header:
                    dialog.setContentView(R.layout.prefs_layout_dialog_text);
                    ((TextView) dialog.findViewById(R.id.pref)).setText(item.getId());
                    setupDialogButtons(item, dialog);
                    break;
                case R.string.prefs_network_daily_xfer_limit_mb_header:
                case R.string.prefs_other_store_up_to_an_additional_x_days_of_work_header:
                case R.string.prefs_other_store_at_least_x_days_of_work_header:
                case R.string.prefs_disk_access_interval_header:
                case R.string.prefs_disk_min_free_gb_header:
                case R.string.battery_temperature_max_header:
                    dialog.setContentView(R.layout.prefs_layout_dialog_number);
                    ((TextView) dialog.findViewById(R.id.pref)).setText(item.getId());
                    setupDialogButtons(item, dialog);
                    break;
                case R.string.prefs_power_source_header:
                case R.string.prefs_client_log_flags_header:
                    try {
                        setupSelectionListDialog(item, dialog);
                    }
                    catch(RemoteException e) {
                        if(Logging.ERROR) {
                            Log.e(Logging.TAG, "PrefsFragment.ValueOnClick.onClick() error: ", e);
                        }
                    }
                    break;
                case R.string.battery_charge_min_pct_header:
                    setupSliderDialog(item, dialog);
                    ((TextView) dialog.findViewById(R.id.pref)).setText(item.getId());
                    break;
                default:
                    if(Logging.ERROR) {
                        Log.d(Logging.TAG, "PrefsActivity onItemClick: could not map ID: " + item.getId());
                    }
                    return;
            }

            // show dialog
            dialog.show();
        }
    }

    private final class WriteClientPrefsAsync extends AsyncTask<GlobalPreferences, Void, Boolean> {
        @Override
        protected Boolean doInBackground(GlobalPreferences... params) {
            try {
                return BOINCActivity.monitor.setGlobalPreferences(params[0]);
            }
            catch(RemoteException e) {
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if(Logging.DEBUG) {
                Log.d(Logging.TAG, "WriteClientPrefsAsync returned: " + success);
            }
            updateLayout();
        }
    }

    private final class SetCcConfigAsync extends AsyncTask<String, Void, Boolean> {
        @Override
        protected Boolean doInBackground(String... params) {
            if(Logging.DEBUG) {
                Log.d(Logging.TAG, "SetCcConfigAsync with: " + params[0]);
            }
            try {
                return BOINCActivity.monitor.setCcConfig(params[0]);
            }
            catch(RemoteException e) {
                return false;
            }
        }
    }
}
