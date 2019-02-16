package firesoft.de.kalenderadapter.fragments;

import android.app.Dialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.widget.TimePicker;

import firesoft.de.kalenderadapter.MainActivity;
import firesoft.de.kalenderadapter.R;

public class TimePickerFragment extends DialogFragment implements TimePickerDialog.OnTimeSetListener {

    TimePickerDialog tp;
    byte mode;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        Bundle b = getArguments();

        int hour = b.getInt("hour");
        int minute = b.getInt("minute");
        mode = b.getByte("mode");

        tp = new TimePickerDialog(getActivity(), R.style.DialogTheme, this, hour, minute, true);

//        switch (mode) {
//
//            case 1:
//                // Intervall
//                tp.setTitle("Bitte wählen Sie ein Intervall aus!");
//                break;
//
//            case 2:
//                // Start
//                tp.setTitle("Bitte wählen Sie einen Startzeitpunkt aus!");
//                break;
//
//        }

        // Create a new instance of TimePickerDialog and return it
        return tp;
    }


    @Override
    public void onTimeSet(TimePicker view, int hourOfDay, int minute) {

        ((MainActivity) getActivity()).onTimeSet(mode,hourOfDay,minute);

    }
}
