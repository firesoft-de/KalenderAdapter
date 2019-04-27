/*  Copyright (C) 2018  David Schlossarczyk

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    For the full license visit https://www.gnu.org/licenses/gpl-3.0.*/

package firesoft.de.kalenderadapter.utility;

import android.arch.lifecycle.MutableLiveData;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.provider.CalendarContract;
import android.support.v4.content.AsyncTaskLoader;
import android.util.Log;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

import firesoft.de.kalenderadapter.BuildConfig;
import firesoft.de.kalenderadapter.R;
import firesoft.de.kalenderadapter.data.CustomCalendarEntry;
import firesoft.de.kalenderadapter.data.ResultWrapper;
import firesoft.de.kalenderadapter.data.ServerParameter;
import firesoft.de.kalenderadapter.interfaces.IErrorCallback;
import firesoft.de.kalenderadapter.manager.CalendarManager;
import firesoft.de.kalenderadapter.manager.PreferencesManager;
import firesoft.de.libfirenet.authentication.Digest;
import firesoft.de.libfirenet.http.HttpWorker;
import firesoft.de.libfirenet.method.GET;
import firesoft.de.libfirenet.util.HttpState;

/**
 * Diese Klasse ist als Loader für den Download und das Eintrage der Events zuständig. Über ein IErrorCallback Interface werden Nachrichten an die UI übergeben.
 */
public class DataLoader extends AsyncTaskLoader<ResultWrapper> implements IErrorCallback {

    //=======================================================
    //=====================VARIABLEN=========================
    //=======================================================

    private ArrayList<ServerParameter> params;
    private CalendarManager cManager;
    private MutableLiveData<String> progress;
    private MutableLiveData<Integer> progressValue;
    private MutableLiveData<Integer> progressMax;
    private boolean managed;
    private PreferencesManager pManager;

    //=======================================================
    //====================KONSTRUKTOR========================
    //=======================================================

    /**
     * Instanziert ein neues DataToolkit
     * @param params Parametersatz
     * @param context Context in dem der Loader läuft
     * @param cManager CalendarManager mit dem Kalenderoperationen durchgeführt werden können
     * @param progress Callback für Fortschrittsberichte an den Nutzer
     * @param managed Gibt an, ob der Loader durch einen Manager verwaltet wird. True = verwaltet, false = eigenständig (aktiviert oder deaktiviert forceLoad())
     */
    public DataLoader(ArrayList<ServerParameter> params, Context context, CalendarManager cManager, MutableLiveData<String> progress, MutableLiveData<Integer> progressValue, MutableLiveData<Integer> progressMax, PreferencesManager pManager, boolean managed) {
        super(context);

        this.params = params;
        this.cManager = cManager;
        this.progress = progress;
        this.progressMax = progressMax;
        this.progressValue = progressValue;
        this.managed = managed;
        this.pManager = pManager;

        cManager.redefineErrorCallback(this);

    }

    // region Public Methoden
    //=======================================================
    //==================PUBLIC METHODEN======================
    //=======================================================

