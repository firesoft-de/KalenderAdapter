/*
 * Copyright (c) 2018.  David Schlossarczyk
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * For the full license visit https://www.gnu.org/licenses/gpl-3.0.
 */

package firesoft.de.kalenderadapter.service;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.Calendar;

import firesoft.de.kalenderadapter.BuildConfig;
import firesoft.de.kalenderadapter.MainActivity;

import static android.content.Context.ALARM_SERVICE;

public class ServiceUtil extends BroadcastReceiver {

    public static void startService(Context context) {

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(ALARM_SERVICE);

        Intent serviceIntent = new Intent(context, BackgroundService.class);
        PendingIntent startServiceIntent = PendingIntent.getService(context,0,serviceIntent,0);

        //Server wird alle 24 Stunden um 03:00 Uhr überprüft
        Calendar calendar = Calendar.getInstance();

        calendar.set(Calendar.HOUR_OF_DAY, 3);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);

        //AlarmManager aktivieren
        if (alarmManager != null) {
            alarmManager.setRepeating(AlarmManager.RTC_WAKEUP,calendar.getTimeInMillis(),AlarmManager.INTERVAL_DAY,startServiceIntent);
        }

    }

    /**
     *
     * @param context Context des Aufrufs
     * @param start Startzeit in Millisekunden (gezählt von 00:00)
     * @param interval Ausführungsintervall
     */
    public static void startService(Context context, long start, long interval) {

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(ALARM_SERVICE);

        Intent serviceIntent = new Intent(context, BackgroundService.class);
        PendingIntent startServiceIntent = PendingIntent.getService(context,0,serviceIntent,0);

        if (BuildConfig.DEBUG) {
            Log.d("LOG_SERVICE", "Starting Timestamp: " + String.valueOf(start));
            Log.d("LOG_SERVICE", "Interval: " + String.valueOf(interval));
        }

        //AlarmManager aktivieren
        if (alarmManager != null) {
            alarmManager.setRepeating(AlarmManager.RTC_WAKEUP,start*1000,interval*1000,startServiceIntent);
        }

    }

    public static void stopService(Context context) {

        // https://stackoverflow.com/questions/47545634/how-to-stop-service-using-alarmmanager

        Intent serviceIntent = new Intent(context, BackgroundService.class);
        PendingIntent pendingIntent = PendingIntent.getService(context, 0, serviceIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        pendingIntent.cancel();
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && intent.getAction() != null) {
            // Wird aufgerufen, wenn das Telefon neugestartet wird
            if (intent.getAction().equals("android.intent.action.BOOT_COMPLETED")) {
                startService(context);
            }
        }
    }

    /**
     * Prüft, ob der Hintergrundservice läuft oder nicht. Basiert auf https://stackoverflow.com/questions/600207/how-to-check-if-a-service-is-running-on-android
     */
    public static boolean checkServiceIsRunning(Context context, Class serviceClass) {
        //https://stackoverflow.com/questions/4556670/how-to-check-if-alarmmanager-already-has-an-alarm-set
        return (PendingIntent.getService(context, 0, new Intent(context,BackgroundService.class), PendingIntent.FLAG_NO_CREATE) != null);
    }

}

