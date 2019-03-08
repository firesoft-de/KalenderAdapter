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

import android.Manifest;
import android.app.Activity;
import android.arch.lifecycle.MutableLiveData;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.Loader;

import java.util.ArrayList;

import firesoft.de.kalenderadapter.R;
import firesoft.de.kalenderadapter.data.ResultWrapper;
import firesoft.de.kalenderadapter.data.ServerParameter;
import firesoft.de.kalenderadapter.interfaces.IErrorCallback;
import firesoft.de.kalenderadapter.utility.DataTool;
import firesoft.de.kalenderadapter.utility.DeleteTaskLoader;

public class AsyncTaskManager implements LoaderManager.LoaderCallbacks<ResultWrapper> {

    //=======================================================
    //=====================VARIABLEN=========================
    //=======================================================

    private LoaderManager loaderManager;
    private Context context;
    private IErrorCallback errorCallback;
    private ArrayList<ServerParameter> params;
    private CalendarManager calendarManager;
    private PreferencesManager pManager;
    private MutableLiveData<String> progress;
    private MutableLiveData<Integer> progressValue;
    private MutableLiveData<Integer> progressMax;

    private static final int MAIN_LOADER = 1;
    private static final int DELETE_LOADER = 2;

    //=======================================================
    //====================KONSTRUKTOR========================
    //=======================================================

    public AsyncTaskManager(android.support.v4.app.LoaderManager loaderManager, Context context, IErrorCallback errorCallback, CalendarManager cManager, PreferencesManager pManager, MutableLiveData<String> progress, MutableLiveData<Integer> progressValue, MutableLiveData<Integer> progressMax) {
        this.loaderManager = loaderManager;
        this.context = context;
        this.errorCallback = errorCallback;
        this.calendarManager = cManager;
        this.pManager = pManager;
        this.progress = progress;
        this.progressMax = progressMax;
        this.progressValue = progressValue;
    }

    //=======================================================
    //==================PUBLIC METHODEN======================
    //=======================================================

    public void startDownload(Activity activity, ArrayList<ServerParameter> params) {
        // Überprüfen, ob die Berechtigung zum Kalenderzugriff vorliegt
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED  ) {
            // Keine Erlaubnis vorhanden.

            // Nutzer nach der Erlaubnis fragen
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.WRITE_CALENDAR, Manifest.permission.READ_CALENDAR}, 1);
        }

        this.params = params;

        initateLoader(MAIN_LOADER);

    }

    /**
     * Beginnt mit dem Löschen der Einträge
     */
    public void startDelete(Activity activity) {
        // Überprüfen, ob die Berechtigung zum Kalenderzugriff vorliegt
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED  ) {
            // Keine Erlaubnis vorhanden.

            // Nutzer nach der Erlaubnis fragen
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.WRITE_CALENDAR, Manifest.permission.READ_CALENDAR}, 1);
        }

        initateLoader(DELETE_LOADER);

    }


    @NonNull
    @Override
    public Loader<ResultWrapper> onCreateLoader(int id, @Nullable Bundle args) {

        switch (id) {

            case MAIN_LOADER:
                return new DataTool(params,context, calendarManager, progress, progressValue, progressMax, pManager, true);

            case DELETE_LOADER:
                return new DeleteTaskLoader(context,calendarManager,progress,progressValue,progressMax);

        }

        throw new IllegalArgumentException("Kein passender Loader verfügbar! (AsyncTaskManager.onCreateLoader))");

    }

    @Override
    public void onLoadFinished(@NonNull Loader<ResultWrapper> loader, ResultWrapper data) {

        // Loader zerstören, da er nicht mehr gebraucht wird und es ansonsten beim Wiederaufrufen der App aus dem Hintergrund (vom Backstack) zu einem ungewollten neustart des Loaders kommen würde.
        this.loaderManager.destroyLoader(loader.getId());

        if (data == null) {
            return;
        }

        if (data.getException() != null) {
            errorCallback.publishError(data.getException().getMessage());
            data.getException().printStackTrace();
        }
        else {
            // Erfolgsmeldung abgeben
            errorCallback.publishProgress(data.getResult(),-1,-1);

        }

    }

    @Override
    public void onLoaderReset(@NonNull Loader loader) {

    }

    /**
     * Die Methode startet die Loader. Durch die Methode werden bereits aktive Loader berücksichtigt und ggf. neugestartet.
     */
    private void initateLoader(int id) {

        //Loader anwerfen
        if (loaderManager.getLoader(id) == null) {
            loaderManager.initLoader(id, null, this);
        } else {
            loaderManager.restartLoader(id, null, this);
        }

    }





}
