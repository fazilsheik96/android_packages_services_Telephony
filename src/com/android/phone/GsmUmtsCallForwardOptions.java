/**
* Changes from Qualcomm Innovation Center, Inc. are provided under the following license:
* Copyright (c) 2024 Qualcomm Innovation Center, Inc. All rights reserved.
* SPDX-License-Identifier: BSD-3-Clause-Clear
*/

package com.android.phone;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ContentProvider;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.os.Process;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.telephony.CarrierConfigManager;
import android.telephony.ims.feature.ImsFeature;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;
import com.android.ims.ImsException;
import com.android.ims.ImsManager;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.TelephonyIntents;

import com.android.internal.telephony.CallForwardInfo;
import com.android.internal.telephony.CommandsInterface;
import com.android.internal.telephony.Phone;

import org.codeaurora.ims.QtiCallConstants;

import java.util.ArrayList;

public class GsmUmtsCallForwardOptions extends TimeConsumingPreferenceActivity
    implements DialogInterface.OnClickListener, DialogInterface.OnCancelListener {
    private static final boolean DBG = (PhoneGlobals.DBG_LEVEL >= 2);
    private static final String LOG_TAG = "GsmUmtsCallForwardOptions";

    private static final String NUM_PROJECTION[] = {
        android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER
    };

    public static final String CALL_FORWARD_INTENT = "org.codeaurora.settings.CDMA_CALL_FORWARDING";

    private static final String BUTTON_CFU_KEY   = "button_cfu_key";
    private static final String BUTTON_CFB_KEY   = "button_cfb_key";
    private static final String BUTTON_CFNRY_KEY = "button_cfnry_key";
    private static final String BUTTON_CFNRC_KEY = "button_cfnrc_key";
    private static final String BUTTON_CFNL_KEY  = "button_cfnl_key";

    private static final String KEY_TOGGLE = "toggle";
    private static final String KEY_STATUS = "status";
    private static final String KEY_NUMBER = "number";
    private static final String KEY_ENABLE = "enable";
    private static final String KEY_START_HOUR = "start_hour";
    private static final String KEY_END_HOUR = "end_hour";
    private static final String KEY_START_MINUTE = "start_minute";
    private static final String KEY_END_MINUTE = "end_minute";
    private static final String KEY_IS_CFUT = "is_cfut";

    private CallForwardEditPreference mButtonCFU;
    private CallForwardEditPreference mButtonCFB;
    private CallForwardEditPreference mButtonCFNRy;
    private CallForwardEditPreference mButtonCFNRc;
    private CallForwardEditPreference mButtonCFNL;

    private boolean mSupportCFNL = true;

    private final ArrayList<CallForwardEditPreference> mPreferences =
            new ArrayList<CallForwardEditPreference> ();
    private int mInitIndex= 0;

    private boolean mFirstResume;
    private Bundle mIcicle;
    private Phone mPhone;
    private SubscriptionInfoHelper mSubscriptionInfoHelper;
    private boolean mReplaceInvalidCFNumbers;
    private int mServiceClass;
    private BroadcastReceiver mReceiver = null;
    private boolean mCheckData = false;
    private boolean mIsUtAllowedWhenWifiOn = false;
    AlertDialog.Builder builder = null;
    private CarrierConfigManager mCarrierConfig;
    private boolean mCallForwardByUssd;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        addPreferencesFromResource(R.xml.callforward_options);

        mSubscriptionInfoHelper = new SubscriptionInfoHelper(this, getIntent());
        mSubscriptionInfoHelper.setActionBarTitle(
                getActionBar(), getResources(), R.string.call_forwarding_settings_with_label);
        mPhone = mSubscriptionInfoHelper.getPhone();

        mCarrierConfig = (CarrierConfigManager)
                getSystemService(CARRIER_CONFIG_SERVICE);

        if (mCarrierConfig != null) {
            PersistableBundle pb = mCarrierConfig.getConfigForSubId(mPhone.getSubId());
            mCheckData = pb.getBoolean("check_mobile_data_for_cf");
            mIsUtAllowedWhenWifiOn = pb.getBoolean("allow_ut_when_wifi_on_bool");
            Log.d(LOG_TAG, "mCheckData = " + mCheckData + ", mIsUtAllowedWhenWifiOn = " +
                    mIsUtAllowedWhenWifiOn);
        }
        PersistableBundle b = null;
        boolean supportCFB = true;
        boolean supportCFNRc = true;
        boolean supportCFNRy = true;
        if (mSubscriptionInfoHelper.hasSubId()) {
            b = PhoneGlobals.getInstance().getCarrierConfigForSubId(
                    mSubscriptionInfoHelper.getSubId());
        } else {
            b = PhoneGlobals.getInstance().getCarrierConfig();
        }
        if (b != null) {
            mReplaceInvalidCFNumbers = b.getBoolean(
                    CarrierConfigManager.KEY_CALL_FORWARDING_MAP_NON_NUMBER_TO_VOICEMAIL_BOOL);
            mCallForwardByUssd = b.getBoolean(
                    CarrierConfigManager.KEY_USE_CALL_FORWARDING_USSD_BOOL);
            supportCFB = b.getBoolean(
                    CarrierConfigManager.KEY_CALL_FORWARDING_WHEN_BUSY_SUPPORTED_BOOL);
            supportCFNRc = b.getBoolean(
                    CarrierConfigManager.KEY_CALL_FORWARDING_WHEN_UNREACHABLE_SUPPORTED_BOOL);
            supportCFNRy = b.getBoolean(
                    CarrierConfigManager.KEY_CALL_FORWARDING_WHEN_UNANSWERED_SUPPORTED_BOOL);
            mSupportCFNL = b.getBoolean(
                    CarrierConfigManager.KEY_CALL_FORWARDING_WHEN_NOT_LOGGED_IN_SUPPORTED_BOOL);
        }
        // Disable mSupportCFNL if IMS UT is not registered or build version is older than S.
        if (!mPhone.isUtEnabled() ||
                SystemProperties.getInt("ro.board.api_level", 0) < Build.VERSION_CODES.S) {
            mSupportCFNL = false;
        }

        PreferenceScreen prefSet = getPreferenceScreen();
        mButtonCFU = (CallForwardEditPreference) prefSet.findPreference(BUTTON_CFU_KEY);
        mButtonCFB = (CallForwardEditPreference) prefSet.findPreference(BUTTON_CFB_KEY);
        mButtonCFNRy = (CallForwardEditPreference) prefSet.findPreference(BUTTON_CFNRY_KEY);
        mButtonCFNRc = (CallForwardEditPreference) prefSet.findPreference(BUTTON_CFNRC_KEY);
        mButtonCFNL  = (CallForwardEditPreference) prefSet.findPreference(BUTTON_CFNL_KEY);

        mButtonCFU.setParentActivity(this, mButtonCFU.reason);
        mButtonCFB.setParentActivity(this, mButtonCFB.reason);
        mButtonCFNRy.setParentActivity(this, mButtonCFNRy.reason);
        mButtonCFNRc.setParentActivity(this, mButtonCFNRc.reason);
        mButtonCFNL.setParentActivity(this, mButtonCFNL.reason);

        mPreferences.add(mButtonCFU);
        layoutCallForwardItem(supportCFB, mButtonCFB, prefSet);
        layoutCallForwardItem(supportCFNRy, mButtonCFNRy, prefSet);
        layoutCallForwardItem(supportCFNRc, mButtonCFNRc, prefSet);
        layoutCallForwardItem(mSupportCFNL, mButtonCFNL, prefSet);

        if (mCallForwardByUssd) {
            //the call forwarding ussd command's behavior is similar to the call forwarding when
            //unanswered,so only display the call forwarding when unanswered item.
            prefSet.removePreference(mButtonCFU);
            prefSet.removePreference(mButtonCFB);
            prefSet.removePreference(mButtonCFNRc);
            prefSet.removePreference(mButtonCFNL);
            mPreferences.remove(mButtonCFU);
            mPreferences.remove(mButtonCFB);
            mPreferences.remove(mButtonCFNRc);
            mPreferences.remove(mButtonCFNL);
            mButtonCFNRy.setDependency(null);
        }

        // we wait to do the initialization until onResume so that the
        // TimeConsumingPreferenceActivity dialog can display as it
        // relies on onResume / onPause to maintain its foreground state.

        /*Retrieve Call Forward ServiceClass*/
        Intent intent = getIntent();
        Log.d(LOG_TAG, "Intent is " + intent);
        mServiceClass = intent.getIntExtra(PhoneUtils.SERVICE_CLASS,
                CommandsInterface.SERVICE_CLASS_VOICE);
        Log.d(LOG_TAG, "serviceClass: " + mServiceClass);

        mFirstResume = true;
        mIcicle = icicle;

        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            // android.R.id.home will be triggered in onOptionsItemSelected()
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        if (mCheckData) {
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(TelephonyIntents.ACTION_ANY_DATA_CONNECTION_STATE_CHANGED);
            intentFilter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
            mReceiver = new PhoneAppBroadcastReceiver();
            registerReceiver(mReceiver, intentFilter);
        }
    }

    private void layoutCallForwardItem(boolean support, CallForwardEditPreference preference,
            PreferenceScreen prefSet) {
        if (support) {
            mPreferences.add(preference);
        } else {
            preference.deInit();
            prefSet.removePreference(preference);
        }
    }

    /**
     * Receiver for intent broadcasts the Phone app cares about.
     */
    private class PhoneAppBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(TelephonyIntents.ACTION_ANY_DATA_CONNECTION_STATE_CHANGED)) {
                String state = intent.getStringExtra(PhoneConstants.STATE_KEY);
                final String apnType = intent.getStringExtra(PhoneConstants.DATA_APN_TYPE_KEY);
                Log.d(LOG_TAG, "apntype is: " + apnType + " state is: " + state);
                if (PhoneConstants.DataState.DISCONNECTED.name().equals(state) &&
                            PhoneConstants.APN_TYPE_DEFAULT.equals(apnType)) {
                    Log.d(LOG_TAG, "default data is disconnected.");
                    checkDataStatus();
                }
            } else if (action.equals(Intent.ACTION_AIRPLANE_MODE_CHANGED)) {
                if (mPhone != null) {
                    for (CallForwardEditPreference pref : mPreferences) {
                        if (pref != null) {
                            pref.setEnabled(PhoneUtils.isSuppServiceAllowedInAirplaneMode(mPhone));
                        }
                    }
                }
            }
        }
    }

    public void checkDataStatus() {
        if (mPhone == null) {
            return;
        }
        int sub = mPhone.getSubId();
        // Find out if the sim card is ready.
        boolean isSimReady = TelephonyManager.from(this)
                .getSimState(SubscriptionManager.getSlotIndex(sub))
                == TelephonyManager.SIM_STATE_READY;
        if (!isSimReady) {
            Log.d(LOG_TAG, "SIM is not ready!");
            String title = (String)this.getResources().getText(R.string.sim_is_not_ready);
            String message = (String)this.getResources()
                    .getText(R.string.sim_is_not_ready);
            showAlertDialog(title, message);
            return;
        }
        if (mPhone.isUtEnabled() && mCheckData) {
            // check whether the current data network is roaming and roaming is enabled
            boolean isDataRoaming = mPhone.getServiceState().getDataRoaming();
            boolean isDataRoamingEnabled = mPhone.getDataRoamingEnabled();
            boolean promptForDataRoaming = isDataRoaming && !isDataRoamingEnabled;
            Log.d(LOG_TAG, "sub = " + sub + ", isDataRoaming = " + isDataRoaming +
                    ", isDataRoamingEnabled = " + isDataRoamingEnabled);
            if (promptForDataRoaming) {
                Log.d(LOG_TAG, "data roaming is disabled");
                String title = (String)this.getResources()
                        .getText(R.string.no_mobile_data_roaming);
                String message = (String)this.getResources()
                        .getText(R.string.cf_setting_mobile_data_roaming_alert);
                showAlertDialog(title, message);
                return;
            }
            // check if mobile data on current sub is enabled by user or airplane mode
            boolean isDataEnabled = TelephonyManager.from(this).createForSubscriptionId(sub)
                    .isDataEnabled();
            boolean isAirplaneMode = Settings.Global.getInt(
                    mPhone.getContext().getContentResolver(), Settings.Global.AIRPLANE_MODE_ON,
                    PhoneGlobals.AIRPLANE_OFF) == PhoneGlobals.AIRPLANE_ON;
            if (!isDataEnabled || isAirplaneMode) {
                Log.d(LOG_TAG, "Mobile data is not available");
                String title = (String)this.getResources().getText(R.string.no_mobile_data);
                String message = (String)this.getResources()
                        .getText(R.string.cf_setting_mobile_data_off_alert);
                showAlertDialog(title, message);
                return;
            }
            if (!mIsUtAllowedWhenWifiOn) {
                // check network capabilities
                NetworkCapabilities caps = getNetworkCapabilities();
                if (caps == null) {
                    Log.d(LOG_TAG, "Can not get network capabilities!");
                    String title = (String)this.getResources()
                            .getText(R.string.no_network_available);
                    String message = (String)this.getResources()
                            .getText(R.string.cf_setting_network_alert);
                    showAlertDialog(title, message);
                    return;
                }
                Log.d(LOG_TAG, "network capabilities : " + caps);
                // check if Wi-Fi is on
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    Log.d(LOG_TAG, "Wi-Fi is on");
                    String title = (String)this.getResources().getText(R.string.wifi_on);
                    String message = (String)this.getResources()
                            .getText(R.string.cf_setting_wifi_on_alert);
                    showAlertDialog(title, message);
                    return;
                }
            }
            // check if the current sub is the default sub
            if (sub != SubscriptionManager.getDefaultDataSubscriptionId()) {
                Log.d(LOG_TAG, "Show data in use indication if data sub is not on current sub");
                showDataInuseToast();
            }
        }
        initCallforwarding();
    }

    @Override
    public void onClick(DialogInterface dialog, int id) {
        if (id == DialogInterface.BUTTON_POSITIVE) {
            Intent newIntent = new Intent("android.settings.SETTINGS");
            newIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(newIntent);
        }
        finish();
        return;
    }

    private NetworkCapabilities getNetworkCapabilities() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(
                Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return null;
        }
        Network activeNetwork = cm.getActiveNetwork();
        return cm.getNetworkCapabilities(activeNetwork);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mCheckData) {
            checkDataStatus();
        } else {
            initCallforwarding();
        }
    }

    private void initCallforwarding () {
        if (mFirstResume) {
            if (mIcicle == null) {
                Log.d(LOG_TAG, "start to init ");
                CallForwardEditPreference pref = mPreferences.get(mInitIndex);
                pref.setExpectMore(canExpectMoreCallFwdReq());
                pref.init(this, mPhone, mReplaceInvalidCFNumbers, mServiceClass, mCallForwardByUssd);
                pref.startCallForwardOptionsQuery();

            } else {
                mInitIndex = mPreferences.size();

                for (CallForwardEditPreference pref : mPreferences) {
                    Bundle bundle = mIcicle.getParcelable(pref.getKey());
                    pref.setToggled(bundle.getBoolean(KEY_TOGGLE));
                    pref.setEnabled(bundle.getBoolean(KEY_ENABLE));
                    pref.setExpectMore(canExpectMoreCallFwdReq());
                    if (bundle.getBoolean(KEY_IS_CFUT)) {
                        pref.init(this, mPhone, mReplaceInvalidCFNumbers,
                                mServiceClass, mCallForwardByUssd);
                        pref.restoreCallCallForwardTimerInfo(
                                bundle.getInt(KEY_START_HOUR),
                                bundle.getInt(KEY_START_MINUTE),
                                bundle.getInt(KEY_END_HOUR),
                                bundle.getInt(KEY_END_MINUTE),
                                bundle.getInt(KEY_STATUS),
                                bundle.getString(KEY_NUMBER),
                                bundle.getBoolean(KEY_IS_CFUT));
                    } else {
                        CallForwardInfo cf = new CallForwardInfo();
                        cf.number = bundle.getString(KEY_NUMBER);
                        cf.status = bundle.getInt(KEY_STATUS);
                        pref.init(this, mPhone, mReplaceInvalidCFNumbers,
                                mServiceClass, mCallForwardByUssd);
                        pref.restoreCallForwardInfo(cf);
                    }
                }
            }
            mFirstResume = false;
            mIcicle = null;
        }
    }

    private void showDataInuseToast() {
        String message = (String)this.getResources()
                .getText(R.string.mobile_data_alert);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mCheckData && mReceiver != null) {
            unregisterReceiver(mReceiver);
        }
        for (CallForwardEditPreference pref : mPreferences) {
            pref.deInit();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        for (CallForwardEditPreference pref : mPreferences) {
            Bundle bundle = new Bundle();
            bundle.putBoolean(KEY_TOGGLE, pref.isToggled());
            bundle.putBoolean(KEY_ENABLE, pref.isEnabled());
            if (pref.isCfutEnabled() &&
                    pref.getPrefId() == CommandsInterface.CF_REASON_UNCONDITIONAL) {
                bundle.putString(KEY_NUMBER, pref.getCfutNumber());
                bundle.putInt(KEY_START_HOUR, pref.getStartHour());
                bundle.putInt(KEY_END_HOUR, pref.getEndHour());
                bundle.putInt(KEY_START_MINUTE, pref.getStartMinute());
                bundle.putInt(KEY_END_MINUTE, pref.getEndMinute());
                bundle.putInt(KEY_STATUS, pref.getCfutStatus());
                bundle.putBoolean(KEY_IS_CFUT, pref.isCfutEnabled());
            } else {
                if (pref.callForwardInfo != null) {
                    bundle.putString(KEY_NUMBER, pref.callForwardInfo.number);
                    bundle.putInt(KEY_STATUS, pref.callForwardInfo.status);
                }
            }
            outState.putParcelable(pref.getKey(), bundle);
        }
    }

    @Override
    public void onFinished(Preference preference, boolean reading) {
        if (mInitIndex < mPreferences.size()-1 && !isFinishing()) {
            if (mInitIndex == 0 && mButtonCFU.isAutoRetryCfu()) {
                Log.i(LOG_TAG, "auto retry case: ");
                CarrierConfigManager carrierConfig = (CarrierConfigManager)
                    getSystemService(CARRIER_CONFIG_SERVICE);
                if(carrierConfig != null && mPhone != null
                        && carrierConfig.getConfigForSubId(mPhone.getSubId())
                            .getBoolean(CarrierConfigManager.KEY_CDMA_CW_CF_ENABLED_BOOL)
                        && mPhone.getPhoneType() == PhoneConstants.PHONE_TYPE_CDMA ) {
                    Log.i(LOG_TAG, "auto retry and switch to cmda method UI.");
                    Intent intent = new Intent(CALL_FORWARD_INTENT);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    finish();
                }
            } else {
                mInitIndex++;
                CallForwardEditPreference pref = mPreferences.get(mInitIndex);
                pref.setExpectMore(canExpectMoreCallFwdReq());
                pref.init(this, mPhone, mReplaceInvalidCFNumbers, mServiceClass, mCallForwardByUssd);
                pref.startCallForwardOptionsQuery();
            }
        }

        super.onFinished(preference, reading);

        // Update CFNL also if CFNRc is changed
        if (preference == mButtonCFNRc && !reading && mSupportCFNL) {
            Log.d(LOG_TAG, "CFNRc is changed, updating CFNL also");
            mButtonCFNL.setExpectMore(canExpectMoreCallFwdReq());
            mButtonCFNL.init(this, mPhone, mReplaceInvalidCFNumbers, mServiceClass,
                    mCallForwardByUssd);
            mButtonCFNL.startCallForwardOptionsQuery();
        }
    }

    public void onError(Preference preference, int error) {
        if (preference == mButtonCFNL &&
                error == QtiCallConstants.CODE_UT_CF_SERVICE_NOT_REGISTERED) {
            Log.d(LOG_TAG, "CFNL failed with CODE_UT_CF_SERVICE_NOT_REGISTERED");
            mSupportCFNL = false;
            getPreferenceScreen().removePreference(preference);
            return;
        }
        super.onError(preference, error);
    }

    private boolean canExpectMoreCallFwdReq() {
        return (mInitIndex < mPreferences.size()-1);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(LOG_TAG, "onActivityResult: done");
        if (resultCode != RESULT_OK) {
            Log.d(LOG_TAG, "onActivityResult: contact picker result not OK.");
            return;
        }
        Cursor cursor = null;
        try {
            // check if the URI returned by the user belongs to the user
            final int currentUser = UserHandle.getUserId(Process.myUid());
            if (currentUser
                    != ContentProvider.getUserIdFromUri(data.getData(), currentUser)) {

                Log.w(LOG_TAG, "onActivityResult: Contact data of different user, "
                        + "cannot access");
                return;
            }
            cursor = getContentResolver().query(data.getData(),
                NUM_PROJECTION, null, null, null);
            if ((cursor == null) || (!cursor.moveToFirst())) {
                Log.d(LOG_TAG, "onActivityResult: bad contact data, no results found.");
                return;
            }

            switch (requestCode) {
                case CommandsInterface.CF_REASON_UNCONDITIONAL:
                    mButtonCFU.onPickActivityResult(cursor.getString(0));
                    break;
                case CommandsInterface.CF_REASON_BUSY:
                    mButtonCFB.onPickActivityResult(cursor.getString(0));
                    break;
                case CommandsInterface.CF_REASON_NO_REPLY:
                    mButtonCFNRy.onPickActivityResult(cursor.getString(0));
                    break;
                case CommandsInterface.CF_REASON_NOT_REACHABLE:
                    mButtonCFNRc.onPickActivityResult(cursor.getString(0));
                    break;
                default:
                    // TODO: may need exception here.
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == android.R.id.home) {  // See ActionBar#setDisplayHomeAsUpEnabled()
            CallFeaturesSetting.goUpToTopLevelSetting(this, mSubscriptionInfoHelper);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showAlertDialog(String title, String message) {
        Dialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setPositiveButton(android.R.string.ok, this)
                .setNegativeButton(android.R.string.cancel, this)
                .setOnCancelListener(this)
                .create();
        dialog.show();
    }
}
