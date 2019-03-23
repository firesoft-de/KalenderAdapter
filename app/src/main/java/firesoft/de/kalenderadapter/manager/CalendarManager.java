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

package firesoft.de.kalenderadapter.manager;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Events;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

import java.util.ArrayList;

import firesoft.de.kalenderadapter.BuildConfig;
import firesoft.de.kalenderadapter.R;
import firesoft.de.kalenderadapter.data.CustomCalendar;
import firesoft.de.kalenderadapter.data.CustomCalendarEntry;
import firesoft.de.kalenderadapter.interfaces.IErrorCallback;

import static firesoft.de.kalenderadapter.data.CustomCalendarEntry.TimeComparison.HIGHER;
import static firesoft.de.kalenderadapter.data.CustomCalendarEntry.TimeComparison.LOWER;
import static firesoft.de.kalenderadapter.manager.CalendarManager.Equality.EQUAL;

/**
 * Stellt eine Klasse zum Verwalten des aktiven Kalenders und seiner Einträge bereit
 */
public class CalendarManager {

    //=======================================================
    //======================VARIABLEN========================
    //=======================================================

    private Context context;

    private ArrayList<CustomCalendar> cals;

    private IErrorCallback errorCallback;

    private CustomCalendar activeCalendar;

    private ArrayList<Integer> entryIds;

    // Enthält bereits hinzugefügte Einträge
    private ArrayList<CustomCalendarEntry> crowd;

    //=======================================================
    //=====================KONSTANTEN========================
    //=======================================================

    private final String reminder_two_days = String.valueOf(2*24*60); // 2 Tag * 24 Stunden * 60 Minuten
    private final String reminder_one_days = String.valueOf(24*60); // 1 Tag * 24 Stunden * 60 Minuten
    private final String early_reminder = String.valueOf(7*24*60); // 7 Tage * 24 Stunden * 60 Minuten

    public static final String MARKER_FOR_ORGANIZER = "kalenderadapter@firesoft.de";

    //=======================================================
    //=====================KONSTRUKTOR=======================
    //=======================================================


    /**
     * Erstellt eine neue Instanz der Klasse
     * @param context Eine Kopie des Context
     */
    public CalendarManager(Context context, @Nullable IErrorCallback errorCallback) {
        this.context = context;
        cals = new ArrayList<>();
        this.errorCallback = errorCallback;
        entryIds = new ArrayList<>();
    }

    //=======================================================
    //========================ENUMS==========================
    //=======================================================

    /**
     * EQUAL = Einträge sind gleich, LATEST = Kandidat ist neuer, UNKNOWN = Kandidat ist noch nicht vorhanden, NO_REFERENCE_VALUE = Keine Vergleichsbasis vorhanden
     */
    public enum Equality {
        EQUAL,
        LATEST,
        NO_REFERENCE_VALUE,
        UNKNOWN
    }

    //=======================================================
    //=====================KONSTANTEN========================
    //=======================================================

    // Projection array. Creating indices for this array instead of doing
    // dynamic lookups improves performance.
    private static final String[] CALENDAR_PROJECTION = new String[]{
            CalendarContract.Calendars._ID,                           // 0
            CalendarContract.Calendars.ACCOUNT_NAME,                  // 1
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,         // 2
            CalendarContract.Calendars.OWNER_ACCOUNT                  // 3
    };

    // The indices for the projection array above.
    private static final int PROJECTION_ID_INDEX = 0;
    private static final int PROJECTION_ACCOUNT_NAME_INDEX = 1;
    private static final int PROJECTION_DISPLAY_NAME_INDEX = 2;
    private static final int PROJECTION_OWNER_ACCOUNT_INDEX = 3;


    /**
     * Projektionsarray für den Eventabruf. Wird genutzt um die gewünschten Daten über die Events zu erhalten
     */
    private static final String[] EVENT_PROJECTION = new String[]{
            Events._ID,                         // 0
            Events.DESCRIPTION,                 // 1
            Events.DTSTART,                     // 2
            Events.DTEND,                       // 3
            Events.TITLE,                       // 4
    };

