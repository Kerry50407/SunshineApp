package com.example.android.sunshine.app;

/**
 * Created by Kerry on 2016/2/23.
 */

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.text.format.Time;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A placeholder fragment containing a simple view.
 */
public class ForecastFragment extends Fragment {

    private ListView listView1;
    private ArrayAdapter<String> mForecastAdapter;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.forecastfragment, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if(id == R.id.action_refresh) {

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
            String location = prefs.getString(getString(R.string.pref_location_key), getString(R.string.pref_location_default));
            new FetchWeatherTask().execute(location);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_main, container, false);
        String[] forecastArray = {"Today - Sunny 88/63","Tomorrow - Foggy 70/46"};
        List<String> weekForecast = new ArrayList<String>(Arrays.asList(forecastArray));

        mForecastAdapter = new ArrayAdapter<String>(getActivity(), R.layout.list_item_forecast, R.id.list_item_forecast_textView, weekForecast);
        listView1 = (ListView) rootView.findViewById(R.id.listView_forecast);
        listView1.setAdapter(mForecastAdapter);

        listView1.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                String forecast = mForecastAdapter.getItem(i);
                Intent intent = new Intent(getActivity(), DetailActivity.class).putExtra(Intent.EXTRA_TEXT, forecast);
                startActivity(intent);
            }
        });
        return rootView;
    }

    public class FetchWeatherTask extends AsyncTask<String, Void, String[]> {

        private final String LOG_TAG = FetchWeatherTask.class.getSimpleName();

        private String getReadableDataString(long time) {
            SimpleDateFormat shortenDateFormat = new SimpleDateFormat("EEE MMM dd");
            return shortenDateFormat.format(time);
        }

        private String formatHighLows(double high, double low, String unitType) {

            if(unitType.equals(getString(R.string.pref_units_imperial))) {
                high = (high * 1.8) +32;
                low = (low * 1.8) + 32;
            } else if(!unitType.equals(getString(R.string.pref_units_metric))) {
                Log.d(LOG_TAG, "Unit type not found: " + unitType);
            }

            long roundedHigh = Math.round(high);
            long roundedLow = Math.round(low);

            String highLowStr = roundedHigh + "/" + roundedLow;
            return highLowStr;
        }

        private String[] getWeatherDataFromJson(String forecastJsonStr, int numDays) throws JSONException {
            final String OWM_LIST = "list";
            final String OWM_WEATHER = "weather";
            final String OWM_TEMPERATURE = "temp";
            final String OWM_MAX = "max";
            final String OWM_MIN = "min";
            final String OWM_DESCRIPTION = "main";

            JSONObject forecastJson = new JSONObject(forecastJsonStr);
            JSONArray weatherArray = forecastJson.getJSONArray(OWM_LIST);

            Time dayTime = new Time();
            dayTime.setToNow();

            int julianStartDay = Time.getJulianDay(System.currentTimeMillis(), dayTime.gmtoff);
            dayTime = new Time();
            String[] resultStrs = new String[numDays];

            SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
            String unitType = sharedPrefs.getString(getString(R.string.pref_units_key), getString(R.string.pref_units_metric));
            for(int i=0; i<weatherArray.length(); i++) {
                String day;
                String description;
                String highAndLow;

                JSONObject dayForecast =  weatherArray.getJSONObject(i);

                long dateTime;
                dateTime = dayTime.setJulianDay(julianStartDay + i);
                day = getReadableDataString(dateTime);

                JSONObject weatherObject = dayForecast.getJSONArray(OWM_WEATHER).getJSONObject(0);
                description = weatherObject.getString(OWM_DESCRIPTION);

                JSONObject temperatureObject = dayForecast.getJSONObject(OWM_TEMPERATURE);
                double high = temperatureObject.getDouble(OWM_MAX);
                double low = temperatureObject.getDouble(OWM_MIN);

                highAndLow = formatHighLows(high, low, unitType);
                resultStrs[i] = day + " - " + description + " - " + highAndLow;
            }
            for(String s: resultStrs) {
                Log.v(LOG_TAG, "Forecast entry: " + s);
            }
            return resultStrs;
        }

        @Override
        protected String[] doInBackground(String... id) {
            HttpURLConnection urlConnection = null;
            BufferedReader reader = null;

            String forecastJsonStr = null;

            String lang = "zh_cn";
            String appid = "44db6a862fba0b067b1930da0d769e98";
            int numDays = 7;
            try {
                final String FORCAST_BASE_URL = "http://api.openweathermap.org/data/2.5/forecast/daily?";
                final String ID_PARAM = "id";
                final String LANG_PARAM = "lang";
                final String APPID_PARAM = "appid";

                Uri buildUri = Uri.parse(FORCAST_BASE_URL).buildUpon()
                        .appendQueryParameter(ID_PARAM, id[0])
                        .appendQueryParameter(LANG_PARAM, lang)
                        .appendQueryParameter(APPID_PARAM, appid)
                        .build();

                URL url = new URL(buildUri.toString());

                Log.d(LOG_TAG, "Build URI = " + url);

                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                urlConnection.connect();

                InputStream inputStream = urlConnection.getInputStream();
                StringBuffer buffer = new StringBuffer();
                if(inputStream == null) {
                    return null;
                }
                reader = new BufferedReader(new InputStreamReader(inputStream));

                String line;
                while((line = reader.readLine()) != null) {
                    buffer.append(line + "\n");
                }

                if(buffer.length() == 0) {
                    return null;
                }
                forecastJsonStr = buffer.toString();

                Log.d(LOG_TAG, "Forecast JSON String" + forecastJsonStr);
            } catch (IOException e) {
                Log.e(LOG_TAG, "Error ", e);
                return null;
            } finally {
                if(urlConnection != null) {
                    urlConnection.disconnect();
                }
                if(reader != null) {
                    try {
                        reader.close();
                    } catch(final IOException e) {
                        Log.e(LOG_TAG, "Error closing stream", e);
                    }
                }
            }

            try {
                return getWeatherDataFromJson(forecastJsonStr, numDays);
            } catch (JSONException e) {
                Log.e(LOG_TAG, e.getMessage(), e);
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(String[] result) {
            super.onPostExecute(result);
            if(result != null) {
                mForecastAdapter.clear();
                for(String dayForecastStr: result) {
                    mForecastAdapter.add(dayForecastStr);
                }
            }
        }

    }
}