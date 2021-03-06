package com.example.android.sunshine.app;

import android.annotation.TargetApi;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.support.design.widget.Snackbar;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;

import com.example.android.sunshine.app.data.WeatherContract;
import com.example.android.sunshine.app.sync.SunshineSyncAdapter;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.ui.PlacePicker;
import com.google.android.gms.maps.model.LatLng;

public class SettingActivity extends PreferenceActivity implements Preference.OnPreferenceChangeListener, SharedPreferences.OnSharedPreferenceChangeListener {

    protected final static int PLACE_PICKER_REQUEST = 9090;
    private ImageView mAttribution;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.pref_general);
        bindPreferenceSummaryToValue(findPreference(getString(R.string.pref_location_key)));
        bindPreferenceSummaryToValue(findPreference(getString(R.string.pref_units_key)));
        bindPreferenceSummaryToValue(findPreference(getString(R.string.pref_art_pack_key)));

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            mAttribution = new ImageView(this);
            mAttribution.setImageResource(R.drawable.powered_by_google_light);

            if(!Utility.isLocationLatLonAvailable(this)) {
                mAttribution.setVisibility(View.GONE);
            }
            setListFooter(mAttribution);
        }
    }

    @Override
    protected void onResume() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        sp.registerOnSharedPreferenceChangeListener(this);
        super.onResume();
    }

    @Override
    protected void onPause() {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        sp.unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    private void bindPreferenceSummaryToValue(Preference preference) {
        preference.setOnPreferenceChangeListener(this);
        setPreferenceSummary(preference, PreferenceManager.getDefaultSharedPreferences(preference.getContext()).getString(preference.getKey(), ""));
    }

    private void setPreferenceSummary(Preference preference, Object value) {
        String stringValue = value.toString();
        String key = preference.getKey();

        if(preference instanceof ListPreference) {
            // For list preferences, look up the correct display value in
            // the preference's 'entries' list (since they have separate labels/values).
            ListPreference listPreference = (ListPreference) preference;
            int prefIndex = listPreference.findIndexOfValue(stringValue);

            if (prefIndex >= 0) {
                // For other preferences, set the summary to the value's simple string representation.
                preference.setSummary(listPreference.getEntries()[prefIndex]);
            }
        } else if (key.equals(getString(R.string.pref_location_key))) {
            @SunshineSyncAdapter.LocationStatus int location = Utility.getLocationStatus(this);
            switch(location) {
                case SunshineSyncAdapter.LOCATION_STATUS_OK:
                    preference.setSummary(stringValue);
                    break;
                case SunshineSyncAdapter.LOCATION_STATUS_UNKNOWN:
                    preference.setSummary(getString(R.string.pref_location_unknown_description, value.toString()));
                    break;
                case SunshineSyncAdapter.LOCATION_STATUS_INVALID:
                    preference.setSummary(getString(R.string.pref_location_error_description, value.toString()));
                    break;
                default:

            }
        } else {
            preference.setSummary(stringValue);
        }
    }
    @Override
    public boolean onPreferenceChange(Preference preference, Object value) {
        setPreferenceSummary(preference, value);
        return true;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    @Override
    public Intent getParentActivityIntent() {
        return super.getParentActivityIntent().addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Check to see if the result is from our Place Picker intent
        if(requestCode == PLACE_PICKER_REQUEST) {
            // Make sure the request was successful
            if(resultCode == RESULT_OK) {
                Place place = PlacePicker.getPlace(data, this);
                String address = place.getAddress().toString();

                LatLng latLong = place.getLatLng();

                if(TextUtils.isEmpty(address)) {
                    address = String.format("(%.2f, %.2f)", latLong.latitude, latLong.longitude);
                }

                SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString(getString(R.string.pref_location_key), address);

                editor.putFloat(getString(R.string.pref_location_latitude), (float) latLong.latitude);
                editor.putFloat(getString(R.string.pref_location_longitude), (float)latLong.longitude);
                editor.commit();

                // Tell the SyncAdapter that we've changed the location, so that we can update
                // our UI with new values. We need to do this manually because we are responding
                // to the PlacePicker widget result here instead of allowing the
                // LocationEditTextPreference to handle these changes and invoke our callbacks.
                Preference locationPreference = findPreference(getString(R.string.pref_location_key));
                setPreferenceSummary(locationPreference, address);

                if(mAttribution != null) {
                    mAttribution.setVisibility(View.VISIBLE);
                } else {
                    View rootView = findViewById(android.R.id.content);
                    Snackbar.make(rootView, getString(R.string.attribution_text), Snackbar.LENGTH_LONG).show();
                }

                Utility.resetLocationStatus(this);
                SunshineSyncAdapter.syncImmediately(this);
            } else {
                super.onActivityResult(requestCode, resultCode, data);
            }
        }
    }

    // This gets called after the preference is changed, which is important because we
    // start our synchronization here
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if(key.equals(getString(R.string.pref_location_key))) {

            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.remove(getString(R.string.pref_location_latitude));
            editor.remove(getString(R.string.pref_location_longitude));
            editor.commit();

            if(mAttribution != null) {
                mAttribution.setVisibility(View.GONE);
            }
            Utility.resetLocationStatus(this);
            SunshineSyncAdapter.syncImmediately(this);
        } else if(key.equals(getString(R.string.pref_units_key))) {
            getContentResolver().notifyChange(WeatherContract.WeatherEntry.CONTENT_URI, null);
        } else if(key.equals(getString(R.string.pref_location_status_key))) {
            Preference locationPreference = findPreference(getString(R.string.pref_location_key));
            bindPreferenceSummaryToValue(locationPreference);
        } else if(key.equals(getString(R.string.pref_art_pack_key))) {
            getContentResolver().notifyChange(WeatherContract.WeatherEntry.CONTENT_URI, null);
        }
    }
}
