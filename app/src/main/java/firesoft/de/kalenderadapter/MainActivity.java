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
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.Observer;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.DialogFragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.CardView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.TimePicker;

import com.github.zagum.switchicon.SwitchIconView;

import java.text.ParseException;
import java.util.ArrayList;

import firesoft.de.kalenderadapter.data.ServerParameter;
import firesoft.de.kalenderadapter.fragments.TimePickerFragment;
import firesoft.de.kalenderadapter.interfaces.IErrorCallback;
import firesoft.de.kalenderadapter.manager.AsyncTaskManager;
import firesoft.de.kalenderadapter.manager.CalendarManager;
import firesoft.de.kalenderadapter.manager.PreferencesManager;
import firesoft.de.kalenderadapter.service.BackgroundService;
import firesoft.de.kalenderadapter.service.ServiceUtil;
import firesoft.de.kalenderadapter.utility.DateAndTimeConversion;

public class MainActivity extends AppCompatActivity implements IErrorCallback {

    //=======================================================
    //=====================VARIABLEN=========================
    //=======================================================

    AsyncTaskManager taskManager;
    CalendarManager cManager;
    PreferencesManager pManager;
    MutableLiveData<String> messageFromBackground;
    private MutableLiveData<Integer> progressValue;
    private MutableLiveData<Integer> progressMax;


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

        drawable = getResources().getDrawable(android.R.drawable.dialog_holo_light_frame);

        card = this.findViewById(R.id.card_about);
        card.setBackground(drawable);

        drawable = getResources().getDrawable(android.R.drawable.dialog_holo_light_frame);

        card = this.findViewById(R.id.card_settings_extended);
        card.setBackground(drawable);

        // UI Listener erstellen
        generateUIListener();


        // Debug Optionen ein/ausschalten
        if (BuildConfig.DEBUG) {
            this.findViewById(R.id.bt_deleteAll).setVisibility(View.VISIBLE);
            this.findViewById(R.id.bt_check_service_alive).setVisibility(View.VISIBLE);
        }
        else {
            this.findViewById(R.id.bt_deleteAll).setVisibility(View.GONE);
            this.findViewById(R.id.bt_check_service_alive).setVisibility(View.GONE);
        }

        // Livedata erstellen. Damit wird eine Möglichkeit geschaffen zwischen verschiedenen Threads zu kommunizieren
        messageFromBackground = new MutableLiveData<>();
        messageFromBackground.observe(this, new Observer<String>() {
            @Override
            public void onChanged(@Nullable String s) {
                publishProgress(s,-1,-1);
            }
        });

        progressValue = new MutableLiveData<>();
        progressValue.observe(this, new Observer<Integer>() {
            @Override
            public void onChanged(@Nullable Integer s) {
                publishProgress(null,s,-1);
            }
        });

        progressMax = new MutableLiveData<>();
        progressMax.observe(this, new Observer<Integer>() {
            @Override
            public void onChanged(@Nullable Integer s) {
                publishProgress(null,-1,s);
            }
        });

        //
        // Startprozedur (Datenobjekte initalisieren, Daten laden, weitere To_Do's erledigen)
        //

        // Preferences Manager erstellen
        pManager = new PreferencesManager(getApplicationContext());

        // Einen CalenderManager erstellen
        cManager = new CalendarManager(getApplicationContext(),this);


        // Daten des PreferencesManager und des CalendarManager laden
        pManager.load();

        // Preference Einstellungen in die UI schreiben
        fillFromPreferences();

        // Appversion anzeigen
        setVersion();

        // Prüfen, ob der Hintergrundservice läuft oder nicht
        if (!ServiceUtil.checkServiceIsRunning(this, BackgroundService.class)) {
            // UI Switch setzen
            setServiceSwitch(false);
            ((SwitchIconView) this.findViewById(R.id.switch_icon_view)).setIconEnabled(false);
        }
        else {
            setServiceSwitch(true);
            ((SwitchIconView) this.findViewById(R.id.switch_icon_view)).setIconEnabled(true);
        }

    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();