    /**
     * Projektionsarray für den Eventabruf. Wird genutzt um die gewünschten Daten über die Events zu erhalten
     */
    private static final String[] EVENT_PROJECTION_WITH_ORGANIZER = new String[]{
            Events._ID,                         // 0
            Events.ORGANIZER,                   // 1
            Events.DESCRIPTION,                 // 2
            Events.DTSTART,                     // 3
            Events.DTEND,                       // 4
            Events.TITLE,                       // 5
    };


    // The indices for the projection array above.
    private static final int EVENT_ID_INDEX = 0;
    private static final int EVENT_DESCRIPTION = 1;
    private static final int EVENT_DTSTART = 2;
    private static final int EVENT_DTEND = 3;
    private static final int EVENT_TITLE = 4;

    //=======================================================
    //==================PRIVATE METHODEN=====================
    //=======================================================

    /**
     * Findet einen Kalender anhand des Kalendernamens
     * @param name Der Name des gesuchten Kalenders
     * @return Der gefundene Kalender oder null, falls kein passender Kalender gefunden wurde
     */
    private CustomCalendar findCalendarByName(String name) {

        // Falls noch keine Kalender geladen wurden, sollte dies jetzt nachgeholt werden.
        if (cals.size() == 0) {
            getCalendars();
        }

        // Die vorhandenen Kalender durchsuchen und den Kalender mit dem gesuchten Namen ausgeben.
        for (CustomCalendar cal : cals
                ) {
            if (cal.getDisplayName().equals(name)) {
                return cal;
            }
        }
        return null;
    }

    /**
     * Findet einen Kalender anhand der ID
     * @param id Id welche gesucht wird
     * @return Der gefundene Kalender oder null, falls kein passender Kalender gefunden wurde
     */
    private CustomCalendar findCalenderById(int id) {

        // Falls noch keine Kalender geladen wurden, sollte dies jetzt nachgeholt werden.
        if (cals.size() == 0) {
            getCalendars();
        }

        // Die vorhandenen Kalender durchsuchen und den Kalender mit der gesuchten ID ausgeben.
        for (CustomCalendar cal : cals
                ) {
            if (cal.getId() == id) {
                return cal;
            }
        }
        return null;
    }

    /**
     * Ruft aus dem momentan aktiven Kalender die bestehenden Einträge ab
     * @return Null falls keine Einträge in der internen ID-Liste hinterlegt sind, ansonsten eine Liste mit den, für einen Vergleich notwendigen, Eintragsdaten
     */
    private ArrayList<CustomCalendarEntry> getExistingEntries() throws SecurityException {
        // Erstellt mit https://www.grokkingandroid.com/androids-calendarcontract-provider/

        ArrayList<CustomCalendarEntry> entries = new ArrayList<>();

        // Wenn keine ID's gespeichert wurden, können Einträge nur über den Marker gefunden werden
        if (entryIds.size() == 0) {
            return getExistingEntriesByMarker();
        }

        for (Integer id : entryIds
                ) {

            // Cursor für den Datenabruf erstellen. Es wird anhand der ID (welche in dem Array mitgeliefert wird) nach einem Event gesucht
            // Es wird zusätzlich die Spalte Deleted geprüft. Wenn diese = 1, dann wurde der Eintrag bereits gelöscht
            Cursor cur = context.getContentResolver().query(CalendarContract.Events.CONTENT_URI, EVENT_PROJECTION, "(" + Events._ID + " = ?) AND (" + Events.CALENDAR_ID + " = " + activeCalendar.getId() + " AND " + Events.DELETED + " = 0)", new String[]{Integer.toString(id)},
                    null);

            if (cur != null && cur.moveToFirst()) {

                // Das erste Suchergebnis abrufen und ein neues Entry-Objekt erstellen
                CustomCalendarEntry entry = new CustomCalendarEntry();
                entry.setEntryID(cur.getInt(EVENT_ID_INDEX));
                entry.setDescription(cur.getString(EVENT_DESCRIPTION));

                String tmpString = cur.getString(EVENT_DTSTART);
                long tmpInt = Long.valueOf(tmpString);

                entry.setStartMillis(tmpInt);
                entry.setEndMillis(Long.valueOf(cur.getString(EVENT_DTEND)));
                entry.setTitle(cur.getString(EVENT_TITLE));

                cur.close();

                // Eintrag zur Liste hinzufügen
                entries.add(entry);
            }
        }

        entries = mergeEntryLists(getExistingEntriesByMarker(), entries);

        return entries;
    }

