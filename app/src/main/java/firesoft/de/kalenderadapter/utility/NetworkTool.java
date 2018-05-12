/*  Copyright (C) 2018  David Schlossarczyk

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    For the full license visit https://www.gnu.org/licenses/gpl-3.0.*/

package firesoft.de.kalenderadapter.utility;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.support.annotation.Nullable;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.AbstractMap;
import java.util.ArrayList;
import android.util.Base64;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;

import firesoft.de.kalenderadapter.data.DigestContainer;
import firesoft.de.kalenderadapter.data.ServerParameter;

/**
 * Stellt Methoden zum Verbinden mit einem HTTP(S)-Server zur Verfügung.
 */
public class NetworkTool {

    //=======================================================
    //=====================KONSTANTEN========================
    //=======================================================

    private static int CONNECTION_TIMEOUT = 10 * 1000; // Millisekunden

    public enum REQUEST_METHOD {
        POST,
        GET
    }

    //=======================================================
    //====================PUBLIC METHODEN====================
    //=======================================================

    /**
     * Führt eine Abfrage mittels HTTP oder HTTPS durch. Standardmäßig wird eine HTTPS Verbindung aufgebaut und HTTP nur als Rückfallebene verwendet.
     * @param context Context in dem die Anwendung läuft
     * @param url Adresse des Servers
     * @param parameters List mit Parameter die via POST geschickt werden sollen (In der aktuellen App werden hierüber Passwort und Nutzername in die Methode geschleust.
     * @param forceHTTP Erzwingt die Verwendung von HTTP
     * @return Inhalt der HTTP(S)-Antwort als String
     */
    public static String request(Context context, String url, ArrayList<ServerParameter> parameters, boolean forceHTTP) throws IOException  {

        // Internetverbindung prüfen und ggf. einen Fehler werfen
        if (!checkNetwork(context)) {
            throw new ConnectException("Keine Internetverbindung vorhanden!");
        }

        InputStream stream = null;

        // Passwort und Nutzernamen ermitteln und im Anschluss diese aus den Parametern streichen
        String user = null;
        String password = null;

        int[] removeIndex = new int[2];
        removeIndex[0] = -1;
        removeIndex[1] = -1;

        for (ServerParameter param: parameters
             ) {

            switch (param.getKey()) {

                case "user":
                    user = param.getValueAsString();
                    removeIndex[0] = parameters.indexOf(param);
                    break;

                case "pw":
                    password = param.getValueAsString();
                    removeIndex[1] = parameters.indexOf(param);
                    break;

            }
        }

        if (removeIndex[0] == -1 || removeIndex[1] == -1) {
            throw new IOException("Keine Zugangsdaten übergeben! (NetworkTool.request)");
        }

        // Prüfen, welcher Index größer ist. Falls der kleinere zuerst gelöscht wird und dann der größere, wird anstelle des korrekten größeren Indexes das Element an der Position Index + 1 gelöscht
        if (removeIndex[0] > removeIndex[1]) {
            parameters.remove(removeIndex[0]);
            parameters.remove(removeIndex[1]);
        }
        else {
            parameters.remove(removeIndex[1]);
            parameters.remove(removeIndex[0]);
        }


        // Prüfen ob HTTP erzwungen wird. Falls dies nicht der Fall ist, eine HTTPS Abfrage starten. Ansonsten eine HTTP Anfrage starten
        if (!forceHTTP) {
            try {
                stream = httpsRequest(url, ServerParameter.convertToSimpleEntry(parameters), REQUEST_METHOD.GET, user, password, null);
            } catch (MalformedURLException ex1) {
                // Url ist falsch formatiert
                ex1.printStackTrace();
                throw ex1;
            }
            catch (IOException ex1) {
                // HTTPS Abfrage ist gescheitert

                // Stack ausgeben und Fehler loggen
                ex1.printStackTrace();

                // Rückfallebene ohne SSL benutzen
                try {
                    stream = httpRequest(url, ServerParameter.convertToSimpleEntry(parameters), REQUEST_METHOD.GET, user, password, null);
                } catch (IOException ex2) {
                    // Zweite Anfrage ist auch gescheitert! Bearbeitung abbrechen und Fehler an Aufrufer ausgeben
                    // MalformedURL Ausnahmen werden zu diesem Zeitpunkt wahrscheinlich nicht mehr auftreten. Daher müssen diese nicht abgefragt werden.
                    ex2.printStackTrace();
                    throw ex2;
                }
            }
        }

        // Stream auslesen
        return inputReader(stream);
    }


