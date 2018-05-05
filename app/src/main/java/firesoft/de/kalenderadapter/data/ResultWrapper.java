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

/**
 * Wrapperklasse für die Rückgabewerte des DataTools
 */
public class ResultWrapper {

    //=======================================================
    //=====================VARIABLEN=========================
    //=======================================================

    private final ArrayList<Integer> entryIds;
    private final Exception exception;

    //=======================================================
    //====================KONSTRUKTOR========================
    //=======================================================

    /**
     * Erstellt eine neue Instanz
     *
     * @param entryIds Das Datenpaket welches abgerufen wurde
     */
    public ResultWrapper(ArrayList<Integer> entryIds) {
        this.entryIds = entryIds;
        this.exception = null;
    }

    /**
     * Erstellt eine neue Instanz
     *
     * @param exception Der Fehler der während der Abfrage aufgetreten ist
     */
    public ResultWrapper(Exception exception) {
        this.entryIds = null;
        this.exception = exception;
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

}