    /**
     * Findet Einträge die mit dem spezifischen Marker der App im Feld ORGANIZER versehen wurden.
     * @return Liste die auch alle zusätzlich gefundenen Einträge enthält
     */
    private ArrayList<CustomCalendarEntry> getExistingEntriesByMarker() throws SecurityException {

        ArrayList<CustomCalendarEntry> entries = new ArrayList<>();

        // Cursor für den Datenabruf erstellen. Es wird anhand der ID (welche in dem Array mitgeliefert wird) nach einem Event gesucht
        // Es wird zusätzlich die Spalte Deleted geprüft. Wenn diese = 1, dann wurde der Eintrag bereits gelöscht
        Cursor cur = context.getContentResolver().query(CalendarContract.Events.CONTENT_URI, EVENT_PROJECTION, "(" + Events.ORGANIZER + " = ? AND " + Events.DELETED + " = 0)", new String[]{MARKER_FOR_ORGANIZER},
                null);

        boolean moreItemsAvailable = cur.moveToFirst();

        while (moreItemsAvailable) {

            // Das Suchergebnis abrufen und ein neues Entry-Objekt erstellen
            CustomCalendarEntry entry = new CustomCalendarEntry();
            entry.setEntryID(cur.getInt(EVENT_ID_INDEX));
            entry.setDescription(cur.getString(EVENT_DESCRIPTION));

            String tmpString = cur.getString(EVENT_DTSTART);
            long tmpInt = Long.valueOf(tmpString);

            entry.setStartMillis(tmpInt);
            entry.setEndMillis(Long.valueOf(cur.getString(EVENT_DTEND)));
            entry.setTitle(cur.getString(EVENT_TITLE));

            entries.add(entry);

            moreItemsAvailable = cur.moveToNext();

        }

        return entries;

    }

    /**
     * Fügt zwei Listen zusammen 
     * @param ListA Erste Liste als ArrayList<CustomCalendarEntry>
     * @param ListB Zweite Liste als ArrayList<CustomCalendarEntry>
     * @return Ergebnisliste als ArrayList<CustomCalendarEntry>
     */
    private ArrayList<CustomCalendarEntry> mergeEntryLists(ArrayList<CustomCalendarEntry> ListA, ArrayList<CustomCalendarEntry> ListB) {

        // Inhalt von Liste A wird direkt in die Ausgabeliste geschrieben
        ArrayList<CustomCalendarEntry> mergedList = ListA;

        // Alle Einträge in der Liste B durchgehen und prüfen, ob diese bereits in der Ausgabeliste enthalten sind
        for (CustomCalendarEntry entryFromListB: ListB
             ) {

            // Binärsuche nach einem gleichen Eintrag durchführen
            binarySearch(mergedList,0,mergedList.size() - 1,entryFromListB);

            /*boolean allreadyExistsInMergedList = false;

            for (int i = 0; i < mergedList.size(); i++) {
                if (entryFromListB.equals(mergedList.get(i))) {
                    allreadyExistsInMergedList = true;
                    i = mergedList.size();
                }
            }

            if (!allreadyExistsInMergedList) {
                mergedList.add(entryFromListB);
            }*/

        }
        
        return mergedList;
    }

    /**
     * Führt eine Binärsuche nach einem CustomCalendarEntry anhand der Startzeiten in einer ArrayList aus
     */
    private void binarySearch(ArrayList<CustomCalendarEntry> list, int start, int end, CustomCalendarEntry candidate) {

        // Abkürzung: Wenn die Startzeit des Kandidaten kleiner oder größer als der erste bzw. letzt Wert der List ist, kann dieser direkt hinzugefügt werden.
        if (list.get(0).compareStartTime(candidate) == LOWER) {
            list.add(0,candidate);
            return;
        }
        else if (list.get(list.size()-1).compareStartTime(candidate) == HIGHER) {
            list.add(candidate);
            return;
        }


        int pos = start + (end - start) / 2;
        boolean lastStep = false;

        // Prüfen, ob der aktuelle Wert direkt zwischen den beiden vorherigen liegt
        if (pos == start) {
            //Marker setzen. Dieser gibt an die nachfolgende Überprüfung den Hinweis ggf. jetzt einen Wert in die Liste einzufügen.
            lastStep = true;
        }


        switch (list.get(pos).compareStartTime(candidate)) {

            case HIGHER:
                if (lastStep) {
                    list.add(pos,candidate);
                }
                else {
                    binarySearch(list, pos + 1, end, candidate);
                }
                break;

            case EQUAL:
                // Beide Einträge vergleichen und nur ggf. hinzufügen.
                if (!list.get(pos).equals(candidate)) {
                    // Sind nicht gleich -> Hinzufügen
                    list.add(pos,candidate);
                }
                break;

            case LOWER:
                if (lastStep) {
                    list.add(pos - 1,candidate);
                }
                else {
                    binarySearch(list, start, pos - 1, candidate);
                }
                break;

        }


    }


