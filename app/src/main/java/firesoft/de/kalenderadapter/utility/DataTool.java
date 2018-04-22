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

import java.io.IOException;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Arrays;

import firesoft.de.kalenderadapter.manager.CalendarManager;
import firesoft.de.kalenderadapter.data.CustomCalendarEntry;
import firesoft.de.kalenderadapter.data.ResultWrapper;
import firesoft.de.kalenderadapter.data.ServerParameter;
import firesoft.de.kalenderadapter.interfaces.IErrorCallback;

public class DataTool extends AsyncTaskLoader<ResultWrapper> {

    //=======================================================
    //=====================VARIABLEN=========================
    //=======================================================

    private ArrayList<ServerParameter> params;
    private IErrorCallback errorCallback;
    private CalendarManager cManager;
    private MutableLiveData<String> progress;


    //=======================================================
    //====================KONSTRUKTOR========================
    //=======================================================

    public DataTool(ArrayList<ServerParameter> params, IErrorCallback errorCallback, Context context, CalendarManager cManager, MutableLiveData<String> progress) {
        super(context);

        this.params = params;
        this.errorCallback = errorCallback;
        this.cManager = cManager;
        this.progress = progress;
    }

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
        int removeIndex = -1;

        for (ServerParameter param: params
                ) {

            switch (param.getKey()) {

                case "url":
                    url = param.getValueAsString();
                    removeIndex = params.indexOf(param);
                    break;

            }

            if (removeIndex > -1) {break;}

        }

        if (removeIndex == -1) {
            InvalidParameterException e = new InvalidParameterException("Keine URL gefunden! (DataTool.loadInBackground)");
            return new ResultWrapper(e);
        }

        params.remove(removeIndex);

        // Anfrage mit dem NetworkTool abschicken und die Antwort abrufen
        try {
            serverResponse = NetworkTool.request(getContext(), url, params, false);
        } catch (IOException e) {
            return new ResultWrapper(e);
        }

        // Die Serverantwort in einzelne Events aufteilen
        ArrayList<String> events = parseServerResponse(serverResponse);
        ArrayList<Integer> eventIds = new ArrayList<>();


        // Den CalendarManager anweisen zu den bestehenden ID's die Einträge zu laden
        cManager.loadCalendarEntries();

        int counter = 0;

        // Marker der angibt, ob auf bereits getätigte Eintragungen geprüft werden muss
        boolean equalityCheckNeeded = true;

        // Die einzelnen Events durchgehen und jeweils einen Kalendereintrag erstellen
        for (String event: events
             ) {
            CustomCalendarEntry entry;


            try {
                entry = CustomCalendarEntry.fromICS(event,cManager.getActiveCalendar().getId());
            } catch (Exception e) {
                return new ResultWrapper(e);
            }

            if (entry != null && entry.getEntryState() != CustomCalendarEntry.EntryState.DECLINED) {
                // Wenn der Eintrag abgelehnt wurde, muss er auch nicht mehr zum Kalender hinzugefügt werden

                int response = addCalenderEntry(entry, equalityCheckNeeded);

                // Antwort auswerten
                switch (response) {
                    case -1:
                        // Irgendwas ist schief gelaufen
                        Exception e = new Exception("Konnte Eintrag nicht erstellen! Eintragsname: " + entry.getTitle() + " am " + entry.getStartMillis() + " (DataTool.loadIngBackground)");
                        return new ResultWrapper(e);

                    case -2:
                        // Eintrag ist schon vorhanden -> nichts tun
                        break;

                    case -3:
                        // Es existieren noch gar keine Einträge -> Es muss nicht weiter geprüft werden
                        equalityCheckNeeded = false;
                        break;

                    default:
                        // Es wurde eine ID zurückgegeben -> Hinzufügen
                        eventIds.add(response);
                        break;
                }

            }

            //errorCallback.publishProgress(counter, events.size(),null);

            counter ++;

            progress.postValue("Fortschritt " + counter + "/" + events.size());

        }

        // Liste mit den Event-IDs zurückgeben
        return new ResultWrapper(eventIds);
    }

    @Override
    protected void onStartLoading() {
        forceLoad();
    }


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

    /**
     * Fügt einen neuen Eintrag in den Kalender ein
     * @param entry Ein Abschnitt eines ICS Strings
     * @param checkIfExists Gibt an, ob vor dem Hinzufügen des Eintrags geprüft werden soll, ob der Eintrag bereits existiert
     * @return -1, falls der Eintrag nicht hinzugefügt wurde, -2 falls der Eintrag schon vorhanden ist, -3 falls keine Vergleichsdaten vorliegen
     */
    private int addCalenderEntry(CustomCalendarEntry entry, boolean checkIfExists) {

        ContentResolver cr = getContext().getContentResolver();

        ContentValues values = new ContentValues();
        values.put(CalendarContract.Events.DTSTART, entry.getStartMillis());
        values.put(CalendarContract.Events.DTEND, entry.getEndMillis());
        values.put(CalendarContract.Events.TITLE, entry.getTitle());
        values.put(CalendarContract.Events.DESCRIPTION, entry.getDescription());
        values.put(CalendarContract.Events.EVENT_LOCATION, entry.getLocation());
        values.put(CalendarContract.Events.CALENDAR_ID, entry.getCalendarID());
        values.put(CalendarContract.Events.EVENT_TIMEZONE, entry.getTimezone());

        Uri uri;
        try {
            uri = cr.insert(CalendarContract.Events.CONTENT_URI, values);
        } catch (SecurityException e) {
            e.printStackTrace();
            errorCallback.publishError("Eine Sicherheitsaußnahme ist aufgertreten! (CalendarManager.addCalenderEntry");
            return -1;
        }

        // Wenn nicht überprüft werden soll, die Einträge hier direkt hinzufügen
        if (!checkIfExists) {
            long eventID = -1;
            if (uri != null) {
                eventID = Long.parseLong(uri.getLastPathSegment());
            }
            return (int) eventID;
        }

        // Prüfen, ob der Eintrag bereits hinzugefügt wurde
        switch (cManager.checkEntryExists(entry)) {

            case EQUAL:
                return -2;

            case UNKNOWN:
                // Eintrag ist neu -> hinzufügen
                long eventID = -1;
                if (uri != null) {
                    eventID = Long.parseLong(uri.getLastPathSegment());
                }
                return (int) eventID;

            case NO_REFERENCE_VALUE:
                return -3;
        }

        return -1;

    }
}
