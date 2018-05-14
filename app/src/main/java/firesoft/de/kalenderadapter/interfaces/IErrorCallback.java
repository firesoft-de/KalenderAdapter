/*  Copyright (C) 2018  David Schlossarczyk

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    For the full license visit https://www.gnu.org/licenses/gpl-3.0.*/

package firesoft.de.kalenderadapter.interfaces;

/**
 * Standartisiertes Interface zum Anzeigen von Meldungen an den Benutzer
 */
public interface IErrorCallback {

    /**
     * Bietet die internen Klassen die Möglichkeit eine Fehlerausgabe an Benutzer anzustoßen
     * @param message Nachricht die angezeigt werden soll
     */
    void publishError(String message);

    /**
     * Gibt ein Fortschrittsfeedback an den Nutzer aus
     * @param message Nachricht die angezeigt werden soll
     */
    void publishProgress(String message);

    /**
     * Hängt eine Meldung an die zuletzt angezeigte Meldung an
     * @param message Anzuhängende Nachricht
     */
    void appendProgress(String message);

}