    //=======================================================
    //==================ÖFFENTLICHE METHODEN=================
    //=======================================================

    /**
     * Setzt anhand des Kalendernames den aktiven Kalender
     */
    public void setActiveCalendar(String name) {
        activeCalendar = findCalendarByName(name);
    }

    /**
     * Setzt anhand einer ID den aktiven Kalender
     */
    public void setActiveCalendar(int id) {
        activeCalendar = findCalenderById(id);
    }

    /**
     * Ruft die verfügbaren Kalender ab
     * @return Eine Liste mit den Namen der Kalender
     */
    public ArrayList<String> getCalendars() {

        Cursor cur;
        ArrayList<String> resultList = new ArrayList<>();

        ContentResolver cr = context.getContentResolver();
        Uri uri = CalendarContract.Calendars.CONTENT_URI;

        try {
            // Daten abrufen
            cur = cr.query(uri, CALENDAR_PROJECTION, null, null, null);
        } catch (SecurityException e) {
            e.printStackTrace();
            //errorCallback.publishError("Eine Sicherheitsaußnahme ist aufgetreten! (CalendarManager.getCalendars");
            return null;
        }

        // Prüfung um Fehler zu vermeiden
        if (cur != null && cur.getCount() > 0) {

            // Die Elemente des Cursors Schritt für Schritt durchgehen und dabei die Kalender speichern
            while (cur.moveToNext()) {

                // Variablen vorbereiten
                long calID;
                String displayName;
                String accountName;
                String ownerName;

                // Variablen befüllen
                calID = cur.getLong(PROJECTION_ID_INDEX);
                displayName = cur.getString(PROJECTION_DISPLAY_NAME_INDEX);
                accountName = cur.getString(PROJECTION_ACCOUNT_NAME_INDEX);
                ownerName = cur.getString(PROJECTION_OWNER_ACCOUNT_INDEX);

                // Daten speichern
                resultList.add(displayName);
                cals.add(new CustomCalendar((int) calID, displayName, accountName, ownerName));

            }

            // Cursor schließen
            cur.close();
            return resultList;
        }

        // Es wurden keine Elemente gefunden -> Fehlermeldung ausgeben
        if (errorCallback != null) {
            errorCallback.publishError(context.getString(R.string.error_no_calendars_found));
            errorCallback.switchCalendarUIElements(false);
        }
        return null;
    }

