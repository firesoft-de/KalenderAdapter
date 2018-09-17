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

import android.arch.lifecycle.Lifecycle;
import android.arch.lifecycle.LifecycleOwner;
import android.arch.lifecycle.MutableLiveData;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.provider.CalendarContract;
import android.support.annotation.NonNull;
import android.support.v4.content.AsyncTaskLoader;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.Observable;
import java.util.Observer;

import firesoft.de.kalenderadapter.data.CustomCalendarEntry;
import firesoft.de.kalenderadapter.data.ResultWrapper;
import firesoft.de.kalenderadapter.data.ServerParameter;
import firesoft.de.kalenderadapter.interfaces.IErrorCallback;
import firesoft.de.kalenderadapter.manager.CalendarManager;
import firesoft.de.kalenderadapter.manager.PreferencesManager;
import firesoft.de.libfirenet.authentication.Digest;
import firesoft.de.libfirenet.http.HttpWorker;
import firesoft.de.libfirenet.interfaces.ICallback;
import firesoft.de.libfirenet.method.GET;
import firesoft.de.libfirenet.util.HttpState;

public class DataTool extends AsyncTaskLoader<ResultWrapper> implements IErrorCallback {

    //=======================================================
    //=====================VARIABLEN=========================
    //=======================================================

    private ArrayList<ServerParameter> params;
    private CalendarManager cManager;
    private MutableLiveData<String> progress;
    private boolean managed;
    private PreferencesManager pManager;

    //=======================================================
    //=====================KONSTANTEN========================
    //=======================================================

    private final String reminder_two_days = String.valueOf(2*24*60); // 2 Tag * 24 Stunden * 60 Minuten
    private final String reminder_one_days = String.valueOf(24*60); // 1 Tag * 24 Stunden * 60 Minuten
    private final String early_reminder = String.valueOf(7*24*60); // 7 Tage * 24 Stunden * 60 Minuten

    //=======================================================
    //====================KONSTRUKTOR========================
    //=======================================================

    /**
     * Instanziert ein neues DataToolkit
     * @param params Parametersatz
     * @param context Context in dem der Loader läuft
     * @param cManager Ein CalendarManager
     * @param progress Callback für Fortschrittsberichte an den Nutzer
     * @param managed Gibt an, ob der Loader durch einen Manager verwaltet wird. True = verwaltet, false = eigenständig (aktiviert oder deaktiviert forceLoad())
     */
    public DataTool(ArrayList<ServerParameter> params, Context context, CalendarManager cManager, MutableLiveData<String> progress, PreferencesManager pManager, boolean managed) {
        super(context);

        this.params = params;
        this.cManager = cManager;
        this.progress = progress;
        this.managed = managed;
        this.pManager = pManager;

        cManager.redefineErrorCallback(this);

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
        String user = null;
        String pass = null;

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

            // Anfrage ausführen
            worker.excecute();
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | IOException | NoSuchMethodException e)
        {
            return new ResultWrapper(e);
        }


        // Serverantwort abrufen
        assert worker != null;
        serverResponse = worker.toString();

        if (serverResponse == null || serverResponse.equals("")) {
            // Laut Status stimmt irgendwas nicht. -> Fehlermeldung werfen
            return new ResultWrapper(new Exception("Download fehlgeschlagen! HTTP-Response: " + Objects.requireNonNull(worker.getResponseCode().getValue()).toString()));
        }

        // Verbindung trennen
        worker.disconnect();

        // Die Serverantwort in einzelne Events aufteilen
        ArrayList<String> events = parseServerResponse(serverResponse);
        ArrayList<Integer> eventIds = new ArrayList<>();

        // Marker der angibt, ob auf bereits getätigte Eintragungen geprüft werden muss
        boolean equalityCheckNeeded;

        // Prüfen, ob die bestehenden Einträge überschrieben werden sollen. In diesem Fall können jetzt alle Einträge gelöscht und die neuen direkt eingefügt werden. Das ist einfacher, als bei allen zu prüfen, ob sich etwas geändert hat.
        if (pManager.isReplaceExistingActivated()) {

            // Prüfen, ob bereits Einträge geladen wurden. Falls dies nicht der Fall ist, sollte dies jetzt nachgeholt werden. Es kann sonst zu Fehlern in .deleteEntries() kommen.
            if (cManager.getEntryIds() == null ||cManager.getEntryIds().size() == 0) {
                cManager.loadCalendarEntries();
            }

            // Alle bestehenden Einträge löschen
            cManager.deleteEntries();
            pManager.setEntryIds("");
            equalityCheckNeeded = false;
        }
        else {
            // Einträge bleiben bestehen, es werden nur neue Einträge hinzugefügt
            // Den CalendarManager anweisen zu den bestehenden ID's die Einträge zu laden
            cManager.loadCalendarEntries();
            equalityCheckNeeded = true;
        }

        int counter = 0;

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

                int response = addCalenderEntry(entry, equalityCheckNeeded, pManager.isReminderActivated(),pManager.isInteligentReminderActivated());

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

                        // Ersten Eintrag nochmal hinzufügen, da er bei der Antwort -3 nicht bearbeitet wurde
                        eventIds.add(addCalenderEntry(entry, false, pManager.isReminderActivated(),pManager.isInteligentReminderActivated()));
                        break;

