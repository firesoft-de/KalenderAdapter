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

package firesoft.de.kalenderadapter.data;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class CustomCalendarEntry {

    //=======================================================
    //======================VARIABLEN========================
    //=======================================================

    private String title;

    private long startMillis;

    private long endMillis;

    private String description;

    private int calendarID;

    private EntryState entryState;

    private String location;

    private String category;

    private String timezone;

    //=======================================================
    //=====================KONSTRUKTOR=======================
    //=======================================================

    /**
     * Erzeugt eine neue Instanz
     * @param calendarID ID des Kalenders in den der Eintrag geschrieben werden soll
     * @param title Titel des Events
     * @param startTime Anfangszeit
     * @param endTime Endzeit
     * @param description Beschreibung
     * @param entryState Status des Events
     * @param location Ort an dem das Event stattfindet
     * @param category Kategorie
     * @throws ParseException Sollte es beim umwandeln der Start- und Endzeit in Unix-Zeit zu einem Fehler kommen, wird eine ParseException geworfen.
     */
    public CustomCalendarEntry(int calendarID, String title, String startTime, String endTime, String description, EntryState entryState, String location, String category, String timezone) throws ParseException{

        Calendar calendar;
        Date date;

        this.calendarID = calendarID;
        this.title = title;
        this.description = description;
        this.entryState = entryState;
        this.location = location;
        this.category = category;
        this.timezone = timezone;

        // Start und Endzeit parsen
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.GERMANY);

        date = format.parse(startTime);
        calendar = Calendar.getInstance();
        calendar.setTime(date);
        startMillis = calendar.getTimeInMillis();

        date  = format.parse(endTime);
        calendar = Calendar.getInstance();
        calendar.setTime(date);
        endMillis = calendar.getTimeInMillis();

    }

    /**
     * Erstellt eine neue Instanz ohne Daten in das Objekt zu füllen
      */
    public CustomCalendarEntry(){

    }

    /**
     * Prüft, ob ein eingegebener Eintrag in Datum und Titel übereinstimmt
     * @return true wenn die überprüften Felder zeichengenau! übereinstimmen, false wenn dies nicht der Fall ist
     */
    public boolean equals(CustomCalendarEntry candidate) {

        boolean checkresult_1 = this.title.equals(candidate.title);
        boolean checkresult_2 = this.description.equals(candidate.description);
        boolean checkresult_3 = (this.startMillis == candidate.startMillis);
        boolean checkresult_4 = (this.endMillis == candidate.endMillis);

        return (checkresult_1 && checkresult_2 && checkresult_3 && checkresult_4);
    }

    //=======================================================
    //===================STATIC METHODS======================
    //=======================================================

    /**
     * Erzeugt auf Basis eines Strings mit ICS Daten einen neuen CalendarEntry
     * @param icsString String mit den Daten der ICS Datei. Der String darf nur einen Eintrag mit oder ohne BEGIN:VEVENT / END:VEVENT enthalten!
     * @param calendarID Die ID des Kalenders, dem der Eintrag zugeordnet wird.
     * @return Null, falls der Eintrag nicht erfolgreich erzeugt werden konnte (es fehlen Titel, Zustand, Start- und Endzeit)
     * @throws ParseException Falls es beim Erstellen des Eintrags zu Fehlern beim Parsen der Zeiten kommt, wird dieser Fehler aus dem Konstruktor weitergeleitet.
     */
    public static CustomCalendarEntry fromICS(String icsString, int calendarID) throws ParseException {

        CustomCalendarEntry result;

        String title = null;
        String description = null;
        String location = null;
        String category = null;
        EntryState entryState = null;
        String startTime = null;
        String endTime = null;
        String timezone = null;

        String[] elements = icsString.split("\n");

        for (String line: elements
             ) {

            // Prüfen, ob das erste Element der ICS Datei heruntergeladen wurde.
            if (line.equals("BEGIN:VCALENDAR")) {
                return null;
            }

            if (!line.equals("")) {
                String argument = line.split(":")[0];
                argument = argument.replace("\t", "");
                String value = line.split(":",2)[1];

                switch (argument) {

                    case "SUMMARY":
                        title = value;
                        break;

                    case "STATUS":
                        switch (value) {
                            case "CONFIRMED":
                                entryState = EntryState.CONFIRMED;
                                break;
                            case "TENTATIVE":
                                entryState = EntryState.OPEN;
                                break;
                        }
                        break;

                    case "CATEGORIES":

                        if (value.contains("\\,")) {
                            category = value.replace("\\,", "-");
                            description = value.replace("\\,", "-");
                        }
                        else {
                            category = value;
                            description = value;
                        }

                        break;

                    case "LOCATION":
                        location = value;
                        break;

                    default:

                        // DTSTART;TZID=Europe/Berlin:20180417T190000
                        // DTSTART;VALUE=DATE:20180517
                        if (argument.contains("DTSTART")) {

                            if (argument.contains("TZID")) {
                                startTime = value;
                                String timezoneRaw = argument.split(";")[1];
                                timezone = timezoneRaw.split("=")[1];
                            }
                            else if (argument.contains("VALUE=")) {
                                startTime = value + "T000000";
                            }

                        }
                        else if (argument.contains("DTEND")) {
                            if (argument.contains("TZID")) {
                                endTime = value;
                            }
                            else if (argument.contains("VALUE=")) {
                                endTime = value + "T000000";
                            }
                        }
                }
            }



        }

        //Zur Description noch den aktuellen Status hinzufügen
        if (entryState != null) {
            switch (entryState) {
                case OPEN:
                    description += "\n" + "Noch keine Rückmeldung abgegeben!";
                    break;

                case DECLINED:
                    description += "\n" + "Termin abgelehnt!";
                    break;

                case CONFIRMED:
                    description += "\n" + "Termin bestätigt!";
                    break;
            }
        }
        else {
            description += "\n" + "Status unbekannt!";
        }

        // Falls keine Zeitzone angegeben wurde, wird diese hier auf Berlin gesetzt
        if (timezone == null) {
            timezone = "Europe/Berlin";
        }

        // Falls keine Endzeit angegeben ist, wird hier die Endzeit auf "00:00" gesetzt
        if (endTime == null) {
            endTime = startTime.split("T")[0] + "T235959" ;
        }

        if (title != null && entryState != null && startTime != null) {
            result = new CustomCalendarEntry(calendarID, title, startTime, endTime, description, entryState, location, category, timezone);
            return result;
        }
        else {
            return null;
        }
    }

    //=======================================================
    //========================ENUMS==========================
    //=======================================================

    /**
     * Beschreibt den Zustand den ein Eintrag einnehmen kann.
     */
    public enum EntryState {
        CONFIRMED,
        DECLINED,
        OPEN
    }

    //=======================================================
    //=======================GETTER==========================
    //=======================================================

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public long getStartMillis() {
        return startMillis;
    }

    public void setStartMillis(long startMillis) {
        this.startMillis = startMillis;
    }

    public long getEndMillis() {
        return endMillis;
    }

    public void setEndMillis(long endMillis) {
        this.endMillis = endMillis;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getCalendarID() {
        return calendarID;
    }

    public void setCalendarID(int calendarID) {
        this.calendarID = calendarID;
    }

    public EntryState getEntryState() {
        return entryState;
    }

    public void setEntryState(EntryState entryState) {
        this.entryState = entryState;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

}
