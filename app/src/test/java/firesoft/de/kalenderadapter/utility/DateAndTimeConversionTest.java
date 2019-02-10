/*
 * Copyright (c) 2019.  David Schlossarczyk
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

package firesoft.de.kalenderadapter.utility;

import android.text.method.DateTimeKeyListener;

import org.junit.Before;
import org.junit.Test;

import java.text.ParseException;
import java.util.Calendar;

import static org.junit.Assert.*;

public class DateAndTimeConversionTest {

    @Test
    public void attachEpochShouldAlwaysBeInTheFuture() {
        long calculatedStartTime = 0;
        try {
            // 10 Millisekunden nach Mitternacht
            calculatedStartTime = DateAndTimeConversion.attachEpoch(10);
        } catch (ParseException e) {
            e.printStackTrace();
        }

        Calendar checkCalendar = Calendar.getInstance();

        boolean check = calculatedStartTime > checkCalendar.getTimeInMillis();

        assertTrue(check);
    }

    @Test(expected = ParseException.class)
    public void attachEpochShouldFailWithNegativInput() throws ParseException {
        DateAndTimeConversion.attachEpoch(-10);
    }

    @Test
    public void convertMillisToStringShouldProvideValidResult1() throws ParseException {
        String result = DateAndTimeConversion.convertMillisToString(120*1000);
        assertEquals("00:02",result);
    }

    @Test
    public void convertMillisToStringShouldProvideValidResult2() throws ParseException {
        String result = DateAndTimeConversion.convertMillisToString(140400000);
        assertEquals("15:00", result);
    }

    @Test
    public void convertMillisToStringShouldProvideValidResult3() throws ParseException {
        String result = DateAndTimeConversion.convertMillisToString(82800000);
        assertEquals("23:00", result);
    }

    @Test
    public void convertMillisToStringShouldProvideValidResult4() throws ParseException {
        String result = DateAndTimeConversion.convertMillisToString(86340000);
        assertEquals("23:59", result);
    }

    @Test(expected = ParseException.class)
    public void convertMillisToStringShouldRecogniseWrongInput() throws ParseException {
        DateAndTimeConversion.convertMillisToString(-120);
    }



}