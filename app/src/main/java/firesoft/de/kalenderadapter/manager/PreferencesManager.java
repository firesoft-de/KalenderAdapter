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


import android.app.AlarmManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import java.util.Date;

/**
 * Manager Klasse mit der die gespeicherten Einstellungen zentrak verwaltet und bereitgestellt werden
 */
public class PreferencesManager {

    //=======================================================
    //=================INTERNE VARIABLEN=====================
    //=======================================================

    private Context context;
    private SharedPreferences preferences;
    private SharedPreferences.Editor editor;

    //=======================================================
    //===================PREF VARIABLEN======================
    //=======================================================

    /**
     * Enthält die URL des Servers
     */
    private String url;

    /**
     * Speichert, ob der Nutzer die Logfunktion aktiviert hat
     * Momentan ohne Funktion
      */
    private boolean logEnabled;

    /**
     * Speichert den Nutzernamen
     */
    private String user;

    /**
     * Enthält die ID'S der erzeugten Kalendereinträge
     */
    private String entryIds;

    /**
     * Enthält das Kennwort für den Datenabruf
     */
    private String password;

    /**
     * Enthält die ID des aktuell vom Nutzer ausgewählten Kalender
     */
    private int activeCalendarId;

    /**
     * Enthält den Zeitpunkt von dem an Synchronisiert werden soll als Millisekunden beginnend um 00:00 Uhr
     */
    private long sync_from;

    /**
     * Enthält das Intervall mit dem synchronisiert werden soll in Millisekunden
     */
    private long sync_interval;

    /**
     * Enthält einen Wert der angibt, ob der Nutzer aktiv den Hintergrundprozess beendet hat
     */
    private boolean sync_disabled;

    //=======================================================
    //=====================KONSTANTEN========================
    //=======================================================

    private static final String PREFS = "firesoft.de.kalenderadapter";
    private static final String URL = "url";
    private static final String LOG = "log";
    private static final String USER = "user";
    private static final String ENTRYIDS = "entryids";
    private static final String PASSWORD = "zulu";
    private static final String ACTIVE_CALENDAR = "active_calendar";
    private static final String SYNC_FROM = "sync_from";
    private static final String SYNC_INTERVAL = "sync_interval";
    private static final String SYNC_DISABLED = "sync_disabled";

    public static final long default_sync_start = 10800000;

    //=======================================================
    //===================PUBLIC METHODEN=====================
    //=======================================================

    public PreferencesManager(Context context) {

        this.context = context;

    }

    /**
     * Lädt die Einstellungen aus der Preference Datei
     * @throws PackageManager.NameNotFoundException Falls die eingegebene Version nicht gefunden wird, wird ein Fehler geworfen.
     */
    public void load() throws PackageManager.NameNotFoundException{

        // Preference Objekt erstellen
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);

        // Aktuelle Versionsnummer abrufen
        PackageInfo packageInfo;

        packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(),0);

        switch (packageInfo.versionCode) {

            default:

//            case xyz:
//                // Version xyz
//                loadvxyz();
//                break;


            case 5:
                // Version 0.3
                loadv2();
                break;

            case 4:
                // Version 0.2
                loadv1();
                break;
        }
    }

    /**
     * Speichert die Einstellungen in den PREFS
     */
    public void save()  {
        editor = preferences.edit();

        editor.putString(URL,url);
        editor.putString(USER,user);
        editor.putString(ENTRYIDS,entryIds);
        editor.putString(PASSWORD,password);
        editor.putBoolean(LOG,logEnabled);
        editor.putInt(ACTIVE_CALENDAR, activeCalendarId);
        editor.putLong(SYNC_FROM, sync_from);
        editor.putLong(SYNC_INTERVAL,sync_interval);
        editor.putBoolean(SYNC_DISABLED, sync_disabled);

        editor.apply();
    }

    /**
     * Löscht alle Einstellungen
     */
    public void delete() {
        editor = preferences.edit();
        editor.clear();
        editor.apply();
        reset();
    }

    /**
     * Setzt die Einstellungen zurück
     */
    public void reset() {
        url = "";
        user = "";
        password = "";
        activeCalendarId = 0;
        entryIds = "";
        logEnabled = false;
        sync_from = default_sync_start; // Standard 03:00 Uhr
        sync_from = AlarmManager.INTERVAL_DAY; // Standard 24 Stunden
    }

    //=======================================================
    //====================LOAD METHODEN======================
    //=======================================================

    /**
     * Lädt die Einstellungen der Appversion 0.1 und aller kompatiblen Versionen
     */
    private void loadv2() {
        url = preferences.getString(URL, "");
        logEnabled = preferences.getBoolean(LOG,false);
        user = preferences.getString(USER,"");
        entryIds = preferences.getString(ENTRYIDS,"");
        password = preferences.getString(PASSWORD,"");
        activeCalendarId = preferences.getInt(ACTIVE_CALENDAR,0);
        sync_from = preferences.getLong(SYNC_FROM,default_sync_start ); // Standard 03:00 Uhr
        sync_interval = preferences.getLong(SYNC_INTERVAL, AlarmManager.INTERVAL_DAY); // Standard 24 Stunden
        sync_disabled = preferences.getBoolean(SYNC_DISABLED, true);
    }

    /**
     * Lädt die Einstellungen der Appversion 0.1 und aller kompatiblen Versionen
     */
    private void loadv1() {
        url = preferences.getString(URL, "");
        logEnabled = preferences.getBoolean(LOG,false);
        user = preferences.getString(USER,"");
        entryIds = preferences.getString(ENTRYIDS,"");
        password = preferences.getString(PASSWORD,"");
        activeCalendarId = preferences.getInt(ACTIVE_CALENDAR,0);
    }

    //=======================================================
    //===================PRIVATE METHODEN====================
    //=======================================================

    //=======================================================
    //====================GETTER/SETTER======================
    //=======================================================

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    // Ohne Funktion
    private boolean isLogEnabled() {
        return logEnabled;
    }

    // Ohne Funktion
    private void setLogEnabled(boolean logEnabled) {
        this.logEnabled = logEnabled;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getEntryIds() {
        return entryIds;
    }

    public void setEntryIds(String ids) {
        this.entryIds = "";
        this.entryIds = ids;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public int getActiveCalendarId() {
        return activeCalendarId;
    }

    public void setActiveCalendarId(int activeCalendarId) {
        this.activeCalendarId = activeCalendarId;
    }

    public long getSyncFrom() {
        return sync_from;
    }

    public void setSyncFrom(long sync_from) {
        this.sync_from = sync_from;
    }

    public long getSyncInterval() {
        return sync_interval;
    }

    public void setSyncInterval(long sync_interval) {
        this.sync_interval = sync_interval;
    }

    public boolean isSyncDisabled() {
        return sync_disabled;
    }

    public void setSyncDisabled(boolean sync_disabled) {
        this.sync_disabled = sync_disabled;
    }
}

