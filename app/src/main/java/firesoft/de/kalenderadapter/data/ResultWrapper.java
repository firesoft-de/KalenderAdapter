/*  Copyright (C) 2018  David Schlossarczyk

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    For the full license visit https://www.gnu.org/licenses/gpl-3.0.*/

package firesoft.de.kalenderadapter.data;

import java.util.ArrayList;

import javax.xml.transform.Result;

/**
 * Wrapperklasse für die Rückgabewerte des DataTools
 */
public class ResultWrapper {

    //=======================================================
    //=====================VARIABLEN=========================
    //=======================================================

    private final ArrayList<Integer> entryIds;
    private final Exception exception;
    private final String result;
    private final int deletedEntrys;
    private final int addedEntrys;

    //=======================================================
    //====================KONSTRUKTOR========================
    //=======================================================

    /**
     * Erstellt eine neue Instanz
     * @param deletedEntrys Integer, Anzahl der gelöschten Einträge <0  wird als 0 interpretiert
     * @param addedEntrys Integer, Anzahl der hinzugefügten Einträge. <0 wird als 0 interpretiert
     * @param entryIds ArrayList(Integer), Liste mit den ID's der Einträge die beim Durchlauf hinzugefügt wurden.
     */
    public ResultWrapper(ArrayList<Integer> entryIds, String result, int deletedEntrys, int addedEntrys) {
        this.entryIds = entryIds;
        this.result = result;
        this.exception = null;

        if (deletedEntrys <= 0) {
            this.deletedEntrys = 0;
        }
        else {
            this.deletedEntrys = deletedEntrys;
        }

        if (addedEntrys <= 0) {
            this.addedEntrys = 0;
        }
        else {
            this.addedEntrys = addedEntrys;
        }
    }

    /**
     * Erstellt eine neue Instanz
     * @param result String, Textnachricht die den erreichten Zustand beschreibt. Kann bspw. an den Benutzer ausgegeben werden.
     * @param deletedEntrys Integer, Anzahl der gelöschten Einträge <0  wird als 0 interpretiert
     * @param addedEntrys Integer, Anzahl der hinzugefügten Einträge. <0 wird als 0 interpretiert
     */
    public ResultWrapper(String result, int deletedEntrys, int addedEntrys) {
        this.result = result;
        this.exception = null;
        this.entryIds = null;

        if (deletedEntrys <= 0) {
            this.deletedEntrys = 0;
        }
        else {
            this.deletedEntrys = deletedEntrys;
        }

        if (addedEntrys <= 0) {
            this.addedEntrys = 0;
        }
        else {
            this.addedEntrys = addedEntrys;
        }
    }

    /**
     * Erstellt eine neue Instanz
     *
     * @param exception Der Fehler der während der Abfrage aufgetreten ist
     */
    public ResultWrapper(Exception exception) {
        this.entryIds = null;
        this.exception = exception;
        this.result = "";
        deletedEntrys = -1;
        addedEntrys = -1;
    }

    //=======================================================
    //===================GETTER SETTER=======================
    //=======================================================

    /**
     * Gibt den Datenstring aus der vom Server geladen wurde
     * @return Serverresponse
     */
    public ArrayList<Integer> getIds() {
        return entryIds;
    }

    /**
     * Gibt mögliche Fehlermeldungen aus
     * @return Fehlermeldung als Exception
     */
    public Exception getException() {
        return exception;
    }

    /**
     * Gibt das Ergebnis (als String aus)
     */
    public String getResult() {return result;}

    /**
     * Gibt die Anzahl der gelöschten Einträge aus.Sollten keine Werte vorliegen, wird ein negativer Wert ausgegeben.
     */
    public int getDeletedEntrys() {
        return deletedEntrys;
    }

    /**
     * Gibt die Anzahl der hinzugefügten Einträge aus. Sollten keine Werte vorliegen, wird ein negativer Wert ausgegeben.
     */
    public int getAddedEntrys() {
        return addedEntrys;
    }
}
