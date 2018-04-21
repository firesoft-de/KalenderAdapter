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

package firesoft.de.kalenderadapter;

import org.junit.Assert;
import org.junit.Test;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import firesoft.de.kalenderadapter.data.DigestContainer;
import firesoft.de.kalenderadapter.utility.NetworkTool;

import static org.hamcrest.CoreMatchers.is;

public class DigestAuthTest {

    @Test
    public void digestTest() throws UnsupportedEncodingException {
        // Headerfeld mit den Informationen für die Authentifizierung auslesen
        String auth_field = "Digest realm=\"THW-Dienstplaner Login\", " +
                "qop=\"auth\", " +
                "algorithm=MD5, "+
                "nonce=\"i3hKaeRpBQA=bc8f16ca586395187b0cc820afce188b9a6b2eeb\"";


        String user = "DavidS";
        String password = "s5MhKr9Jpn[ZgM1<";

        String url = "/";
        String method = "GET";


        /////


        // Prüfen ob Digest verwendet wird
        if (!auth_field.contains("Digest") || auth_field.equals("")) {
            throw new SecurityException("Unbekanntes Auth-Verfahren. Benötigt wird Digest!");
        }

        // Einen Container für die vom Server gesendeten Daten erstellen und diesen befüllen
        DigestContainer container = new DigestContainer(auth_field);

        // Eine Digest-Instanz mit dem vom Server angegebenen Algorithmus erstellen
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance(container.getAlgorithm());
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            throw new SecurityException(e.getMessage());
        }



        // String A1 (RFC2617 3.2.2.2) erstellen und hashen. Dieser enthält Nutzername, Realm und Passwort
        String rawA1 = null;
        String hashedA1 = null;

        rawA1 = user + ":" + container.getRealm() + ":" + password;
        digest.update(rawA1.getBytes("ISO-8859-1"));

        hashedA1 = bytesToHexString(digest.digest());
        digest.reset();



        // String A2 (RFC2617 3.2.2.3) erstellen und hashen. Dieser enthält die Request Methode und der URL
        String rawA2 = null;
        String hashedA2 = null;

        rawA2 = method + ":" + url;
        digest.update(rawA2.getBytes("ISO-8859-1"));

        hashedA2 = bytesToHexString(digest.digest());
        digest.reset();

        // Den Response String aus A1 und A2 zusammebauen
        String rawResponse = null;
        String hashedResponse = null;



        // Dieser Abschnitt wird abweichend von der Norm durchgeführt. Vom Server kommt kein nonce-Count und cNonce
        if (container.getQop().equals("auth") || container.getQop().equals("auth-int")) {
            rawResponse = hashedA1 + ":" + container.getNonce() + ":" + container.getNcAsString() + ":" + "fkjwSDtW" + ":" + container.getQop() + ":" + hashedA2;
        }
        else {
            rawResponse = method + ":" + container.getNonce() + ":" +  url;
        }

        digest.update(rawResponse.getBytes("ISO-8859-1"));

        hashedResponse = bytesToHexString(digest.digest());
        digest.reset();


        // Die Authentifzierungsbestandteile zusammenführen und an die Connection anhängen
        StringBuilder builder = new StringBuilder(128);

        builder.append("Digest ");
        builder.append("username").append("=\"").append(user).append("\", ");
        builder.append("realm").append("=\"").append(container.getRealm()).append("\", ");
        builder.append("nonce").append("=\"").append(container.getNonce()).append("\", ");
        builder.append("uri").append("=\"").append("/").append("\", ");
        builder.append("algorithm").append("=\"").append(container.getAlgorithm()).append("\", ");
        builder.append("response").append("=\"").append(hashedResponse).append("\", ");
        builder.append("qop").append("=").append(container.getQop()).append(", ");
        builder.append("nc").append("=").append("00000001").append(", ");
        builder.append("cnonce").append("=\"").append("fkjwSDtW").append("\"");

        String correct = "Digest username=\"DavidS\", realm=\"THW-Dienstplaner Login\", nonce=\"i3hKaeRpBQA=bc8f16ca586395187b0cc820afce188b9a6b2eeb\", uri=\"/\", algorithm=\"MD5\", qop=auth, nc=00000001, cnonce=\"fkjwSDtW\", response=\"54195f47877016f3268be902bcecc48c\"\n";

        Assert.assertThat(builder.toString(), is(correct));
    }


    // (c) Slightfood, https://gist.github.com/slightfoot/5624590
    private static final String HEX_LOOKUP = "0123456789abcdef";
    private static String bytesToHexString(byte[] bytes)
    {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte aByte : bytes) {
            sb.append(HEX_LOOKUP.charAt((aByte & 0xF0) >> 4));
            sb.append(HEX_LOOKUP.charAt((aByte & 0x0F)));
        }
        return sb.toString();
    }

}
