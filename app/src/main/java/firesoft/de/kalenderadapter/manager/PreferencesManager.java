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


import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

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

    //=======================================================
    //=====================KONSTANTEN========================
    //=======================================================

    private static final String PREFS = "firesoft.de.kalenderadapter";
    private static final String URL = "url";
    private static final String LOG = "log";
    private static final String USER = "user";
    private static final String ENTRYIDS = "entryids";

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
        PackageInfo packageInfo = null;

        try {
            packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(),0);
        } catch (PackageManager.NameNotFoundException e) {
            // Fehler im Paketmanager, die verwendete Version kann nicht identifiziert werden.
            throw e;
        }

        switch (packageInfo.versionCode) {

            default:

//            case 3:
                // Version
//
//                break;
//
//            case 2:
                // Version
//
//                break;

            case 2:
                // Version 0.1
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
        editor.putBoolean(LOG,logEnabled);

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
        logEnabled = false;
    }

    //=======================================================
    //====================LOAD METHODEN======================
    //=======================================================

    /**
     * Lädt die Einstellungen der Appversion 0.1 und aller kompatiblen Versionen
     */
    private void loadv1() {
        url = preferences.getString(URL, "");
        logEnabled = preferences.getBoolean(LOG,false);
        user = preferences.getString(USER,"");
        entryIds = preferences.getString(ENTRYIDS,"");
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

}

