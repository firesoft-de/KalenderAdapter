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

import java.util.AbstractMap;
import java.util.ArrayList;

/**
 * Stellt einen Datentyp für Serverparameter bereit
 */
public class ServerParameter implements AbstractMap.Entry {

    private String key;
    private Object value;

    /**
     * Erstellt eine neue Instanz
     * @param key Inhalt des Keys
     * @param value Inhalt des Values
     */
    public ServerParameter(String key, Object value) {
        this.key = key;
        this.value = value;
    }

    /**
     * Gibt den Inhalt des Keys aus
     * @return Key als Object
     */
    @Override
    public String getKey() {
        return key;
    }

    /**
     * Gibt den Inhalt des Values aus
     * @return Value als Object
     */
    @Override
    public Object getValue() {
        return value;
    }

    /**
     * Gibt den Inhalt des Values aus
     * @return Value als String
     */
    public String getValueAsString() {
        return String.valueOf(value);
    }

    /**
     * Gibt den Inhalt des Values aus
     * @return Value als Integer
     */
    public Integer getValueAsInteger() {
        return (Integer) value;
    }


    /**
     * Setzt den Value
     * @param input Neuer Wert für den Value
     * @return null
     */
    @Override
    public Object setValue(Object input) {

        if (value instanceof String) {
            this.value = String.valueOf(input);
        }
        else if (value instanceof Integer) {
            this.value = input;
        }

        return null;
    }

    /**
     * Setzt den Key
     * @param key Neuer Wert des Keys
     */
    public void setKey(String key) {
        this.key = key;
    }

    public static ArrayList<AbstractMap.SimpleEntry> convertToSimpleEntry(ArrayList<ServerParameter> input) {

        ArrayList<AbstractMap.SimpleEntry> map = new ArrayList<>();

        for (ServerParameter parameter: input
                ) {
            AbstractMap.SimpleEntry entry = new AbstractMap.SimpleEntry<>(parameter.getKey(), parameter.getValueAsString());
            map.add(entry);
        }

        return map;
    }
}
