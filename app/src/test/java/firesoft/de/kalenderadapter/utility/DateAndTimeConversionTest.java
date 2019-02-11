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
    public void attachEpochShouldAlwaysBeInTheFutureWithSmallDifferences() {

        Calendar cal = Calendar.getInstance();
        long calculatedStartTime = (cal.get(Calendar.MINUTE) * 60 + cal.get(Calendar.SECOND) + cal.get(Calendar.HOUR_OF_DAY) * 60 * 60) * 1000 + cal.get(Calendar.MILLISECOND) ;
        try {
            // 10 Millisekunden nach Mitternacht
            calculatedStartTime = DateAndTimeConversion.attachEpoch(calculatedStartTime);
        } catch (ParseException e) {
            e.printStackTrace();
        }

        boolean check = calculatedStartTime > cal.getTimeInMillis();

        assertTrue(check);
    }

    @Test
    public void attachEpochShouldAlwaysBeInTheFutureEvenWithInputOneHourAhead() {

        Calendar cal = Calendar.getInstance();
        long calculatedStartTime = (cal.get(Calendar.MINUTE) * 60 + cal.get(Calendar.SECOND) + cal.get(Calendar.HOUR_OF_DAY) * 60 * 60 + 3600) * 1000 + cal.get(Calendar.MILLISECOND) ;
        try {
            // 10 Millisekunden nach Mitternacht
            calculatedStartTime = DateAndTimeConversion.attachEpoch(calculatedStartTime);
        } catch (ParseException e) {
            e.printStackTrace();
        }

        boolean check = calculatedStartTime > cal.getTimeInMillis();

        assertTrue(check);
    }

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
    public void convertStringToMillisShouldFailWhenMalformed() throws ParseException {
        DateAndTimeConversion.convertStringToMillis("AA.K");
    }

    @Test
    public void convertStringToMillisShouldSuccedWhenWellFormed() throws ParseException {
        long result = DateAndTimeConversion.convertStringToMillis("01:16");
        assertEquals(4560000,result);
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

    @Test
    public void getHoursOfMillisShouldProvideValidResults() throws ParseException{
        int hours = DateAndTimeConversion.getHoursOfMillis(86340000);
        assertEquals(23,hours);
    }

    @Test
    public void getMinutesOfMillisShouldProvideValidResults() throws ParseException{
        int hours = DateAndTimeConversion.getMinutesOfMillis(86340000);
        assertEquals(59,hours);
    }

    @Test(expected = ParseException.class)
    public void convertHourAndMinuteToMillisShouldThrowExceptionWhenInvalidFormat1() throws ParseException {
        DateAndTimeConversion.convertHourAndMinuteToMillis(40,100);
    }

    @Test(expected = ParseException.class)
    public void convertHourAndMinuteToMillisShouldThrowExceptionWhenInvalidFormat2() throws ParseException {
        DateAndTimeConversion.convertHourAndMinuteToMillis(-10,10);
    }

    @Test
    public void convertHourAndMinuteToMillisShouldProvideValidResults1() throws ParseException {
        long result = DateAndTimeConversion.convertHourAndMinuteToMillis(0,0);
        assertEquals(0,result);
    }

    @Test
    public void convertHourAndMinuteToMillisShouldProvideValidResultsWithHoursBiggerThan24Hours() throws ParseException {
        long result = DateAndTimeConversion.convertHourAndMinuteToMillis(40,0);
        assertEquals(144000000,result);
    }

    @Test
    public void convertHourAndMinuteToMillisShouldProvideValidResults2() throws ParseException {
        long result = DateAndTimeConversion.convertHourAndMinuteToMillis(0,59);
        assertEquals(3540000,result);
    }

}