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

import java.util.Calendar;

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
        if (alarmManager != null) { //AlarmManager.INTERVAL_DAY
            alarmManager.setRepeating(AlarmManager.RTC_WAKEUP,calendar.getTimeInMillis(),AlarmManager.INTERVAL_DAY,startServiceIntent);
        }

    }

    public static void stopService(Context context) {
        context.stopService(new Intent(context, BackgroundService.class));
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
     * @param activity Aufrufende MainActivity
     */
    public static boolean checkServiceIsRunning(MainActivity activity, Class serviceClass) {

        ActivityManager manager = (ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);

        if (manager == null) {
            return false;
        }

        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getClass().getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

}

