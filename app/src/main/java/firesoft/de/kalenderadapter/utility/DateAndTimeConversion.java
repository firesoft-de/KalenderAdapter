/*
 * Copyright (c) 2019.  David Schlossarczyk
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

package firesoft.de.kalenderadapter.utility;

import java.text.ParseException;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Calendar.HOUR;

/**
 * Diese Klasse stellt Methoden bereit um Zeitangaben wie bspw. HH:MM in Millisekunden umzurechnen. Es stehen Methoden für absolute Zeiten seit Epoch und relative Zeiten zur Verfügung.
 */
public class DateAndTimeConversion {

    /**
     * Wandelt eine Zeitangabe im Stringformat in einen Wert in Millisekunden um.
     * @param input Zeit mit Stunden und Minutenangabe im Stringformat HH:MM
     * @return Zeit in Millisekunden umgerechnet. Format long
     * @throws ParseException Wird geworfen falls das Format der Zeitangabe fehlerhaft ist oder die Konvertierung fehlgeschlagen ist.
     */
    public static long convertStringToMillis(String input) throws ParseException {
        // Format prüfen
        Pattern p = Pattern.compile("\\d*\\d:\\d*\\d");
        Matcher m = p.matcher(input);

        if (!m.matches()) {
            throw new ParseException("Malformed string!",0);
        }

        // Gefundenen String im validen Format abrufen
        String matchedString = m.group();

        // String aufteilen
        String[] split = matchedString.split(":");

        // Endergebnis berechnen
        return (Integer.getInteger(split[0]) * 60 * 60 + Integer.getInteger(split[1]) * 60) * 1000;
    }


    /**
     * Wandelt eine Zeitangabe in Millisekunden in einen String vom Format HH:mm um.
     * @param input Zeitpunkt in Millisekunden
     * @return String im Format HH:mm
     * @throws ParseException Wird geworfen falls das Format der long-Wert negativ ist die Konvertierung fehlgeschlagen ist.
     */
    public static String convertMillisToString(long input) throws ParseException {

        if (input < 0) {
            throw new ParseException("input smaller then zero",0);
        }

        // Kalender erzeugen. Dieser wird verwendet um zu überprüfen ob der eingegebene Zeitpunkt am heutigen Tag bereits vergangen ist (Vergangenheitsprüfung).
        GregorianCalendar checkCalendar = new GregorianCalendar();
        checkCalendar.setTimeInMillis(input);
        checkCalendar.setTimeZone(TimeZone.getTimeZone("GMT"));

        int a = checkCalendar.get(HOUR);
        int b = checkCalendar.get(Calendar.MINUTE);

        return String.format(Locale.GERMAN, "%1$tH:%1$tM", checkCalendar);

    }

    /**
     * Wandelt eine Zeitangabe in Millisekunden in einen String vom Format HH:mm um.
     * @param hour Zeitangabe Stundenteil
     * @param minute Zeitangabe Minutenteil
     * @return Zeit in Millisekunden umgerechnet. Format long
     * @throws ParseException Wird geworfen falls das Format der long-Wert negativ ist die Konvertierung fehlgeschlagen ist.
     */
    public static long convertHourAndMinuteToMillis(int hour, int minute) throws ParseException {

        if (hour < 0 || minute < 0 || hour > 24 || minute > 60) {
            throw new ParseException("input smaller than zero or bigger than allowed!",0);
        }

        return (hour * 60 * 60 + minute * 60) * 1000;
    }

    /**
     * Fügt die Zeit in Millisekunden seit dem Epoch zur Eingabe hinzu. Kann verwendet werden, um den Startzeitpunkt des Service zu erzeugen. Es wird berücksichtigt, ob der eingegebene Zeitpunkt am heutigen Tag bereits in der Vergangenheit liegt. In diesem Fall wird ein Tag hinzugefügt (Zeitpunkt liegt dann nicht mehr in der Vergangenheit).
     * @param input Relativer Zeitpunkt am Tag in Millisekunden bspw. 03:00 Uhr morgens -> 10800000
     * @return Absoluter Zeitpunkt der garanitert in der Zukunft liegt. Angabe in Millisekunden seit Epoch
     * @throws ParseException Wird geworfen falls die Eingabe kleiner 0 oder die Konvertierung fehlgeschlagen ist.
     */
    public static long attachEpoch(long input) throws ParseException{

        if (input < 0) {
            throw new ParseException("input smaller then zero",0);
        }

        // Kalender erzeugen. Dieser wird verwendet um zu überprüfen ob der eingegebene Zeitpunkt am heutigen Tag bereits vergangen ist (Vergangenheitsprüfung).
        Calendar checkCalendar = Calendar.getInstance();
        checkCalendar.setTimeInMillis(input);

        Calendar conversionCalendar = Calendar.getInstance();

        // Vergangenheitsprüfung durchführen
        // Stunden prüfen
        if (checkCalendar.get(HOUR) < conversionCalendar.get(HOUR)) {
            // Eingabe liegt in der Vergangenheit -> +1 Tag
            conversionCalendar.add(Calendar.DAY_OF_MONTH,1);
        }
        // Minuten prüfen
        else if (checkCalendar.get(Calendar.MINUTE) < conversionCalendar.get(Calendar.MINUTE)) {
            // Eingabe liegt in der Vergangenheit -> +1 Tag
            conversionCalendar.add(Calendar.DAY_OF_MONTH,1);
        }

        // Uhrzeit auf 00:00:00.00 zurückdrehen
        conversionCalendar.set(HOUR,0);
        conversionCalendar.set(Calendar.MINUTE,0);
        conversionCalendar.set(Calendar.SECOND,0);
        conversionCalendar.set(Calendar.MILLISECOND,0);

        // Eingabe hinzufügen
        conversionCalendar.setTimeInMillis(conversionCalendar.getTimeInMillis() + input);

        return conversionCalendar.getTimeInMillis();
    }
}
