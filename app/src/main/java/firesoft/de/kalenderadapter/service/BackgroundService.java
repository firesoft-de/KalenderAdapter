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

import android.app.Service;
import android.arch.lifecycle.MutableLiveData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.Loader;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;

import firesoft.de.kalenderadapter.BuildConfig;
import firesoft.de.kalenderadapter.data.ResultWrapper;
import firesoft.de.kalenderadapter.data.ServerParameter;
import firesoft.de.kalenderadapter.interfaces.IErrorCallback;
import firesoft.de.kalenderadapter.manager.AsyncTaskManager;
import firesoft.de.kalenderadapter.manager.CalendarManager;
import firesoft.de.kalenderadapter.manager.PreferencesManager;
import firesoft.de.kalenderadapter.utility.DataTool;

public class BackgroundService extends Service implements IErrorCallback, Loader.OnLoadCompleteListener<ResultWrapper>{

    PreferencesManager pManager;
    CalendarManager cManager;
    DataTool dataTool;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        AsyncTaskManager taskManager;

        if (BuildConfig.DEBUG) {
            Log.d("LOG_SERVICE", "Service run!");
        }

        // PreferencesManager starten
        pManager = new PreferencesManager(getApplicationContext());
        try {
            pManager.load();
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        // CalendarManager starten
        cManager = new CalendarManager(getApplicationContext(),this);
        cManager.setActiveCalendar(pManager.getActiveCalendarId());

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

        // Kommunikationskanal mit Backgroundthreads. Wird im BackgroundService nicht benötigt. Muss aber dem DataTool mitgegeben werden.
        MutableLiveData<String> messageFromBackground = new MutableLiveData<>();

        dataTool = new DataTool(parameters,this,getApplicationContext(),cManager,messageFromBackground, false);

        // Basierend auf https://stackoverflow.com/questions/8696146/can-you-use-a-loadermanager-from-a-service/24393728
        dataTool.registerListener(1,this); // 1 = Marker für MainLoader (im AsyncTaskManager definiert)
        dataTool.startLoading();



        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void publishError(String message) {
        // Nichts tun
    }

    @Override
    public void publishProgress(String message) {
        // Nichts tun
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

        // Kein Fehler ist aufgetreten. Die IDs werden in den Preferences Manager geschrieben und gespeichert.
        cManager.setEntryIds(data.getIds());
        pManager.setEntryIds(cManager.getEntryIdsAsString());
        pManager.save();

        // Nichts tun und sich freuen das alles geklappt hat.
    }

    @Override
    public void onDestroy() {

        // Loader stoppen
        if (dataTool != null) {
            dataTool.unregisterListener(this);
            dataTool.cancelLoad();
            dataTool.stopLoading();
        }

    }


}
