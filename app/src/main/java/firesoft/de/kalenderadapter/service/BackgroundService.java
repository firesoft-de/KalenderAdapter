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

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.arch.lifecycle.MutableLiveData;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.Loader;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;

import firesoft.de.kalenderadapter.BuildConfig;
import firesoft.de.kalenderadapter.MainActivity;
import firesoft.de.kalenderadapter.R;
import firesoft.de.kalenderadapter.data.ResultWrapper;
import firesoft.de.kalenderadapter.data.ServerParameter;
import firesoft.de.kalenderadapter.manager.CalendarManager;
import firesoft.de.kalenderadapter.manager.PreferencesManager;
import firesoft.de.kalenderadapter.utility.DataLoader;

public class BackgroundService extends Service implements Loader.OnLoadCompleteListener<ResultWrapper>{

    PreferencesManager pManager;
    CalendarManager cManager;
    DataLoader dataLoader;

    public final static byte ID = 15;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (BuildConfig.DEBUG) {
            writeToFile("Service started!");
            Log.d("LOG_SERVICE", "Service activated!");
        }

        try {

            // PreferencesManager starten
            pManager = new PreferencesManager(getApplicationContext());
            pManager.load();

            if (BuildConfig.DEBUG) {
                writeToFile("PreferencesManager loaded");
                Log.d("LOG_SERVICE", "PreferencesManager loaded!");
            }

            // CalendarManager starten
            cManager = new CalendarManager(getApplicationContext(),null);
            cManager.setActiveCalendar(pManager.getActiveCalendarId());             // Aktiven Kalender laden
    //        cManager.setEntryIdsFromString(pManager.getEntryIds());                 // Die von der App gesetzten Einträge laden
            cManager.loadCalendarEntries();                                         // Die Kalender laden

            if (BuildConfig.DEBUG) {
                writeToFile("CalendarManager loaded");
                Log.d("LOG_SERVICE", "CalendarManager loaded!");
            }

            // Prüfen, ob alle benötigten Daten vorliegen
            if (pManager.getUrl().equals("") || pManager.getPassword().equals("") || pManager.getUrl().equals("") || cManager.getActiveCalendar() == null) {
                // Es fehlen Daten
                return Service.START_NOT_STICKY; // Aussage für das OS: Service nicht neustarten. Siehe https://stackoverflow.com/questions/9093271/start-sticky-and-start-not-sticky
            }

            // Die einzelnen Parameter hinzufügen und im Preferences Manager speichern
            ArrayList<ServerParameter> parameters = new ArrayList<>();
            ServerParameter param = new ServerParameter("url", pManager.getUrl());
            parameters.add(param);

            param = new ServerParameter("user", pManager.getUser());
            parameters.add(param);

            param = new ServerParameter("pw", pManager.getPassword());
            parameters.add(param);

            // Kommunikationskanal mit Backgroundthreads. Wird im BackgroundService nicht benötigt. Muss aber dem DataLoader mitgegeben werden.
            MutableLiveData<String> messageFromBackground = new MutableLiveData<>();
            MutableLiveData<Integer> valFromBackground = new MutableLiveData<>();
            MutableLiveData<Integer> maxFromBackground = new MutableLiveData<>();

            dataLoader = new DataLoader(parameters,getApplicationContext(),cManager,messageFromBackground, valFromBackground, maxFromBackground, pManager,false);

            if (BuildConfig.DEBUG) {
                writeToFile("DataLoader initalized!");
                Log.d("LOG_SERVICE", "DataLoader initalized!");
            }

            // Basierend auf https://stackoverflow.com/questions/8696146/can-you-use-a-loadermanager-from-a-service/24393728
            dataLoader.registerListener(1,this); // 1 = Marker für MainLoader (im AsyncTaskManager definiert)
            dataLoader.startLoading();

            if (BuildConfig.DEBUG) {
                writeToFile("DataLoader running!");
                Log.d("LOG_SERVICE", "DataLoader running!");
            }

        } catch (Exception e) {
            if (BuildConfig.DEBUG) {
                Log.d("LOG_SERVICE", "Error! " + e.getMessage());
                writeToFile("Error! " + e.getMessage());
            }
            e.printStackTrace();
        }