    /**
     * Prüft ob eine Verbindung zum Internet besteht
     * @param context Context in dem die Anwendung läuft
     * @return true falls Verbindung vorhanden, false falls nicht
     */
    public static boolean checkNetwork(Context context) {
        // Instanz des VerbindungsManagers abrufen
        ConnectivityManager connMgr;
        connMgr = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        // Abfragen, ob ein Netzwerk vorhanden ist
        if (connMgr != null) {
            NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();

            // Prüfen ob das Netzwerk mit dem Internet verbunden ist
            return networkInfo != null && networkInfo.isConnected();
        }
        else {
            return false;
        }
    }

    //=======================================================
    //===================PRIVATE METHODEN====================
    //=======================================================

    /**
     * Führt eine HTTP Abfrage durch
     * @param url Adresse des Servers
     * @param parameters Parameter, welche mittels POST an den Server geschickt werden sollen
     * @return InputStream mit der Serverantwort
     */
    private static InputStream httpRequest(String url, ArrayList<AbstractMap.SimpleEntry> parameters, REQUEST_METHOD method,  String user, String password, @Nullable String authentificationField) throws IOException {
        HttpURLConnection connection;

        // URL erzeugen
        URL _url = generateURL(url, true);

        // Verbindungsobjekt initalisieren
        connection = (HttpURLConnection) _url.openConnection();

        if (connection == null) {
            throw new IOException("Verbindung konnt nicht hergestellt werden!");
        }

        // Verbindungsparameter einrichten
        connection.setConnectTimeout(CONNECTION_TIMEOUT);
        connection.setReadTimeout(CONNECTION_TIMEOUT);
        connection.setDoInput(true);
        connection.setDoOutput(true);

        if (method == REQUEST_METHOD.POST) {
            connection.setRequestMethod("POST");
        }
        else if (method == REQUEST_METHOD.GET) {
            connection.setRequestMethod("GET");
        }

        // Prüfen, ob noch Parameter über sind oder die Liste leer ist. Falls noch welche da sind, diese per POST anhänge
        if (parameters.size() > 0) {
            postWriter(connection.getOutputStream(), parameters);
        }

        // Prüfen, ob ein Auth-String übergeben wurde und diesen ggf. anhängen
        if (authentificationField != null && !authentificationField.equals(""))  {
            connection.addRequestProperty("Authorization", authentificationField);
        }

        // Verbindung herstellen
        connection.connect();

        // Antwortcode prüfen
        int response = connection.getResponseCode();

        switch (response) {
            case HttpURLConnection.HTTP_OK:
                // Verbindung hergestellt, jetzt Daten abrufen
                return connection.getInputStream();
            case HttpsURLConnection.HTTP_UNAUTHORIZED:
                // Authorisierung  erforderlich -> Auth erstellen, anhängen und neue Anfrage starten
                String auth = appendAuth(connection, user, password);

                return httpsRequest(url, parameters, REQUEST_METHOD.GET, user, password, auth);
            default:
                throw new IOException("Fehler bei der HTTP Anfrage! Fehlercode:" + String.valueOf(response));
        }
    }

    /**
     * Führt eine HTTPS Abfrage durch
     * @param url Adresse des Servers
     * @param parameters Parameter, welche mittels POST an den Server geschickt werden sollen
     * @return InputStream mit der Serverantwort
     */
    private static InputStream httpsRequest(String url, ArrayList<AbstractMap.SimpleEntry> parameters, REQUEST_METHOD method, String user, String password, @Nullable String authentificationField) throws IOException{
        HttpsURLConnection connection;

        // URL erzeugen
        URL _url = generateURL(url, false);

        // Verbindungsobjekt initalisieren
        connection = (HttpsURLConnection) _url.openConnection();

        if (connection == null) {
            throw new IOException("Verbindung konnt nicht hergestellt werden!");
        }

        // Verbindungsparameter einrichten
        connection.setConnectTimeout(CONNECTION_TIMEOUT);
        connection.setReadTimeout(CONNECTION_TIMEOUT);
        connection.setDoInput(true);
        connection.setDoOutput(true);

        if (method == REQUEST_METHOD.POST) {
            connection.setRequestMethod("POST");
        }
        else if (method == REQUEST_METHOD.GET) {
            connection.setRequestMethod("GET");
        }

        // Prüfen, ob ein Auth-String übergeben wurde und diesen ggf. anhängen
        if (authentificationField != null && !authentificationField.equals(""))  {
            connection.addRequestProperty("Authorization", authentificationField);
        }

        // Prüfen, ob noch Parameter über sind oder die Liste leer ist. Falls noch welche da sind, diese per POST anhänge
        if (parameters.size() > 0) {
            postWriter(connection.getOutputStream(), parameters);
        }

        // Verbindung herstellen
        connection.connect();

        // Antwortcode prüfen
        int response = connection.getResponseCode();

        switch (response) {
            case HttpsURLConnection.HTTP_OK:
                // Verbindung hergestellt, jetzt Daten abrufen
                return connection.getInputStream();

            case HttpsURLConnection.HTTP_UNAUTHORIZED:
                // Authorisierung  erforderlich -> Auth erstellen, anhängen und neue Anfrage starten
                String auth = appendAuth(connection, user, password);

                return httpsRequest(url, parameters, REQUEST_METHOD.GET, user, password, auth);
            default:
                throw new IOException("Fehler bei der HTTP Anfrage! Fehlercode:" + String.valueOf(response));
        }
    }

