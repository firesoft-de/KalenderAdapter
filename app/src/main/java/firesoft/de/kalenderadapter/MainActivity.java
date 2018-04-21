/*  Copyright (C) 2018  David Schlossarczyk

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    For the full license visit https://www.gnu.org/licenses/gpl-3.0.*/

package firesoft.de.kalenderadapter;

import android.Manifest;
import android.annotation.SuppressLint;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.Observer;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.CardView;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;

import firesoft.de.kalenderadapter.manager.PreferencesManager;
import firesoft.de.kalenderadapter.data.ServerParameter;
import firesoft.de.kalenderadapter.interfaces.IErrorCallback;
import firesoft.de.kalenderadapter.manager.AsyncTaskManager;
import firesoft.de.kalenderadapter.manager.CalendarManager;

public class MainActivity extends AppCompatActivity implements IErrorCallback {

    //=======================================================
    //=====================VARIABLEN=========================
    //=======================================================

    AsyncTaskManager taskManager;
    CalendarManager cManager;
    PreferencesManager pManager;
    MutableLiveData<String> messageFromBackground;


    //=======================================================
    //=====================OVERRIDES=========================
    //=======================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Elevation der Cards setzen
        Drawable drawable = getResources().getDrawable(android.R.drawable.dialog_holo_light_frame);

        CardView card = this.findViewById(R.id.card_calendar);
        card.setBackground(drawable);

        drawable = getResources().getDrawable(android.R.drawable.dialog_holo_light_frame);

        card = this.findViewById(R.id.card_settings);
        card.setBackground(drawable);

        // UI Feedbacks für den Spinner erstellen
        Spinner spinner = this.findViewById(R.id.spinner_calendar);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                spinnerSelectionChanged(adapterView, view, i, l);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        // Einen CalenderManager erstellen
        cManager = new CalendarManager(getApplicationContext(),this);

        // Die verf. Kalender in den Spinner schreiben
        populateSpinner();

        // Livedata erstellen. Damit wird eine Möglichkeit geschaffen zwischen verschiedenen Threads zu kommunizieren
        messageFromBackground = new MutableLiveData<>();
        messageFromBackground.observe(this, new Observer<String>() {
            @Override
            public void onChanged(@Nullable String s) {
                publishProgress(s);
            }
        });

        // UI Feedbacks für den Downloadbutton hinzufügen
        Button btStartDownload = this.findViewById(R.id.bt_startDownload);
        btStartDownload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startLoader();
            }
        });

        // UI Feedbacks für den Deletebutton hinzufügen
        Button btDelete  = this.findViewById(R.id.bt_deleteAll);
        btDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                cManager.deleteEntries();
                pManager.setEntryIds("");
            }
        });

        // Preferences Manager erstellen
        pManager = new PreferencesManager(getApplicationContext());

        try {
            pManager.load();

            // Entry-IDS laden
            cManager.setEntryIdsFromString(pManager.getEntryIds());

        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            displayMessage("Fehler beim Laden der Einstellungen! " + e.getMessage(), Snackbar.LENGTH_LONG);
        }


    }

    /**
     * Wird aufgerufen, wenn die App beendet wird
     */
    @Override
    protected void onDestroy() {

        savePrefs();

        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case 1: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // Berechtigung wurde gegeben
                    startLoader();

                }
            }

            case 2: {
                // Aufruf aus dem populateSpinner

                populateSpinner();

            }

            //Keine Berechtigung
        }
    }

    //=======================================================
    //====================UI-METHODEN=======================
    //=======================================================

    /**
     * Verarbeitet die Auswahl des Nutzers im Spinner
     */
    private void spinnerSelectionChanged(AdapterView<?> adapterView, View view, int i, long l) {
        String selectedElement = adapterView.getItemAtPosition(i).toString();
        cManager.setActiveCalendar(selectedElement);
    }

    /**
     * Fragt die verfügbaren Kalender ab und schreibt diese in den Spinner
     */
    private void populateSpinner() {

        checkPermission(2);

        // Abfragen welche Kalender verfügbar sind
        ArrayList<String> availableCalendars = cManager.getCalendars();

        // Spinner einrichten (Layout festlegen)
        Spinner spinner = this.findViewById(R.id.spinner_calendar);

        // Daten in den Spinner schreiben (via Adapter)
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getApplicationContext(), R.layout.custom_spinner_item,availableCalendars);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

    }

    //=======================================================
    //===================HILFSMETHODEN=======================
    //=======================================================

    /**
     * Interfacemethode um Fehlermeldungen aus den tieferen Klassen zum Benutzer durchzustellen
     * @param message Nachricht die angezeigt werden soll
     */
    @Override
    public void publishError(String message) {
        displayMessage(message,Snackbar.LENGTH_LONG);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void publishProgress(String message) {
        TextView tv = this.findViewById(R.id.tV_progress);
        tv.setText(message);
    }


    public void checkPermission(int requestCode) {
        if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED  ) {
            // Keine Erlaubnis vorhanden.

            // Nutzer nach der Erlaubnis fragen
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_CALENDAR, Manifest.permission.READ_CALENDAR}, requestCode);
        }
    }

    /**
     * Zeigt eine Nachricht als SnackBar an
     * @param message Inhalt der anzuzeigenden Nachricht
     * @param duration Anzeigedauer (Werte sind in der SnackBar-Klasse hinterlegt)
     */
    private void displayMessage(String message, int duration) {
        Snackbar.make(this.findViewById(R.id.MainFrame), message, duration)
                .show();
    }

    private void startLoader() {

        EditText user = this.findViewById(R.id.eT_user);
        EditText password = this.findViewById(R.id.et_pw);
        EditText url = this.findViewById(R.id.eT_url);

        // Daten aus den TextViews abfragen
        if (user.getText().toString().equals("") || password.getText().toString().equals("") || url.getText().toString().equals("")) {
            displayMessage("Bitte fülle alle Felder aus!", Snackbar.LENGTH_LONG);
            return;
        }

        ArrayList<ServerParameter> parameters = new ArrayList<>();
        ServerParameter param = new ServerParameter("url", url.getText().toString());
        parameters.add(param);

        param = new ServerParameter("user", user.getText().toString());
        parameters.add(param);

        param = new ServerParameter("pw", password.getText().toString());
        parameters.add(param);

        taskManager = new AsyncTaskManager(getSupportLoaderManager(), getApplicationContext(),this,cManager, pManager, messageFromBackground);
        taskManager.startDownload(this,parameters);

    }

    private void savePrefs() {

        EditText etURL = this.findViewById(R.id.eT_url);
        EditText etUser = this.findViewById(R.id.eT_user);

        pManager.setUrl(etURL.getText().toString());
        pManager.setUser(etUser.getText().toString());

        pManager.save();
    }

}