        // Die verf. Kalender in den Spinner schreiben
        populateSpinner();
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

                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    changeUiElementsEnabled((ViewGroup) this.findViewById(R.id.MainFrame), true);
                    this.findViewById(R.id.bt_showRightsManagment).setVisibility(View.GONE);

                    // Berechtigung wurde gegeben
                    populateSpinner();

                }
                else {
                    // Keine Berechtigung erteilt. Alle UI-Elemente sperren und einen Button zur erneuten Anzeige der Aufforderung anzeigen.
                    this.findViewById(R.id.bt_showRightsManagment).setVisibility(View.VISIBLE);
                    changeUiElementsEnabled((ViewGroup) this.findViewById(R.id.MainFrame),false);
                }

            }

            //Keine Berechtigung
        }
    }

    //=======================================================
    //====================UI-METHODEN=======================
    //=======================================================

        /**
        * Erstellt die benötigten UI-Listener für die MainActivity
        */
        private void generateUIListener() {

        // Spinner
        Spinner spinner = this.findViewById(R.id.spinner_calendar);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                spinnerSelectionChanged(adapterView, i);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        // Downloadbutton
        this.findViewById(R.id.bt_startDownload).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // progressbar auf intermediate setzen
                setProgressbarState(false);

                startLoader();
            }
        });

        this.findViewById(R.id.bt_emptyCalendar).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                cManager.deleteEntries();
//                pManager.setEntryIds("");
            }
        });

        // Deletebutton für alle Einträge im Kalender (DEBUG-Option)
        this.findViewById(R.id.bt_deleteAll).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                // Bestätigung des Nutzers abfragen, ob wirjklich der gesamte Kalender gelöscht werden soll
                final Dialog informationDialog = new Dialog(MainActivity.this);

                informationDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                informationDialog.setCancelable(true);
                informationDialog.setContentView(R.layout.layout_information_dialog);


                ((TextView) informationDialog.findViewById(R.id.info_button)).setText(R.string.info_delete_text);

                Button dialogButton = (Button) informationDialog.findViewById(R.id.info_button);
                dialogButton.setText(R.string.debug_delete_all_info);
                dialogButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        informationDialog.dismiss();

                        cManager.clearCalendar();
