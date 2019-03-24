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

import java.util.ArrayList;

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

    public final static byte ID = 0;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (BuildConfig.DEBUG) {
            Log.d("LOG_SERVICE", "Service activated!");
        }

        // PreferencesManager starten
        pManager = new PreferencesManager(getApplicationContext());
        pManager.load();

        // CalendarManager starten
        cManager = new CalendarManager(getApplicationContext(),null);
        cManager.setActiveCalendar(pManager.getActiveCalendarId());             // Aktiven Kalender laden
//        cManager.setEntryIdsFromString(pManager.getEntryIds());                 // Die von der App gesetzten Einträge laden
        cManager.loadCalendarEntries();                                         // Die Kalender laden

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

        // Basierend auf https://stackoverflow.com/questions/8696146/can-you-use-a-loadermanager-from-a-service/24393728
        dataLoader.registerListener(1,this); // 1 = Marker für MainLoader (im AsyncTaskManager definiert)
        dataLoader.startLoading();

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
            Toast.makeText(getApplicationContext(), "KalenderAdapter: Während der Hintergrundsynchronisation ist ein Fehler aufgetreten! Fehlermeldung: " + data.getException().getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        if (BuildConfig.DEBUG) {
            Log.d("LOG_SERVICE", "Service completed!");
            buildNotification();
        }

        // Kein Fehler ist aufgetreten. Die IDs werden in den Preferences Manager geschrieben und gespeichert.
//        cManager.setEntryIds(data.getIds());
//        pManager.setEntryIds(cManager.getEntryIdsAsString());
//        pManager.save();

        // Nichts tun und sich freuen das alles geklappt hat.
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

    private void buildNotification() {
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

        //API Version prüfen und entsprechend eine Notification mit oder ohne NotificationChannel erstellen
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // The id of the channel.
            String id = "channel_01";
            // The user-visible name of the channel.
            CharSequence name = "Synchronisierung"; //getString(R.string.channel_name);
            // The user-visible description of the channel.
            String description = "Benachrichtigungen rund um Synchronisierungen";
            int importance = 0;
            importance = NotificationManager.IMPORTANCE_DEFAULT;

            NotificationChannel mChannel = new NotificationChannel(id, name, importance);
            // Configure the notification channel.
            mChannel.setDescription(description);
            mChannel.enableLights(true);
            // Sets the notification light color for notifications posted to this
            // channel, if the device supports this feature.
            mChannel.setLightColor(Color.BLUE);
            mChannel.enableVibration(true);
            mChannel.setVibrationPattern(new long[]{100, 200, 100, 200, 100});
            notificationManager.createNotificationChannel(mChannel);

            notification = new Notification.Builder(getApplicationContext(), id)
                    //.setLargeIcon(largeIcon)
                    .setSmallIcon(R.mipmap.ic_icon)
                    .setContentTitle("Synchronisierung")
                    .setContentText("Der lokale Kalender wurde mit dem Server synchronisiert.")
                    .setChannelId(mChannel.getId())
                    .setContentIntent(resultPendingIntent)
                    .setAutoCancel(true)
                    .setStyle(new Notification.BigTextStyle()
                            .bigText("Der lokale Kalender wurde mit dem Server synchronisiert."))
                    .build();

        } else {
            notification = new Notification.Builder(getApplicationContext())
                    //.setLargeIcon(largeIcon)
                    .setSmallIcon(R.mipmap.ic_icon)
                    .setContentTitle("Synchronisierung")
                    .setContentText("Der lokale Kalender wurde mit dem Server synchronisiert.")
                    .setContentIntent(resultPendingIntent)
                    .setAutoCancel(true)
                    .setStyle(new Notification.BigTextStyle()
                            .bigText("Der lokale Kalender wurde mit dem Server synchronisiert."))
                    .build();
        }

        notificationManager.notify(1, notification);
    }

}
