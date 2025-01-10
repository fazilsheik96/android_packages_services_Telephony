/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
* Changes from Qualcomm Innovation Center, Inc. are provided under the following license:
* Copyright (c) 2024 Qualcomm Innovation Center, Inc. All rights reserved.
* SPDX-License-Identifier: BSD-3-Clause-Clear
*/

package com.android.phone;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.os.UserManager;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.telephony.CarrierConfigManager;
import android.util.Log;
import android.view.MenuItem;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneConstants;
import com.android.internal.telephony.flags.Flags;

import java.util.ArrayList;

public class GsmUmtsCallOptions extends PreferenceActivity {
    private static final String LOG_TAG = "GsmUmtsCallOptions";
    private final boolean DBG = (PhoneGlobals.DBG_LEVEL >= 2);

    public static final String CALL_FORWARDING_KEY = "call_forwarding_key";
    public static final String CALL_BARRING_KEY = "call_barring_key";
    public static final String ADDITIONAL_GSM_SETTINGS_KEY = "additional_gsm_call_settings_key";

    private boolean mCommon = false;

    private Phone mPhone;
    private IntentFilter mIntentFilter;
    private static ArrayList<Preference> mPreferences = new ArrayList<Preference> ();

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_AIRPLANE_MODE_CHANGED) && mPhone != null) {
                setPreferencesState(PhoneUtils.isSuppServiceAllowedInAirplaneMode(mPhone));
            }
        }
    };

    private void setPreferencesState (boolean state) {
        for (Preference pref : mPreferences) {
            pref.setEnabled(state);
        }
    }

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        addPreferencesFromResource(R.xml.gsm_umts_call_options);

        SubscriptionInfoHelper subInfoHelper = new SubscriptionInfoHelper(this, getIntent());
        PersistableBundle pb = null;
        if (subInfoHelper.hasSubId()) {
            pb = PhoneGlobals.getInstance().getCarrierConfigForSubId(subInfoHelper.getSubId());
        } else {
            pb = PhoneGlobals.getInstance().getCarrierConfig();
        }
        mCommon = pb != null && pb.getBoolean("config_common_callsettings_support_bool");
        subInfoHelper.setActionBarTitle(
                getActionBar(), getResources(),
                mCommon ? R.string.labelCommonMore_with_label : R.string.labelGsmMore_with_label);

        init(getPreferenceScreen(), subInfoHelper);
        mPhone = subInfoHelper.getPhone();
        if (mPhone != null) {
            setPreferencesState(PhoneUtils.isSuppServiceAllowedInAirplaneMode(mPhone));
        }
        if (subInfoHelper.getPhone().getPhoneType() != PhoneConstants.PHONE_TYPE_GSM) {
            //disable the entire screen
            getPreferenceScreen().setEnabled(false);
        }
        mIntentFilter = new IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        registerReceiver(mBroadcastReceiver, mIntentFilter);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mBroadcastReceiver);
        mPreferences.clear();
        mIntentFilter = null;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public static void init(PreferenceScreen prefScreen, SubscriptionInfoHelper subInfoHelper) {
        PersistableBundle b = null;
        if (subInfoHelper.hasSubId()) {
            b = PhoneGlobals.getInstance().getCarrierConfigForSubId(subInfoHelper.getSubId());
        } else {
            b = PhoneGlobals.getInstance().getCarrierConfig();
        }

        // If mobile network configs are restricted, then hide the GsmUmtsCallForwardOptions,
        // GsmUmtsAdditionalCallOptions, and GsmUmtsCallBarringOptions.
        UserManager userManager = (UserManager) subInfoHelper.getPhone().getContext()
                .getSystemService(Context.USER_SERVICE);
        boolean mobileNetworkConfigsRestricted =
                userManager.hasUserRestriction(UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS);
        if (Flags.ensureAccessToCallSettingsIsRestricted() && mobileNetworkConfigsRestricted) {
            Log.i(LOG_TAG, "Mobile network configs are restricted, hiding GSM call "
                    + "forwarding, additional call settings, and call options.");
        }

        Preference callForwardingPref = prefScreen.findPreference(CALL_FORWARDING_KEY);
        if (callForwardingPref != null) {
            if (b != null && b.getBoolean(
                    CarrierConfigManager.KEY_CALL_FORWARDING_VISIBILITY_BOOL) &&
                    (!Flags.ensureAccessToCallSettingsIsRestricted() ||
                            !mobileNetworkConfigsRestricted)) {
                callForwardingPref.setIntent(
                        subInfoHelper.getIntent(CallForwardType.class));
                mPreferences.add(callForwardingPref);
            } else {
                prefScreen.removePreference(callForwardingPref);
            }
        }

        Preference additionalGsmSettingsPref =
                prefScreen.findPreference(ADDITIONAL_GSM_SETTINGS_KEY);
        if (additionalGsmSettingsPref != null) {
            if (b != null && (b.getBoolean(
                    CarrierConfigManager.KEY_ADDITIONAL_SETTINGS_CALL_WAITING_VISIBILITY_BOOL)
                    || b.getBoolean(
                    CarrierConfigManager.KEY_ADDITIONAL_SETTINGS_CALLER_ID_VISIBILITY_BOOL)) &&
                    (!Flags.ensureAccessToCallSettingsIsRestricted() ||
                            !mobileNetworkConfigsRestricted)) {
                additionalGsmSettingsPref.setIntent(
                        subInfoHelper.getIntent(GsmUmtsAdditionalCallOptions.class));
                mPreferences.add(additionalGsmSettingsPref);
            } else {
                prefScreen.removePreference(additionalGsmSettingsPref);
            }
        }

        Preference callBarringPref = prefScreen.findPreference(CALL_BARRING_KEY);
        if (callBarringPref != null) {
            if (b != null && b.getBoolean(CarrierConfigManager.KEY_CALL_BARRING_VISIBILITY_BOOL) &&
                    (!Flags.ensureAccessToCallSettingsIsRestricted() ||
                            !mobileNetworkConfigsRestricted)) {
                callBarringPref.setIntent(subInfoHelper.getIntent(GsmUmtsCallBarringOptions.class));
                mPreferences.add(callBarringPref);
            } else {
                prefScreen.removePreference(callBarringPref);
            }
        }
    }
}