    /**
     * Lädt die Daten vom Server
     */
    @Override
    public ResultWrapper loadInBackground() {

        String serverResponse = null;

        String url = null;
        String user = null;
        String pass = null;

        // Variablen für Feedback an Nutzer
        int addedEntries = 0;
        int deletedEntries = 0;

        for (ServerParameter param : params
                ) {

            switch (param.getKey()) {

                case "url":
                    url = param.getValueAsString();
                    break;

                case "user":
                    user = param.getValueAsString();
                    break;

                case "pw":
                    pass = param.getValueAsString();
                    break;

            }

        }

        // Passenden Authenticator erstellen
        Digest authenticator = new Digest(user, pass);

        // Worker Objekt erstellen
        HttpWorker worker = null;
        MutableLiveData<HttpState> stateData;

        try {
            // HTTP-Worker initalisieren
            worker = new HttpWorker(url, GET.class, getContext(), authenticator, null, false, null);

            // Internetverbindung testen
            if (!worker.checkNetwork(getContext())) {

                // Es ist keine Internetverbindung vorhanden
                return new ResultWrapper(new IOException(getContext().getString(R.string.error_no_network)));

            }

            // Anfrage ausführen
            worker.execute();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | IOException | NoSuchMethodException e)
        {
            return new ResultWrapper(e);
        }

        // Serverantwort abrufen
        assert worker != null;
        serverResponse = worker.getResponse(true);

        if (serverResponse == null || serverResponse.equals("")) {
            // Laut Status stimmt irgendwas nicht. -> Fehlermeldung werfen
            return new ResultWrapper(new Exception(getContext().getString(R.string.error_download_failed) + Objects.requireNonNull(worker.getResponseCode().getValue()).toString()));
        }

        // Verbindung trennen
        worker.disconnect();

        // Die Serverantwort in einzelne Events aufteilen
        ArrayList<String> events = parseServerResponse(serverResponse);
        ArrayList<Integer> eventIds = new ArrayList<>();

        // Marker der angibt, ob auf bereits getätigte Eintragungen geprüft werden muss
        boolean equalityCheckNeeded;

        cManager.loadCalendarEntries();
        deletedEntries = cManager.getCrowdCount();

        // Prüfen, ob die bestehenden Einträge überschrieben werden sollen. In diesem Fall können jetzt alle Einträge gelöscht und die neuen direkt eingefügt werden. Das ist einfacher, als bei allen zu prüfen, ob sich etwas geändert hat.
        if (pManager.isReplaceExistingActivated()) {

            // Alle bestehenden Einträge löschen
            cManager.deleteEntries();

            equalityCheckNeeded = false;
        }
        else {
            // Einträge bleiben bestehen, es werden nur neue Einträge hinzugefügt
            // Den CalendarManager anweisen zu den bestehenden ID's die Einträge zu laden
            cManager.loadCalendarEntries();
            equalityCheckNeeded = true;
        }

        int counter = 0;

        // Es kann passieren, dass im ersten Eintrag der events-Liste der "Header" der ical Datei enthalten ist. Jetzt prüfen, ob dies der Fall ist und den Header ggf. entfernen
        if (events.get(0).contains("BEGIN:VCALENDAR")) {
            events.remove(0);
        }

        if (BuildConfig.DEBUG) {
            Log.d("LOG_SERVICE", "Adding: " + events.size());
        }

        addedEntries = events.size();


        // Die einzelnen Events durchgehen und jeweils einen Kalendereintrag erstellen
        for (String event: events
             ) {
            CustomCalendarEntry entry;

            try {
                entry = CustomCalendarEntry.fromICS(event,cManager.getActiveCalendar().getId());
            } catch (Exception e) {
                return new ResultWrapper(e);
            }

            if (entry != null && entry.getEntryState() != CustomCalendarEntry.EntryState.DECLINED && entry.getEntryState() != CustomCalendarEntry.EntryState.CANCELED) {
                // Wenn der Eintrag abgelehnt oder gecancelt wurde, muss er auch nicht mehr zum Kalender hinzugefügt werden

                int response = cManager.addCalenderEntry(entry, equalityCheckNeeded, pManager.isReminderActivated(),pManager.isInteligentReminderActivated());

                // Antwort auswerten
                switch (response) {
                    case -1:
                        // Irgendwas ist schief gelaufen
                        Exception e = new Exception(getContext().getString(R.string.error_failed_to_create_entry) + entry.getTitle() + " @ " + entry.getStartMillis() + " (DataLoader.loadIngBackground)");
                        return new ResultWrapper(e);

                    case -2:
                        // Eintrag ist schon vorhanden -> nichts tun
                        break;

                    case -3:
                        // Es existieren noch gar keine Einträge -> Es muss nicht weiter geprüft werden
                        equalityCheckNeeded = false;

                        // Ersten Eintrag nochmal hinzufügen, da er bei der Antwort -3 nicht bearbeitet wurde
                        eventIds.add(cManager.addCalenderEntry(entry, false, pManager.isReminderActivated(),pManager.isInteligentReminderActivated()));
                        break;

                    default:
                        // Es wurde eine ID zurückgegeben -> Hinzufügen
                        eventIds.add(response);
                        break;
                }

            }

            counter ++;

            publishProgress("Fortschritt " + counter + "/" + events.size(), counter, events.size());

        }

        // Liste mit den Event-IDs zurückgeben
        return new ResultWrapper(eventIds,getContext().getString(R.string.info_import_successfull),deletedEntries,addedEntries);
    }

    @Override
    protected void onStartLoading() {
        //if (managed) { // Kann so nicht stehen bleiben. Ohne forceLoad() wird der enthaltene Code überhaupt nicht ausgeführt :/
            forceLoad();
        //}
    }

    // endregion

    // region Interne Methoden zum Bearbeiten von Kalendereinträgen
    //=======================================================
    //===========METHODEN ZUR DATENVERARBEITUNG==============
    //=======================================================

    /**
     * Parst die Antwort des Servers in eine Stringliste mit den einzelnen Events
     * @param serverResponse Komplette Antwort des Servers (ohne HTTP Header)
     */
    private ArrayList<String> parseServerResponse(String serverResponse) {

        String[] rawEvents = serverResponse.split("BEGIN:VEVENT");

        return new ArrayList<>(Arrays.asList(rawEvents));

    }



    // endregion


    // region Hilfsmethoden für Terminerinnerungen



    // endregion


    // region IErrorCallback-Methoden

    /**
     * Ermöglicht es threadsichere Meldungen an den Nutzer auszugeben
     * @param message Nachricht die angezeigt werden soll
     */
    @Override
    public void publishError(String message) {
        progress.postValue(message);
    }

    /**
     * Ermöglicht es threadsichere Meldungen an den Nutzer auszugeben
     * @param message Nachricht die angezeigt werden soll
     */
    @Override
    public void publishProgress(String message, int progressVal, int progressMx) {
        progress.postValue(message);
        progressMax.postValue(progressMx);
        progressValue.postValue(progressVal);
    }

    @Override
    public void appendProgress(String message) {
        progress.postValue(progress.getValue() + " - " + message);
    }

    @Override
    public void switchCalendarUIElements(boolean enable) {
        // Hier wird nichts gemacht
    }

    // endregion
}