                    default:
                        // Es wurde eine ID zurückgegeben -> Hinzufügen
                        eventIds.add(response);
                        break;
                }

            }

            counter ++;

            publishProgress("Fortschritt " + counter + "/" + events.size());

        }

        // Liste mit den Event-IDs zurückgeben
        return new ResultWrapper(eventIds);
    }

    @Override
    protected void onStartLoading() {
        //if (managed) { // Kann so nicht stehen bleiben. Ohne forceLoad() wird der enthaltene Code überhaupt nicht ausgeführt :/
            forceLoad();
        //}
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
     * @param setReminder Gibt an, ob für die Einträge eine Erinnerung hinzugefügt werden soll (falls keine andere eingetragen wurde)
     * @param useInteligentReminder Gibt an, ob die Erinnerungen in Abhängigkeit des (Rückmelde-)Status gesetzt werden sollen
     * @return -1, falls der Eintrag nicht hinzugefügt wurde, -2 falls der Eintrag schon vorhanden ist, -3 falls keine Vergleichsdaten vorliegen
     */
    private int addCalenderEntry(CustomCalendarEntry entry, boolean checkIfExists, boolean setReminder, boolean useInteligentReminder) {

        ContentResolver cr = getContext().getContentResolver();

        ContentValues values = new ContentValues();
        values.put(CalendarContract.Events.DTSTART, entry.getStartMillis());
        values.put(CalendarContract.Events.DTEND, entry.getEndMillis());
        values.put(CalendarContract.Events.TITLE, entry.getTitle());
        values.put(CalendarContract.Events.DESCRIPTION, entry.getDescription());
        values.put(CalendarContract.Events.EVENT_LOCATION, entry.getLocation());
        values.put(CalendarContract.Events.CALENDAR_ID, entry.getCalendarID());
        values.put(CalendarContract.Events.EVENT_TIMEZONE, entry.getTimezone());

        // Prüfen, ob der Eintrag bereits hinzugefügt wurde
        if (checkIfExists) { // Wenn auf vorhandensein geprüft werden soll. Ansonsten wird übersprungen
            switch (cManager.checkEntryExists(entry)) {

                case EQUAL:
                    return -2;

                case UNKNOWN:
                    // Der Eintrag ist unbekannt. Also wird hier nichts getan und die Switch Anweisung übersprungen
                    break;

                case NO_REFERENCE_VALUE:
                    return -3;
            }
        }

        // Eintrag in den Kalender einfügen
        Uri uri;
        try {
            uri = cr.insert(CalendarContract.Events.CONTENT_URI, values);
        } catch (SecurityException e) {
            e.printStackTrace();
            publishError("Eine Sicherheitsausnahme ist aufgertreten! (CalendarManager.addCalenderEntry");
            return -1;
        }

        // ID des letzten Eintrags abrufen
        int eventID = -1;
        if (uri != null) {
            eventID = Integer.parseInt(uri.getLastPathSegment());
        }

        // Erinnerungen hinzufügen, falls gewünscht
        // Basierend auf https://www.quora.com/How-do-I-programmatically-set-a-reminder-message-for-a-particular-date-in-Android
        if (setReminder && uri != null) {

            // Erinnerung erstellen und die ID des zugehörigen Events eintragen
            ArrayList<ContentValues> reminders = new ArrayList<>();
             // Erinnerungsart einfügen. Es wird nur DEFAULT und ALARM unterstützt.

            // Welche Art von Erinnerung soll hinzugefügt werden? Standardmäßig oder intelligent?
            if (useInteligentReminder) {
                ContentValues reminder;

                switch (entry.getEntryState()) {
                    case OPEN:
                        // Erinnerung eine Woche zwei Tage und einen Tag vorher
                        reminder = createNewReminder(eventID);
                        reminder.put(CalendarContract.Reminders.MINUTES,early_reminder);
                        reminders.add(reminder);
                        // Kein break, da die nachfolgenden Einträge auch benötigt werden!

                    case CONFIRMED:
                        // Erinnerung zwei und einen Tage vorher reicht aus
                        reminder = createNewReminder(eventID);
                        reminder.put(CalendarContract.Reminders.MINUTES,reminder_two_days);
                        reminders.add(reminder);

                        reminder = createNewReminder(eventID);
                        reminder.put(CalendarContract.Reminders.MINUTES,reminder_one_days);
                        reminders.add(reminder);
                        break;
                }
            }
            else {
                // Einfache Erinnerung zwei Tage vorher hinzufügen
                ContentValues reminder = createNewReminder(eventID);
                reminder.put(CalendarContract.Reminders.MINUTES,reminder_two_days);
                reminders.add(reminder);
            }

            // Alle Erinnerungen hinzufügen
            for (ContentValues reminder: reminders
                 ) {
                try {
                    cr.insert(CalendarContract.Reminders.CONTENT_URI, reminder);
                } catch (SecurityException e) {
                    e.printStackTrace();
                    publishError("Eine Sicherheitsausnahme ist aufgetreten! (CalendarManager.addCalenderEntry (Reminders)");
                    return -1;
                }
            }

        }

        return eventID;

    }

    /**
     * Hilfsmethode die einen neuen ContentValue erzeugt der einen Reminder enthält
     */
    private ContentValues createNewReminder(int entryId) {
        ContentValues reminder = new ContentValues();
        reminder.put(CalendarContract.Reminders.EVENT_ID, entryId);
        reminder.put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_DEFAULT);
        return reminder;
    }

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
    public void publishProgress(String message) {
        progress.postValue(message);
    }

    @Override
    public void appendProgress(String message) {
        progress.postValue(progress.getValue() + " - " + message);
    }
}