    /**
     * Liest die Serverantwort und konvertiert diese in einen String
     * @param stream Stream mit der Antwort des Servers
     * @return Serverantwort im Stringformat
     * @throws NullPointerException Wird geworfen, falls der eingegenbene Stream keine Daten enthält
     * @throws IOException Wird geworfen, falls es während des Auslesens zu einem Fehler kommt
     */
    private static String inputReader(InputStream stream) throws NullPointerException, IOException {
        String response;
        StringBuilder builder = new StringBuilder();

        if (stream != null) {

            //reader erstellen und diesen buffern
            InputStreamReader reader = new InputStreamReader(stream, Charset.forName("UTF-8"));
            BufferedReader bReader = new BufferedReader(reader);

            try {
                String line = bReader.readLine();

                while (line != null) {
                    builder.append(line).append("\n");
                    line = bReader.readLine();
                }
            } catch (IOException e) {
                e.printStackTrace();
                throw e;
            }
        }
        else {
            throw new NullPointerException("Eingegebener Antwortstream enthält keine Daten!");
        }

        response = builder.toString();

        return response;
    }

    /**
     * Schreibt POST Daten in eine Outputstream
     * @param stream Ausgehender Stream der HTTP oder HTTPS Verbindung
     * @param parameters Zu schreibende Daten/Parameter
     */
    private static void postWriter(OutputStream stream, ArrayList<AbstractMap.SimpleEntry> parameters) throws IOException {
        // Benötigte Objekte erstellen
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(stream, "UTF-8"));

        StringBuilder query = new StringBuilder();
        boolean first = true;

        // Query zusammenbauen
        for (AbstractMap.SimpleEntry pair : parameters)
        {
            if (first) {
                first = false;
            }
            else {
                query.append("&");
            }

            query.append(URLEncoder.encode(pair.getKey().toString(), "UTF-8"));
            query.append("=");
            query.append(URLEncoder.encode(pair.getValue().toString(), "UTF-8"));
        }

        // Post Query in den OutputStream schreiben
        writer.write(query.toString());

