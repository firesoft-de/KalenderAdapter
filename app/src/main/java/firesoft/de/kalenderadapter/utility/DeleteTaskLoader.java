package firesoft.de.kalenderadapter.utility;

import android.arch.lifecycle.MutableLiveData;
import android.support.v4.content.AsyncTaskLoader;
import android.content.Context;

import firesoft.de.kalenderadapter.R;
import firesoft.de.kalenderadapter.data.ResultWrapper;
import firesoft.de.kalenderadapter.interfaces.IErrorCallback;
import firesoft.de.kalenderadapter.manager.CalendarManager;

public class DeleteTaskLoader extends AsyncTaskLoader<ResultWrapper> implements IErrorCallback {

    private CalendarManager cManager;
    private MutableLiveData<String> progress;
    private MutableLiveData<Integer> progressValue;
    private MutableLiveData<Integer> progressMax;

    public DeleteTaskLoader(Context context, CalendarManager cManager, MutableLiveData<String> progress, MutableLiveData<Integer> progressValue, MutableLiveData<Integer> progressMax) {

        super(context);

        this.cManager = cManager;
        this.progress = progress;
        this.progressValue = progressValue;
        this.progressMax = progressMax;

        cManager.redefineErrorCallback(this);
    }

    @Override
    public ResultWrapper loadInBackground() {

        // Neue Liste der hinzugefügten Einträge holen und dann diese Einträge löschen
        cManager.loadCalendarEntries();
        cManager.deleteEntries();

        return new ResultWrapper(getContext().getString(R.string.info_extinction_successfull));
    }

    // region Unwichtige Methoden

    @Override
    public void switchCalendarUIElements(boolean enable) {
        // Hier wird nichts gemacht
    }

    @Override
    protected void onStartLoading() {
        // Kann so nicht stehen bleiben. Ohne forceLoad() wird der enthaltene Code überhaupt nicht ausgeführt :/
        forceLoad();
    }

    // endregion

    // region Reporting Methoden

    /**
     * Ermöglicht es threadsichere Meldungen an den Nutzer auszugeben
     * @param message Nachricht die angezeigt werden soll
     */
    @Override
    public void publishError(String message) {
        progress.postValue(message);
    }

    /**
     * Ermöglicht es threadsichere Meldungen an den Nutzer auszugeben
     * @param message Nachricht die angezeigt werden soll
     */
    @Override
    public void publishProgress(String message, int progressVal, int progressMx) {
        progress.postValue(message);
        progressMax.postValue(progressMx);
        progressValue.postValue(progressVal);
    }

    @Override
    public void appendProgress(String message) {
        progress.postValue(progress.getValue() + " - " + message);
    }

    // endregion

}
