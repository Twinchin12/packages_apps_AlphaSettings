/*
 * Copyright (C) 2016-2022 crDroid Android Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alpha.settings.fragments;

import static android.os.UserHandle.USER_CURRENT;
import static android.os.UserHandle.USER_SYSTEM;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.database.ContentObserver;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Handler;
import android.content.om.IOverlayManager;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.SwitchPreference;

import com.android.internal.logging.nano.MetricsProto;
import com.android.internal.util.crdroid.Utils;
import com.android.internal.util.crdroid.ThemeUtils;
import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import com.alpha.settings.preferences.CustomSeekBarPreference;
import com.alpha.settings.preferences.SystemSettingListPreference;

import lineageos.providers.LineageSettings;

import java.util.List;
import java.util.ArrayList;

@SearchIndexable
public class QuickSettings extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener {

    public static final String TAG = "QuickSettings";

    private static final String KEY_SHOW_BRIGHTNESS_SLIDER = "qs_show_brightness_slider";
    private static final String KEY_BRIGHTNESS_SLIDER_POSITION = "qs_brightness_slider_position";
    private static final String KEY_SHOW_AUTO_BRIGHTNESS = "qs_show_auto_brightness";
    private static final String KEY_PREF_TILE_ANIM_STYLE = "qs_tile_animation_style";
    private static final String KEY_PREF_TILE_ANIM_DURATION = "qs_tile_animation_duration";
    private static final String KEY_PREF_TILE_ANIM_INTERPOLATOR = "qs_tile_animation_interpolator";
    private static final String KEY_QS_PANEL_STYLE  = "qs_panel_style";
    private static final String QS_PAGE_TRANSITIONS = "custom_transitions_page_tile";

    private Handler mHandler;
    private IOverlayManager mOverlayManager;
    private IOverlayManager mOverlayService;
    private SystemSettingListPreference mQsStyle;
    private ListPreference mShowBrightnessSlider;
    private ListPreference mBrightnessSliderPosition;
    private SwitchPreference mShowAutoBrightness;
    private ListPreference mTileAnimationStyle;
    private CustomSeekBarPreference mTileAnimationDuration;
    private ListPreference mTileAnimationInterpolator;
    private SystemSettingListPreference mPageTransitions;
    private ThemeUtils mThemeUtils;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.alpha_settings_quicksettings);

        final Context mContext = getActivity().getApplicationContext();
        final ContentResolver resolver = mContext.getContentResolver();
        final PreferenceScreen prefScreen = getPreferenceScreen();

        mThemeUtils = new ThemeUtils(getActivity());

        mOverlayService = IOverlayManager.Stub
        .asInterface(ServiceManager.getService(Context.OVERLAY_SERVICE));

        mQsStyle = (SystemSettingListPreference) findPreference(KEY_QS_PANEL_STYLE);
        mCustomSettingsObserver.observe();

        mPageTransitions = (SystemSettingListPreference) findPreference(QS_PAGE_TRANSITIONS);
        mPageTransitions.setOnPreferenceChangeListener(this);
        int customTransitions = Settings.System.getIntForUser(resolver,
                Settings.System.CUSTOM_TRANSITIONS_KEY,
                0, UserHandle.USER_CURRENT);
        mPageTransitions.setValue(String.valueOf(customTransitions));
        mPageTransitions.setSummary(mPageTransitions.getEntry());


        mShowBrightnessSlider = findPreference(KEY_SHOW_BRIGHTNESS_SLIDER);
        mShowBrightnessSlider.setOnPreferenceChangeListener(this);
        boolean showSlider = LineageSettings.Secure.getIntForUser(resolver,
                LineageSettings.Secure.QS_SHOW_BRIGHTNESS_SLIDER, 1, UserHandle.USER_CURRENT) > 0;

        mBrightnessSliderPosition = findPreference(KEY_BRIGHTNESS_SLIDER_POSITION);
        mBrightnessSliderPosition.setEnabled(showSlider);

        mShowAutoBrightness = findPreference(KEY_SHOW_AUTO_BRIGHTNESS);
        boolean automaticAvailable = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_automatic_brightness_available);
        if (automaticAvailable) {
            mShowAutoBrightness.setEnabled(showSlider);
        } else {
            prefScreen.removePreference(mShowAutoBrightness);
        }
        mTileAnimationStyle = (ListPreference) findPreference(KEY_PREF_TILE_ANIM_STYLE);
        mTileAnimationDuration = (CustomSeekBarPreference) findPreference(KEY_PREF_TILE_ANIM_DURATION);
        mTileAnimationInterpolator = (ListPreference) findPreference(KEY_PREF_TILE_ANIM_INTERPOLATOR);

        mTileAnimationStyle.setOnPreferenceChangeListener(this);

        int tileAnimationStyle = Settings.System.getIntForUser(resolver,
                Settings.System.QS_TILE_ANIMATION_STYLE, 0, UserHandle.USER_CURRENT);
        updateAnimTileStyle(tileAnimationStyle);
    }

    private CustomSettingsObserver mCustomSettingsObserver = new CustomSettingsObserver(mHandler);
    private class CustomSettingsObserver extends ContentObserver {

        CustomSettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            Context mContext = getContext();
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.QS_PANEL_STYLE),
                    false, this, UserHandle.USER_ALL);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            if (uri.equals(Settings.System.getUriFor(Settings.System.QS_PANEL_STYLE))) {
                updateQsStyle();
            }
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mShowBrightnessSlider) {
            int value = Integer.parseInt((String) newValue);
            mBrightnessSliderPosition.setEnabled(value > 0);
            if (mShowAutoBrightness != null)
                mShowAutoBrightness.setEnabled(value > 0);
            return true;
        } else if (preference == mTileAnimationStyle) {
            int value = Integer.parseInt((String) newValue);
            updateAnimTileStyle(value);
            return true;
        } else if (preference == mQsStyle) {
            mCustomSettingsObserver.observe();
            return true;
            } else if (preference == mPageTransitions) {
            int customTransitions = Integer.parseInt(((String) newValue).toString());
            Settings.System.putIntForUser(getContentResolver(),
                    Settings.System.CUSTOM_TRANSITIONS_KEY, customTransitions, UserHandle.USER_CURRENT);
            int index = mPageTransitions.findIndexOfValue((String) newValue);
            mPageTransitions.setSummary(
                    mPageTransitions.getEntries()[index]);
            return true;
        }
        return false;
    }

    public static void reset(Context mContext) {
        ContentResolver resolver = mContext.getContentResolver();
        Settings.System.putIntForUser(resolver,
                Settings.System.SECURE_LOCKSCREEN_QS_DISABLED, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.QS_TRANSPARENCY, 100, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.QS_FOOTER_DATA_USAGE, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.QS_TILE_ANIMATION_STYLE, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.QS_TILE_ANIMATION_DURATION, 1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.QS_TILE_ANIMATION_INTERPOLATOR, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.QS_LAYOUT_COLUMNS_LANDSCAPE, 4, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.QS_LAYOUT_COLUMNS, 2, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.QS_TILE_VERTICAL_LAYOUT, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.QS_TILE_LABEL_HIDE, 0, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.QS_TILE_LABEL_SIZE, 14, UserHandle.USER_CURRENT);
        LineageSettings.Secure.putIntForUser(resolver,
                LineageSettings.Secure.QS_SHOW_BRIGHTNESS_SLIDER, 1, UserHandle.USER_CURRENT);
        LineageSettings.Secure.putIntForUser(resolver,
                LineageSettings.Secure.QS_BRIGHTNESS_SLIDER_POSITION, 0, UserHandle.USER_CURRENT);
        LineageSettings.Secure.putIntForUser(resolver,
                LineageSettings.Secure.QS_SHOW_AUTO_BRIGHTNESS, 1, UserHandle.USER_CURRENT);
        Settings.System.putIntForUser(resolver,
                Settings.System.QS_PANEL_STYLE, 0, UserHandle.USER_CURRENT);
    }

    private void updateAnimTileStyle(int tileAnimationStyle) {
        mTileAnimationDuration.setEnabled(tileAnimationStyle != 0);
        mTileAnimationInterpolator.setEnabled(tileAnimationStyle != 0);
    }

    private void updateQsStyle() {
        ContentResolver resolver = getActivity().getContentResolver();

        int qsPanelStyle = Settings.System.getIntForUser(getContext().getContentResolver(),
                Settings.System.QS_PANEL_STYLE , 0, UserHandle.USER_CURRENT);

        switch (qsPanelStyle) {
            case 0:
              setQsStyle("com.android.systemui");
              break;
            case 1:
              setQsStyle("com.android.system.qs.outline");
              break;
            case 2:
            case 3:
              setQsStyle("com.android.system.qs.twotoneaccent");
              break;
            case 4:
              setQsStyle("com.android.system.qs.shaded");
              break;
            case 5:
              setQsStyle("com.android.system.qs.cyberpunk");
              break;
            case 6:
              setQsStyle("com.android.system.qs.neumorph");
              break;
            case 7:
              setQsStyle("com.android.system.qs.reflected");
              break;
            case 8:
              setQsStyle("com.android.system.qs.surround");
              break;
            case 9:
              setQsStyle("com.android.system.qs.thin");
              break;
            case 10:
              setQsStyle("com.android.system.qs.twotoneaccenttrans");
              break;
            default:
              break;
        }
    }

    public void setQsStyle(String overlayName) {
        mThemeUtils.setOverlayEnabled("android.theme.customization.qs_panel", overlayName, "com.android.systemui");
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.ALPHA;
    }

    /**
     * For search
     */
    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.alpha_settings_quicksettings);
}