        // Schreibvorgang beenden
        writer.flush();
        writer.close();
        stream.close();
    }

    /**
     * Erstellt aus einem String ein URL Objekt. Dabei wird ggf. das Protkoll angepasst
     * @param url Zu prüfende URL
     * @param targetHTTP True wenn als Protokoll HTTP verwendet werden soll. False falls HTTPS verwendet werden soll
     * @return bearbeitete URL
     */
    private static URL generateURL(String url, boolean targetHTTP) throws MalformedURLException {
        URL _url;

        // Infrastruktur für Regex erstellen
        Pattern word = Pattern.compile("(http)[s]?[:]?(//)");

        // Prüfe, ob http oder https vorangestellt wurde
        if (!word.matcher(url).find()) {
            url = "https://" + url;
        }

        // Protkollprüfung
        if (url.contains("https://")) {
            // Die Eingabe verwendet HTTPS
            // Prüfen ob das auch so gewünscht ist
            if (targetHTTP) {
                // Zielprotokoll: HTTP
                // HTTPS gegen HTTP austauschen
                url = url.replace("https://","http://");
            }
        }
        else if (url.contains("http://")){
            // Die Eingabe verwendet HTTP
            // Prüfen ob das auch so gewünscht ist
            if (!targetHTTP) {
                // Zielprotokoll: HTTPS
                // HTTP gegen HTTPS austauschen
                url = url.replace("http://","https://");
            }
        }
        else {
            // Es wurden kein passendes Protkoll gefunden
            throw new MalformedURLException("Kein korrektes Protkoll gefunden!");
        }

        // Prüfen, ob ein abschließendes '/' vorhanden ist
        if (url.substring(url.length()-2,url.length()-1).equals("/")) {
            url = url + "/";
        }

        // URL erzeugen
        _url = new URL(url);
        return _url;
    }

    private static String appendAuth(HttpURLConnection connection, String user, String password) throws SecurityException, UnsupportedEncodingException {

        // Headerfeld mit den Informationen für die Authentifizierung auslesen
        String auth_field = connection.getHeaderField("WWW-Authenticate");

        // Prüfen welche Authentifzierung verwendet wird
        if (auth_field.equals("")) {
            throw new SecurityException("Unbekanntes Authentifzierungs-Verfahren!");
        }

        if (auth_field.contains("Digest")) {
            // Digest Authentifzierung
            return appendDigestAuth(connection, user, password);
        }
        else if (auth_field.contains("Basic")) {
            // Basic Authentifzierung
            return appendBasicAuth(user, password);
        }
        else {
            throw new SecurityException("Unbekanntes Authentifzierungs-Verfahren!");
        }

    }

    /**
     * Fügt an eine bestehende Verbindung eine Digest-Authentifizierung an. Erstellt auf Basis von https://tools.ietf.org/html/rfc2617#page-5 und https://gist.github.com/slightfoot/5624590
     * Achtung: Die Methode ist nicht in der Lage einen eingegebenen CNONCE (wie bspw. bei einem mehrmaligen Datenaustausch zwischen Server und Client vom Server mitgegeben wird) zu verarbeiten!
     * @return Gibt einen String für das "Authorization" Field im HTTP(S) Header zurück
     */
    private static String appendDigestAuth(HttpURLConnection connection, String user, String password) throws SecurityException, UnsupportedEncodingException {

        // Headerfeld mit den Informationen für die Authentifizierung auslesen
        String auth_field = connection.getHeaderField("WWW-Authenticate");

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

        rawA2 = connection.getRequestMethod() + ":" + connection.getURL().getPath();
        digest.update(rawA2.getBytes("ISO-8859-1"));

        hashedA2 = bytesToHexString(digest.digest());
        digest.reset();

        // Den Response String aus A1 und A2 zusammebauen
        String rawResponse = null;
        String hashedResponse = null;



        // Dieser Abschnitt wird abweichend von der Norm durchgeführt. Vom Server kommt kein nonce-Count und cNonce
        if (container.getQop().equals("auth") || container.getQop().equals("auth-int")) {
            rawResponse = hashedA1 + ":" + container.getNonce() + ":" + container.getNcAsString() + ":" + container.generateCnonce(8) + ":" + container.getQop() + ":" + hashedA2;
        }
        else {
            rawResponse = connection.getRequestMethod() + ":" + container.getNonce() + ":" +  connection.getURL().getPath();
        }

        digest.update(rawResponse.getBytes("ISO-8859-1"));

        hashedResponse = bytesToHexString(digest.digest());
        digest.reset();


        // Die Authentifzierungsbestandteile zusammenführen und an die Connection anhängen
        String builder = "Digest " +
                "username" + "=\"" + user + "\", " +
                "realm" + "=\"" + container.getRealm() + "\", " +
                "nonce" + "=\"" + container.getNonce() + "\", " +
                "uri" + "=\"" + "/ical/" + "\", " +
                "algorithm" + "=\"" + container.getAlgorithm() + "\", " +
                "response" + "=\"" + hashedResponse + "\", " +
                "qop" + "=" + container.getQop() + ", " +
                "nc" + "=" + "00000001" + ", " +
                "cnonce" + "=\"" + container.getCnonce() + "\"";

        return builder;

    }

    /**
     * Erstellt eine Authentifzierung auf Basis der Basic-Auth
     */
    private static String appendBasicAuth(String user, String password) {

        // Basierend auf https://robert-reiz.com/2014/10/05/java-http-request-with-basic-auth/
        String user_pass = user + ":" + password;
        String encoded = Base64.encodeToString(user_pass.getBytes(), android.util.Base64.DEFAULT);
        return "Basic " + encoded;
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

    /**
     * Diese Methode bearbeitet die eingebene URL so, dass sie konform mit den nachfolgenden Arbeitschritte ist.
     */
    private static String checkURL(String url) {

        // Infrastruktur für Regex erstellen
        Pattern word = Pattern.compile("(http)[s]?[:]?(//)");

        // Prüfe, ob http oder https vorangestellt wurde
        if (!word.matcher(url).find()) {
            url = "https://" + url;
            return url;
        }

        // Prüfen, ob am Anfang des String http:// steht und das ggf. korrigieren
        word = Pattern.compile("^(http)[^s](//)");

        if (word.matcher(url).find()) {
            url = url.replace("http","https");
        }

        return url;
    }

}