    /**
     * Fügt einen neuen Eintrag in den Kalender ein
     * @param entry Ein Abschnitt eines ICS Strings
     * @param checkIfExists Gibt an, ob vor dem Hinzufügen des Eintrags geprüft werden soll, ob der Eintrag bereits existiert
     * @param setReminder Gibt an, ob für die Einträge eine Erinnerung hinzugefügt werden soll (falls keine andere eingetragen wurde)
     * @param useInteligentReminder Gibt an, ob die Erinnerungen in Abhängigkeit des (Rückmelde-)Status gesetzt werden sollen
     * @return -1, falls der Eintrag nicht hinzugefügt wurde, -2 falls der Eintrag schon vorhanden ist, -3 falls keine Vergleichsdaten vorliegen
     */
    public int addCalenderEntry(CustomCalendarEntry entry, boolean checkIfExists, boolean setReminder, boolean useInteligentReminder) {

        ContentResolver cr = context.getContentResolver();

        ContentValues values = new ContentValues();
        values.put(CalendarContract.Events.DTSTART, entry.getStartMillis());
        values.put(CalendarContract.Events.DTEND, entry.getEndMillis());
        values.put(CalendarContract.Events.TITLE, entry.getTitle());
        values.put(CalendarContract.Events.DESCRIPTION, entry.getDescription());
        values.put(CalendarContract.Events.EVENT_LOCATION, entry.getLocation());
        values.put(CalendarContract.Events.CALENDAR_ID, entry.getEntryID());
        values.put(CalendarContract.Events.EVENT_TIMEZONE, entry.getTimezone());

        // Den Zeitraum des Eintrags als Beschäftigt markieren
        values.put(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_BUSY);

        // Die Spalte ORGANIZER wird als Marker verwendet. Durch diese kann die App erkennen, ob ein Eintrag von ihr angelegt wurde.
        values.put(CalendarContract.Events.ORGANIZER, MARKER_FOR_ORGANIZER);

        // Prüfen, ob der Eintrag bereits hinzugefügt wurde
        if (checkIfExists) { // Wenn auf vorhandensein geprüft werden soll. Ansonsten wird übersprungen
            switch (checkEntryExists(entry)) {

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
//            publishError("Eine Sicherheitsausnahme ist aufgertreten! (CalendarManager.addCalenderEntry");
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

            int attachResponse = attachReminders(entry, cr, eventID, useInteligentReminder);
            if (attachResponse < 0) {
                values.put(CalendarContract.Events.HAS_ALARM, false);
            }
            else {
                values.put(CalendarContract.Events.HAS_ALARM, true);
            }

        }

        return eventID;

    }


    /**
     * Löscht die übergebenen Eventids aus dem aktiven Kalender
     * Anmerkung: Das Löschen als Anwendung (ohne SyncAdapter) führt nur dazu, dass die Spalte "Deleted" auf 1 gesetzt wird. Dies signalisiert einem SyncAdapter, dass die Zeile gelöscht wurde. Der Eintrag bleibt aber in der Datenbank!
     * @return true falls Löschen erfolgreich verlauf ist, ansonsten false. Fehlerausgabe wird direkt durch die Funktion organisiert
     */
    public boolean deleteEntries() {
        int counter = 0;

        // Es sollte immer eine aktuelle Liste gezogen werden
        this.loadCalendarEntries();

        if (BuildConfig.DEBUG) {
            Log.d("LOG_SERVICE", "Deleting: " + getCrowdCount());
        }

        // Falls entryIds == null ist, muss beendet werden, da ansonsten Fehler auftreten.
        if (entryIds == null) {
            return false;
        }

        if (entryIds.size() != 0) {

            for (int id : entryIds
                    ) {

                // https://developer.android.com/guide/topics/providers/calendar-provider.html#attendees
                Uri deleteUri = ContentUris.withAppendedId(Events.CONTENT_URI, id);
                context.getContentResolver().delete(deleteUri, null, null);

                counter++;
                if (errorCallback != null) {
                    errorCallback.publishProgress(String.valueOf(counter) + "/" + String.valueOf(entryIds.size()) + " gelöscht", counter, entryIds.size());
                }
            }
        } else {
            if (errorCallback != null) {
                errorCallback.publishProgress("Keine Termine zum Löschen vorhanden!",1,1);
                return false;
            }
        }

        entryIds.clear();

        // Liste aktualisieren
        this.loadCalendarEntries();

        if (BuildConfig.DEBUG) {
            Log.d("LOG_SERVICE", "Purge completed! Count: " + getCrowdCount());
        }

        return true;

    }

    /**
     * Löscht alle Einträge die im Kalender vorhanden sind.
     */
    public void clearCalendar() {

        ArrayList<Integer> entries = new ArrayList<>();

        // Die ID's der im Kalender vorhandenen Einträge abrufen
        ContentResolver cr = context.getContentResolver();

        // Auf Berechtigung prüfen
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling

            return;
        }

        // Query festlegen nach welchem die Einträge gesucht werden
        String selection = Events.CALENDAR_ID + " = ?";

        // Argumente des Querys definieren
        String[] selectionArgs = new String[] {String.valueOf(activeCalendar.getId())};

        // Cursor bauen, welcher alle Einträge im aktiven Kalender abruft.
        Cursor cur = cr.query(CalendarContract.Events.CONTENT_URI, EVENT_PROJECTION, selection, selectionArgs, null);

        while (cur.moveToNext()) {
            // ID abrufen. Zum leichteren Verständniss ist eine Konstante für den INDEX hinterlegt
            entries.add(cur.getInt(PROJECTION_ID_INDEX));
        }

        // Alle verfügbaren Einträge wurden eingelesen -> Cursor schliessen
        cur.close();

        int counter = 0;

        // Die ausgelesenen ID's löschen
        for (int id: entries
                ) {

            // https://developer.android.com/guide/topics/providers/calendar-provider.html#attendees
            Uri deleteUri = ContentUris.withAppendedId(Events.CONTENT_URI, id);
            int rows = cr.delete(deleteUri, null, null);

            counter++;

            if (errorCallback != null) {
                errorCallback.publishProgress(String.valueOf(counter) + "/" + String.valueOf(entries.size()) + " gelöscht", counter, entries.size());
            }
        }

    }

