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

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.provider.CalendarContract.*;

import java.util.ArrayList;

import firesoft.de.kalenderadapter.data.CustomCalendar;
import firesoft.de.kalenderadapter.data.CustomCalendarEntry;
import firesoft.de.kalenderadapter.interfaces.IErrorCallback;

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
    //=====================KONSTRUKTOR=======================
    //=======================================================


    /**
     * Erstellt eine neue Instanz der Klasse
     * @param context Eine Kopie des Context
     */
    public CalendarManager(Context context, IErrorCallback errorCallback) {
        this.context = context;
        cals = new ArrayList<>();
        this.errorCallback = errorCallback;
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
    private static final String[] CALENDAR_PROJECTION = new String[] {
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
    private static final String[] EVENT_PROJECTION = new String[] {
            Events._ID,                         // 0
            Events.DESCRIPTION,                 // 1
            Events.DTSTART,                     // 2
            Events.DTEND,                       // 3
            Events.TITLE,                 // 1
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

        for (CustomCalendar cal: cals
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

        for (CustomCalendar cal: cals
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
        // TODO: Filterung hinzufügen, damit nur noch relevante Einträge die durch die App getätigt wurden geladen werden und keine durch den Benutzer generierten Einträge
        // Erstellt mit https://www.grokkingandroid.com/androids-calendarcontract-provider/

        ArrayList<CustomCalendarEntry> entries = new ArrayList<>();

        ContentResolver cr = context.getContentResolver();

        if (entryIds.size() == 0) {
            return null;
        }

        for (Integer id: entryIds
                ) {

            // Cursor für den Datenabruf erstellen. Es wird anhand der ID (welche in dem Array mitgeliefert wird) nach einem Event gesucht
            Cursor cur = cr.query(CalendarContract.Events.CONTENT_URI, EVENT_PROJECTION, Events._ID + " = ?",  new String[]{Integer.toString(id)},
                    null);

            if (cur != null && cur.moveToFirst()) {

                // Das erste Suchergebnis abrufen und ein neues Entry-Objekt erstellen
                CustomCalendarEntry entry = new CustomCalendarEntry();
                entry.setCalendarID(cur.getInt(EVENT_ID_INDEX));
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
        return entries;
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
                cals.add(new CustomCalendar((int) calID,displayName,accountName,ownerName));

            }

            // Cursor schließen
            cur.close();
            return resultList;
        }

        // Es wurden keine Elemente gefunden -> Fehlermeldung ausgeben
        errorCallback.publishError("Es konnten keine Kalender gefunden werden.");
        return null;
    }

    /**
     * Löscht die übergebenen Eventids aus dem aktiven Kalender
     */
    public void deleteEntries() {

        int counter = 0;

        if (entryIds.size() != 0) {

            for (int id: entryIds
                 ) {

                // https://developer.android.com/guide/topics/providers/calendar-provider.html#attendees
                ContentResolver cr = context.getContentResolver();
                Uri deleteUri = ContentUris.withAppendedId(Events.CONTENT_URI, id);
                int rows = cr.delete(deleteUri, null, null);

                counter ++;

                errorCallback.publishProgress(String.valueOf(counter) + "/" +  String.valueOf(entryIds.size()) + " gelöscht");

            }
        }

        entryIds.clear();

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
                return Equality.EQUAL;
            }

        }

        return Equality.UNKNOWN;

    }

    /**
     * Public Schnittstelle um eine Liste mit den bereits eingetragenen Einträgen zu laden
     */
    public void loadCalendarEntries() {
        crowd = getExistingEntries();
    }

    //=======================================================
    //=======================GET/SET=========================
    //=======================================================

    public CustomCalendar getActiveCalendar() {
        return activeCalendar;
    }

    public ArrayList<Integer> getEntryIds() {
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

    }
}

