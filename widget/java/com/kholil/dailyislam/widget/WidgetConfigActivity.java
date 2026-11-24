package com.kholil.dailyislam.widget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.kholil.dailyislam.R;

/**
 * Widget configuration screen: pick one of Bangladesh's 64 districts.
 * The choice is saved in SharedPreferences and used for prayer times.
 * Also opens (via the widget host) right after the widget is placed.
 */
public class WidgetConfigActivity extends Activity {

    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // If the user backs out, the widget must not be added half-configured.
        setResult(RESULT_CANCELED);
        setContentView(R.layout.widget_config);

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            appWidgetId = extras.getInt(
                    AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        }

        ListView list = findViewById(R.id.district_list);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_list_item_1, Districts.displayNames());
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Districts.District district = Districts.ALL[position];
                SharedPreferences.Editor editor = getSharedPreferences(
                        PrayerWidgetProvider.PREFS_NAME, MODE_PRIVATE).edit();
                editor.putString(PrayerWidgetProvider.KEY_DISTRICT, district.nameBn);
                editor.putFloat(PrayerWidgetProvider.KEY_LATITUDE, (float) district.latitude);
                editor.putFloat(PrayerWidgetProvider.KEY_LONGITUDE, (float) district.longitude);
                editor.apply();

                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    AppWidgetManager manager = AppWidgetManager.getInstance(WidgetConfigActivity.this);
                    PrayerWidgetProvider.updateWidget(
                            WidgetConfigActivity.this, manager, appWidgetId);
                } else {
                    PrayerWidgetProvider.refreshAll(WidgetConfigActivity.this);
                }

                Intent result = new Intent();
                result.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
                setResult(RESULT_OK, result);
                finish();
            }
        });
    }
}