        return super.onStartCommand(intent, flags, startId);
    }

    /**
     * Wird aufgerufen, wenn die Synchronisation mit dem Server abgeschlossen wurde
     */
    @Override
    public void onLoadComplete(@NonNull Loader<ResultWrapper> loader, @Nullable ResultWrapper data) {

        if (data == null) {
            return;
        }

        if (data.getException() != null) {
            // Es ist ein Fehler aufgetreten. Machen kann man jetzt aber nicht wirklich viel.
            Toast.makeText(getApplicationContext(), "KalenderAdapter: " + getString(R.string.error_background_service) + data.getException().getMessage(), Toast.LENGTH_LONG).show();
            if (BuildConfig.DEBUG) {
                buildNotification(getString(R.string.error_background_service) + " " + data.getException().getMessage(), data.getAddedEntrys(), data.getDeletedEntrys());
            }
            return;
        }

        if (BuildConfig.DEBUG) {
            Log.d("LOG_SERVICE", "Service completed!");
            writeToFile("Service completed!");

            buildNotification(null, data.getAddedEntrys(),data.getDeletedEntrys());
        }

    }

    private void writeToFile(String data) {

        String message;

        message = Calendar.getInstance().getTime().toString();
        message = message.concat(" - ");
        message = message.concat(data);
        message = message.concat("\n");

        try {
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(getApplicationContext().openFileOutput("background_log.txt", Context.MODE_APPEND));
            outputStreamWriter.write(message);
            outputStreamWriter.close();
        }
        catch (IOException e) {
            Log.e("Exception", "File write failed: " + e.toString());
        }
    }

    @Override
    public void onDestroy() {

        // Loader stoppen
        if (dataLoader != null) {
            dataLoader.unregisterListener(this);
            dataLoader.cancelLoad();
            dataLoader.stopLoading();
        }

    }

    /**
     *
     * @param text String, Text der in der Notification angezeigt werden soll
     */
    private void buildNotification(@Nullable String text, @Nullable int addedEntries, @Nullable int deletedEntries) {
        //Notification erstellen
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        Bitmap largeIcon = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_icon);
        Notification notification;

        Intent resultIntent = new Intent(this, MainActivity.class);
        resultIntent.setAction("");
        PendingIntent resultPendingIntent =
                PendingIntent.getActivity(
                        this,
                        0,
                        resultIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT
                );

        // Inhalt der Notification zusammenbauen
        String notificationText;

        Calendar cal = Calendar.getInstance();
        @SuppressLint("DefaultLocale") String time = String.format("%02d:%02d",cal.get(Calendar.HOUR_OF_DAY),cal.get(Calendar.MINUTE));

        if (text != null) {
            notificationText = text;
        }
        else if (addedEntries >= 0 || deletedEntries >= 0) {

            if (addedEntries < 0) {addedEntries = 0;}
            if (deletedEntries < 0) {deletedEntries = 0;}

            notificationText = getString(R.string.info_notification_text) + " " + getString(R.string.info_notification_added_entries)
                    + "\n" + String.valueOf(addedEntries)
                    + "\n" + " " + getString(R.string.info_notification_deleted_entries) + String.valueOf(deletedEntries);
        }
        else {
            notificationText = getString(R.string.info_notification_text);
        }

        // Zeit einfügen
        notificationText = time + " " + notificationText;

        //API Version prüfen und entsprechend eine Notification mit oder ohne NotificationChannel erstellen
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // The id of the channel.
            String id = "channel_01";
            // The user-visible name of the channel.
            CharSequence name = "Synchronisierung"; //getString(R.string.channel_name);
            // The user-visible description of the channel.
            String description = "Synchronisierungsbenachrichtigung";
            int importance = 0;
            importance = NotificationManager.IMPORTANCE_DEFAULT;

            NotificationChannel mChannel = new NotificationChannel(id, name, importance);
            // Configure the notification channel.
            mChannel.setDescription(description);
            mChannel.enableLights(true);
            // Sets the notification light color for notifications posted to this
            // channel, if the device supports this feature.
            mChannel.setLightColor(Color.BLUE);
//            mChannel.enableVibration(true);
//            mChannel.setVibrationPattern(new long[]{100, 200, 100, 200, 100});
            notificationManager.createNotificationChannel(mChannel);

            notification = new Notification.Builder(getApplicationContext(), id)
                    .setSmallIcon(R.mipmap.ic_icon)
                    .setContentTitle(getString(R.string.info_notification_title))
                    .setContentText(notificationText)
                    .setChannelId(mChannel.getId())
                    .setContentIntent(resultPendingIntent)
                    .setAutoCancel(true)
                    .setStyle(new Notification.BigTextStyle()
                            .bigText(notificationText))
                    .build();

        } else {
            notification = new Notification.Builder(getApplicationContext())
                    .setSmallIcon(R.mipmap.ic_icon)
                    .setContentTitle(getString(R.string.info_notification_title))
                    .setContentText(notificationText)
                    .setContentIntent(resultPendingIntent)
                    .setAutoCancel(true)
                    .setStyle(new Notification.BigTextStyle()
                            .bigText(notificationText))
                    .build();
        }

        notificationManager.notify(1, notification);
    }

}
