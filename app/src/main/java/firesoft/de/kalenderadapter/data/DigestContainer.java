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

import java.util.Random;

/**
 * Stellt einen Container für Logindaten des Digest Authentication zur Verfügung
 */
public class DigestContainer {

    //=======================================================
    //======================VARIABLEN========================
    //=======================================================

    private String realm;
    private String nonce;
    private String algorithm;
    private String qop;
    private String cnonce;
    private int nc;

    //=======================================================
    //=====================KONSTRUKTOR=======================
    //=======================================================

    public DigestContainer(String headerField) {

        headerField = headerField.replace("Digest ", "");

        String[] auth_elements = headerField.split(", ");

        for (String element: auth_elements
             ) {

            if (element.contains("MD5")) {
                // Es wird MD5 verwendet
                algorithm = "MD5";

            }
            else {
                String[] element_fields = element.split("(=\")");

                switch (element_fields[0]) {

                    case "realm":
                        realm = element_fields[1];
                        realm = realm.replace("\"","");
                        break;

                    case "nonce":
                        nonce = element_fields[1];
                        nonce = nonce.replace("\"","");
                        break;

                    case "qop":
                        qop = element_fields[1];
                        qop = qop.replace("\"","");
                        break;

                    case "nc":
                        qop = element_fields[1];
                        qop = qop.replace("\"","");
                        break;

                }
            }

        }

        if (nc == 0) {
            nc = 1;
        }

    }

    //=======================================================
    //===================PUBLIC METHODEN=====================
    //=======================================================

    /**
     * Generiert über eine unsicheren Zufallsgenerator einen CNONCE
     * @param length Gewünschte Länge des CNONCE
     */
    public String generateCnonce(int length) {

        final String baseData = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        Random generator = new Random();

        StringBuilder builder = new StringBuilder(length);

        for (int i = 0; i<length; i++) {
            builder.append(baseData.charAt(generator.nextInt(baseData.length())));
        }

        cnonce = builder.toString();

        return builder.toString();

    }

    //=======================================================
    //=======================GETTER==========================
    //=======================================================

    public String getRealm() {
        return realm;
    }

    public String getNonce() {
        return nonce;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public String getQop() {
        return qop;
    }

    public String getCnonce() {
        return cnonce;
    }

    public int getNc() {
        return nc;
    }

    public String getNcAsString() {

        StringBuilder sb = new StringBuilder();
        while(sb.length()+String.valueOf(nc).length()<8) {
            sb.append('0');
        }
        sb.append(String.valueOf(nc));

        return sb.toString();
    }
}