//                        pManager.setEntryIds("");
                    }
                });

                informationDialog.show();

            }
        });

        // Resetbutton
        this.findViewById(R.id.bt_reset).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                pManager.reset();
                pManager.save();
                fillFromPreferences();
            }
        });

        // Ein-/ Ausschalter für den Hintergrundservice
        Switch switch_service = this.findViewById(R.id.switch_service);
        switch_service.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                pManager.setSyncDisabled(!checked); // Ist hier etwas unglücklich gelöst. Der PreferncesManager speichert, ob der Nutzer den Service deaktiviert hat (true = deaktiviert). Der Button gibt aber an, ob der Service aktiv ist (true = aktiv)
                pManager.save();

                //TODO: Rausnehmen oder anders lösen -> Führt wahrscheinlich zu doppeltem ausführen des Servicestart

                // Service je nach Nutzerauswahl starten oder stoppen
                if (!checked) {
                    ServiceUtil.stopService(getApplicationContext());
                }
                else {
                    try {
                        ServiceUtil.startService(getApplicationContext(),pManager.getSyncFrom(),pManager.getSyncInterval());
                    } catch (ParseException e) {
                        publishError(getString(R.string.error_service_times_unchangeable));
                        e.printStackTrace();
                    }
                }

                change_service_indicator();

            }
        });

        // Synchronisationszeitpunkt
        final Button etServiceSyncFrom = this.findViewById(R.id.service_sync_from);
        etServiceSyncFrom.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showStartTimePickerDialog();
            }
        });

        // Synchronisationsintervall
        final Button etServiceSyncInterval = this.findViewById(R.id.service_sync_interval);
        etServiceSyncInterval.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showIntervalTimePickerDialog();
            }
        });

        // Button für die erneute Aufforderung zur Rechtevergabe
        ((Button) this.findViewById(R.id.bt_showRightsManagment)).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                populateSpinner();
            }
        });

        // Test Button für Hintergrundservice
        Button btCheckServiceAlive = this.findViewById(R.id.bt_check_service_alive);
        btCheckServiceAlive.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                boolean res = ServiceUtil.checkServiceIsRunning(getApplicationContext(),BackgroundService.class);
                publishError("Status: " + String.valueOf(res));
            }
        });

    }

    /**
     * Verarbeitet die Auswahl des Nutzers im Spinner
     */
    private void spinnerSelectionChanged(AdapterView<?> adapterView, int i) {
        String selectedElement = adapterView.getItemAtPosition(i).toString();
        cManager.setActiveCalendar(selectedElement);
        pManager.setActiveCalendarId(cManager.getActiveCalendar().getId());
    }

    /**
     * Fragt die verfügbaren Kalender ab und schreibt diese in den Spinner
     */
    private void populateSpinner() {

        checkPermission(2);

        // Abfragen welche Kalender verfügbar sind
        ArrayList<String> availableCalendars = cManager.getCalendars();

        if (availableCalendars == null) {
            return;
        }

        // Den aktiven Kalender anhand der in den Prefs gespeicherten ID setzen
        cManager.setActiveCalendar(pManager.getActiveCalendarId());

        // Spinner einrichten (Layout festlegen)
        Spinner spinner = this.findViewById(R.id.spinner_calendar);

        // Daten in den Spinner schreiben (via Adapter)
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getApplicationContext(), R.layout.custom_spinner_item,availableCalendars);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        // Aktiven Kalender anzeigen (falls vorhanden)
        if (cManager.getActiveCalendar() != null) {
            String calendarName = cManager.getActiveCalendar().getDisplayName();
            spinner.setSelection(availableCalendars.indexOf(calendarName));
        }

    }

    /**
     * Setzt die Version in der About Karte
     */
    private void setVersion() {
        TextView tvVersion = this.findViewById(R.id.about_version);
        String version = "";

        try {
            PackageInfo info = this.getPackageManager().getPackageInfo(this.getPackageName(),0);
            version = info.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        if (!version.equals("")) {
            tvVersion.setText(version);
        }
        else {
            tvVersion.setText(R.string.message_version_unknown);
        }
    }

    /**
     * Füllt die UI-Elemente mit den Daten aus dem Preferences Manager
     */
    private void fillFromPreferences() {

        // Gespeicherte Zugangsdaten, falls vorhanden, einfügen
        if (!pManager.getUrl().equals("")) {
            EditText etURL = this.findViewById(R.id.eT_url);
            etURL.setText(pManager.getUrl());
        }

        if (!pManager.getUser().equals("")) {
            EditText etUser = this.findViewById(R.id.eT_user);
            etUser.setText(pManager.getUser());
        }

        if (!pManager.getPassword().equals("")) {
            EditText etPassword = this.findViewById(R.id.et_pw);
            etPassword.setText(pManager.getPassword());
        }

        // Switch so einstellen, dass der aktuelle Zustand des Service angezeigt wird
        setServiceSwitch(ServiceUtil.checkServiceIsRunning(this,BackgroundService.class));

        Button etSyncFrom = this.findViewById(R.id.service_sync_from);
        try {
            etSyncFrom.setText(DateAndTimeConversion.convertMillisToString(pManager.getSyncFrom()));
        } catch (ParseException e) {
            displayTimeErrorDialog();
            return;
        }

        Button etSyncInterval = this.findViewById(R.id.service_sync_interval);
        try {
            etSyncInterval.setText(DateAndTimeConversion.convertMillisToString(pManager.getSyncInterval()));
        } catch (ParseException e) {
            displayTimeErrorDialog();
            return;
        }

        // Einstellungen für die Erinnerungen abrufen
        ((CheckBox) this.findViewById(R.id.cB_set_reminder)).setChecked(pManager.isReminderActivated());
        ((CheckBox) this.findViewById(R.id.cB_inteligent_reminder)).setChecked(pManager.isInteligentReminderActivated());

        ((CheckBox) this.findViewById(R.id.cB_replace_existing)).setChecked(pManager.isReplaceExistingActivated());

    }

    /**
     * Schreibt die vom Nutzer eingegebenen Daten in den Preferences Manager
     */
    private void savePrefs() {

        EditText etURL = this.findViewById(R.id.eT_url);
        EditText etUser = this.findViewById(R.id.eT_user);
        EditText etPassword = this.findViewById(R.id.et_pw);

        pManager.setUrl(etURL.getText().toString());
        pManager.setUser(etUser.getText().toString());
        pManager.setPassword(etPassword.getText().toString());

        // Sync Einstellungen
        pManager.setSyncDisabled(!((Switch) this.findViewById(R.id.switch_service)).isChecked());
        // Sync Intervall und Startpunkt werden über die TextChange Events gespeichert, da bei Änderungen dieser Eigenschaften auch der Hintergrundservice angefasst werden muss

        // Erinnerungseinstellungen
        pManager.setReminder(((CheckBox) this.findViewById(R.id.cB_set_reminder)).isChecked());
        pManager.setInteligentReminder(((CheckBox) this.findViewById(R.id.cB_inteligent_reminder)).isChecked());

        pManager.setReplaceExisting(((CheckBox) this.findViewById(R.id.cB_replace_existing)).isChecked());

        // Die Id des aktiven Kalenders wird über die spinnerSelectionChanged Methode automatisch auf dem aktuellen Stand gehalten
        pManager.save();
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

    /**
     * Zeigt dem Nutzer eine Nachricht und einen Fortschritt anan
     */
    @Override
    public void publishProgress(String message, int current, int max ) {

        if (message != null) {
            TextView tv = this.findViewById(R.id.tV_progress);
            tv.setText(message);
        }

        if (current > -1 || max > -1) {
            setProgressbarState(true);
            ProgressBar pB = this.findViewById(R.id.progressBar);

            if (pB.getMax() != max && max > -1) {
                pB.setMax(max);
            }

            if (current > -1) {
                pB.setProgress(current);
            }
        }

    }

    /**
     * Setzt die Progressbar auf den Anzeigestatus determintate oder indeterminate
     * @param determinate true = determinate, false = indeterminate
     */
    public void setProgressbarState(boolean determinate) {
        ProgressBar pB = this.findViewById(R.id.progressBar);
        boolean indeterminate = !determinate;


        if (pB.isIndeterminate() != indeterminate) {
            pB.setIndeterminate(indeterminate);
        }
    }

    /**
     * Hängt eine Nachricht an die zuletzt angezeigte Nachricht an
     * @param message Anzuhängende Nachricht. Es wird automatisch der folgende Trenner eingefügt: " - "
     */
    @Override
    public void appendProgress(String message) {
        TextView tv = this.findViewById(R.id.tV_progress);
        tv.append(" - ");
        tv.append(message);
    }

    @Override
    public void switchCalendarUIElements(boolean enable) {

        this.findViewById(R.id.bt_startDownload).setEnabled(enable);
        this.findViewById(R.id.bt_deleteAll).setEnabled(enable);
        this.findViewById(R.id.bt_emptyCalendar).setEnabled(enable);

    }

    /**
     * Prüft, ob die benötigten Berechtigungen zum Zugriff auf den Kalender vorliegen
     * @param requestCode Ein Anwenderdefinierter Marker
     */
    public void checkPermission(final int requestCode) {
        if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED  ) {
            // Keine Erlaubnis vorhanden.

            final MainActivity activity = this;

            // https://stackoverflow.com/a/31279458/9854602
            final Dialog informationDialog = new Dialog(MainActivity.this);

            informationDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            informationDialog.setCancelable(false);
            informationDialog.setContentView(R.layout.layout_information_dialog);

            Button dialogButton = (Button) informationDialog.findViewById(R.id.info_button);
            dialogButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    informationDialog.dismiss();
                    ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.WRITE_CALENDAR, Manifest.permission.READ_CALENDAR}, requestCode);
                }
            });

            informationDialog.show();

            // Nutzer nach der Erlaubnis fragen
            // ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.WRITE_CALENDAR, Manifest.permission.READ_CALENDAR}, requestCode);

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

    /**
     * Zeigt den Dialog der bei fehlerhafter Eingabe der Zeiten angezeigt werden soll.
     */
    private void displayTimeErrorDialog() {

        final MainActivity activity = this;
        final Dialog errorDialog = new Dialog(MainActivity.this);

        errorDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        errorDialog.setCancelable(false);
        errorDialog.setContentView(R.layout.layout_information_malformed_time);

        Button dialogButton = (Button) errorDialog.findViewById(R.id.info_button);
        dialogButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                errorDialog.dismiss();
            }
        });

        errorDialog.show();
    }

    /**
     * Zeigt einen Auswahldialog für Zeiten an.
     */
    public void showStartTimePickerDialog() {

        DialogFragment newFragment = new TimePickerFragment();
        Bundle b = new Bundle();

        try {
            b.putInt("hour",DateAndTimeConversion.getHoursOfMillis(pManager.getSyncFrom()));
            b.putInt("minute",DateAndTimeConversion.getMinutesOfMillis(pManager.getSyncFrom()));
        } catch (ParseException e) {
            publishError("Es ist ein Fehler beim Laden der Startzeit für den Hintergrundservice aus dem Speicher aufgetreten!");
            e.printStackTrace();
            return;
        }

        b.putByte("mode",(byte) 2);

        newFragment.setArguments(b);
        newFragment.show(getSupportFragmentManager(), "start");

        }

    /**
     * Zeigt einen Auswahldialog für Zeiten an.
     */
    public void showIntervalTimePickerDialog() {

        DialogFragment newFragment = new TimePickerFragment();
        Bundle b = new Bundle();

        try {
            b.putInt("hour",DateAndTimeConversion.getHoursOfMillis(pManager.getSyncInterval()));
            b.putInt("minute",DateAndTimeConversion.getMinutesOfMillis(pManager.getSyncInterval()));
        } catch (ParseException e) {
            publishError("Es ist ein Fehler beim Laden der Startzeit für den Hintergrundservice aus dem Speicher aufgetreten!");
            e.printStackTrace();
            return;
        }

        b.putByte("mode",(byte) 1);

        newFragment.setArguments(b);
        newFragment.show(getSupportFragmentManager(), "interval");
    }

    /**
     * Callbackmethode für die Time Picker
     */
    public void onTimeSet(byte mode, int hourOfDay, int minute) {

        switch (mode){

            case (byte) 1:
                changeInterval(hourOfDay,minute);
                break;

            case (byte)2:
                changeStart(hourOfDay,minute);
                break;

        }
    }

    /**
     * Übernimmt Änderungen des Intervalls in den Langzeitspeicher und startet den Hintergrundservice neu
     */
    private void changeInterval(int hourOfDay, int minute) {

        // Eingabe in Millisekunden umwandeln
        long timeMilliSeconds = 0;

        try {
            timeMilliSeconds= DateAndTimeConversion.convertHourAndMinuteToMillis(hourOfDay,minute);
            ((Button) this.findViewById(R.id.service_sync_interval)).setText(DateAndTimeConversion.convertMillisToString(timeMilliSeconds));
        }
        catch (Exception e) {
            displayTimeErrorDialog();
            //publishError("Ungültiger Eingabewert! Erwartet wird: HH:mm (HH <=23, mm<= 59)");
            return;
        }


        // Prüfe, ob sich die Daten geändert haben
        if (pManager.getSyncInterval() != timeMilliSeconds) {
            // Daten wurden geändert

            // Neue Daten in den PreferencesManager schreiben
            pManager.setSyncInterval(timeMilliSeconds);
            pManager.save();

            restartService();
        }

        restartService();

    }

    /**
     * Übernimmt Änderungen des Intervalls in den Langzeitspeicher und startet den Hintergrundservice neu
     */
    private void changeStart(int hourOfDay, int minute) {

        // Eingabe in Millisekunden umwandeln
        long timeMilliSeconds = 0;

        try {
            timeMilliSeconds= DateAndTimeConversion.convertHourAndMinuteToMillis(hourOfDay,minute);
            ((Button) this.findViewById(R.id.service_sync_from)).setText(DateAndTimeConversion.convertMillisToString(timeMilliSeconds));
        }
        catch (Exception e) {
            displayTimeErrorDialog();
            //publishError("Ungültiger Eingabewert! Erwartet wird: HH:mm (HH <=23, mm<= 59)");
            return;
        }


        // Prüfe, ob sich die Daten geändert haben
        if (pManager.getSyncFrom() != timeMilliSeconds) {
            // Daten wurden geändert

            // Neue Daten in den PreferencesManager schreiben
            pManager.setSyncFrom(timeMilliSeconds);
            pManager.save();

            restartService();
        }

        restartService();

    }

    /**
     * Startet den Hintergrundservice neu.
     */
    private void restartService() {

        // Hintergrundservice stoppen
        ServiceUtil.stopService(getApplicationContext());

        // Service neustarten
        try {
            ServiceUtil.startService(getApplicationContext(), pManager.getSyncFrom(),pManager.getSyncInterval());
        } catch (ParseException e) {
            publishError(getString(R.string.error_service_times_unchangeable));
            e.printStackTrace();
        }

        change_service_indicator();

    }

    /**
     * Startet den AsyncTaskLoader (Hintergrundthread) für den Datendownload
     */
    private void startLoader() {

        EditText user = this.findViewById(R.id.eT_user);
        EditText password = this.findViewById(R.id.et_pw);
        EditText url = this.findViewById(R.id.eT_url);

        // Daten aus den TextViews abfragen
        if (user.getText().toString().equals("") || password.getText().toString().equals("") || url.getText().toString().equals("")) {
            displayMessage("Bitte fülle alle Felder aus!", Snackbar.LENGTH_LONG);
            return;
        }

        // Eingabe speichern
        savePrefs();

        // Die einzelnen Parameter hinzufügen und im Preferences Manager speichern
        ArrayList<ServerParameter> parameters = new ArrayList<>();
        ServerParameter param = new ServerParameter("url", url.getText().toString());
        parameters.add(param);

        param = new ServerParameter("user", user.getText().toString());
        parameters.add(param);

        param = new ServerParameter("pw", password.getText().toString());
        parameters.add(param);

        // AsyncTask mit den Parametern starten
        taskManager = new AsyncTaskManager(getSupportLoaderManager(), getApplicationContext(),this,cManager, pManager, messageFromBackground, progressValue,progressMax);
        taskManager.startDownload(this,parameters);

    }

    private void setServiceSwitch(boolean checked) {
        ((Switch) this.findViewById(R.id.switch_service)).setChecked(checked);
        this.findViewById(R.id.service_sync_interval).setEnabled(checked);
        this.findViewById(R.id.service_sync_from).setEnabled(checked);
    }

    /**
     * Ändert den Anzeigestatus und zwei Einstellungselemente auf Basis des Hintergrundstatus
     */
    private void change_service_indicator() {

        boolean res = ServiceUtil.checkServiceIsRunning(getApplicationContext(),BackgroundService.class);
        SwitchIconView siv = ((SwitchIconView) this.findViewById(R.id.switch_icon_view));

        if (siv.isIconEnabled() != res) {
            siv.switchState();
        }

        // Felder ein- bzw. ausschalten
        this.findViewById(R.id.service_sync_interval).setEnabled(res);
        this.findViewById(R.id.service_sync_from).setEnabled(res);

    }

    /**
     * Schaltet alle UI-Elemente ein oder aus
     */
    private void changeUiElementsEnabled(ViewGroup viewGroup, boolean enabled) {

        int childCount = viewGroup.getChildCount();

        for (int i = 0; i < childCount; i++) {

            // Der Button zum erneuten Anzeigen der Berechtigung darf nicht abgeschaltet werden
            if (viewGroup.getChildAt(i) instanceof Button) {
                Button bt = (Button) viewGroup.getChildAt(i);
                if (!bt.getText().equals(getResources().getString(R.string.bt_rights_managment))) {
                    // Es ist nicht der gesuchte Button -> Abschalten/Einschalten
                    bt.setEnabled(enabled);
                }
            }
            else {
                View view = viewGroup.getChildAt(i);
                view.setEnabled(enabled);
            }

            // Prüfen ob das aktuelle Viewobjekt Kinder hat
            try {
                if (((ViewGroup) viewGroup.getChildAt(i)).getChildCount() > 0) {
                    changeUiElementsEnabled((ViewGroup) viewGroup.getChildAt(i), enabled);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }
}