    /**
     * Prüft, ob ein Kandidat bereits im Kalender vorhanden ist und ob der Kandidat ggf. neuer als der Kalendereintrag ist.
     * @param candidate Kandidat der überprüft werden soll
     * @return Equality.LATEST ist nicht implementiert!
     */
    public Equality checkEntryExists(CustomCalendarEntry candidate) {

        if (crowd == null) {
            return Equality.NO_REFERENCE_VALUE;
        }

        if (crowd.size() == 0) {
            return Equality.NO_REFERENCE_VALUE;
        }

        for (CustomCalendarEntry competitor: crowd
                ) {

            if (competitor.equals(candidate)) {
                return EQUAL;
            }

        }

        return Equality.UNKNOWN;

    }

    /**
     * Public Schnittstelle um eine Liste mit den bereits eingetragenen Einträgen zu laden
     */
    public void loadCalendarEntries() {
        crowd = getExistingEntries();

        if (crowd != null) {

            // entryids einfügen
            for (CustomCalendarEntry entry : crowd
                    ) {
                if (!entryIds.contains(entry.getEntryID())) {
                    entryIds.add(entry.getEntryID());
                }
            }
        }
    }

    //=======================================================
    //=======================GET/SET=========================
    //=======================================================

    public CustomCalendar getActiveCalendar() {
        return activeCalendar;
    }

/*    public ArrayList<Integer> getEntryIds() {
        return entryIds;
    }

    public String getEntryIdsAsString() {

        StringBuilder builder = new StringBuilder();

        for (int entry: entryIds
             ) {
            builder.append(String.valueOf(entry)).append(";");
        }

        return builder.toString();
    }

    public void setEntryIds(ArrayList<Integer> entryIds) {
        // Alle Einträge hinzufügen (ohne Duplikate)

        this.entryIds.removeAll(entryIds);
        this.entryIds.addAll(entryIds);
    }

    public void setEntryIdsFromString(String ids) {

        String[] list = ids.split(";");

        entryIds = new ArrayList<>();

        for (int i = 0; i < list.length - 1; i++) {
            int id = Integer.valueOf(list[i]);
            if (!entryIds.contains(id)) {
                entryIds.add(id);
            }
        }
    }*/

    /**
     * Fügt einem Kalendereintrag Erinnerungen hinzu. Dabei wird die Auswahl des Nutzers berücksichtigt.
     * @param entry Eintrag der hinzugefügt wird
     * @param cr ContentResolver der zum hinzufügen des Eintrags verwendet wird
     * @param eventID ID des zu bearbeitenden Kalendereintrags
     * @param useInteligentReminder Gibt an, ob die Erinnerungen in Abhängigkeit des (Rückmelde-)Status gesetzt werden sollen
     * @return -1 falls es beim Einfügen des Eintrags zu einem Fehler gekommen ist, >= 0 falls erfolgreich
     */
    private int attachReminders(CustomCalendarEntry entry, ContentResolver cr, int eventID, boolean useInteligentReminder) {

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
//                publishError(getContext().getString(R.string.error_addCalendarEntry));
                return -1;
            }
        }

        return 1;

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
     * Bietet die Möglichkeit das Callback Interface zu erneuern. Wird bspw. bei der Übergabe eines Objektes an einen anderen Thread benötigt, da der neue Thread sein eigenes Callback Interface eintragen muss.
     * @param callback Das neue Callback Interface
     */
    public void redefineErrorCallback(IErrorCallback callback) {
        errorCallback = callback;
    }

    public int getCrowdCount() {
        return crowd.size();
    }

}

