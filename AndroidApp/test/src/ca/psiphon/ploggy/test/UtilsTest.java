package ca.psiphon.ploggy.test;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import android.test.AndroidTestCase;
import android.test.MoreAsserts;
import android.util.Log;
import ca.psiphon.ploggy.Utils;

public class UtilsTest extends AndroidTestCase {

    public UtilsTest() {
        super();
    }

    @Override
    protected void setUp() throws Exception {
      super.setUp();

      assertTrue(true);
    }

    public void testDateFormatter_formatRelativeDatetime() {
        // We'll force the use of a date, otherwise tests will fail if run
        // between midnight and 1am (because there'll be no time that's "greater
        // than an hour" but also "within the same day".
        Calendar calNow = new GregorianCalendar(TimeZone.getDefault());
        calNow.set(2013, 10, 22, 23, 01, 02); // Friday 2013-11-22T23:01:02 (local TZ)

        Date within1Minute = new Date(calNow.getTimeInMillis() - 30000);
        Date within1Hour = new Date(calNow.getTimeInMillis() - 1800000);
        Date withinSameDay = new Date(calNow.getTimeInMillis() - 9000000);
        Date withinSameWeek = new Date(calNow.getTimeInMillis() - 259200000);
        Date withinSameYear = new Date(calNow.getTimeInMillis() - 1036800000);
        Date moreThanSameYear = new Date(calNow.getTimeInMillis() - 34560000000L);

        // TODO: Make these locale-friendly? (Without just duplicating the function being tested...)

        // ago = false

        MoreAsserts.assertContainsRegex(
                "^\\d{1,2} secs$",
                Utils.DateFormatter.formatRelativeDatetime(getContext(), within1Minute, calNow.getTime(), false));

        MoreAsserts.assertContainsRegex(
                "^\\d{1,2} mins$",
                Utils.DateFormatter.formatRelativeDatetime(getContext(), within1Hour, calNow.getTime(), false));

        MoreAsserts.assertContainsRegex(
                "^\\d{1,2}:\\d{2} (AM|PM)$",
                Utils.DateFormatter.formatRelativeDatetime(getContext(), withinSameDay, calNow.getTime(), false));

        MoreAsserts.assertContainsRegex(
                "^\\w{3}, \\d{1,2}:\\d{2} (AM|PM)$",
                Utils.DateFormatter.formatRelativeDatetime(getContext(), withinSameWeek, calNow.getTime(), false));

        MoreAsserts.assertContainsRegex(
                "^\\w{3} \\d{1,2}, \\d{1,2}:\\d{2} (AM|PM)$",
                Utils.DateFormatter.formatRelativeDatetime(getContext(), withinSameYear, calNow.getTime(), false));

        MoreAsserts.assertContainsRegex(
                "^\\w{3} \\d{1,2} \\d{4}, \\d{1,2}:\\d{2} (AM|PM)$",
                Utils.DateFormatter.formatRelativeDatetime(getContext(), moreThanSameYear, calNow.getTime(), false));

        // ago = true

        MoreAsserts.assertContainsRegex(
                "^\\d{1,2} secs ago$",
                Utils.DateFormatter.formatRelativeDatetime(getContext(), within1Minute, calNow.getTime(), true));

        MoreAsserts.assertContainsRegex(
                "^\\d{1,2} mins ago$",
                Utils.DateFormatter.formatRelativeDatetime(getContext(), within1Hour, calNow.getTime(), true));

        MoreAsserts.assertContainsRegex(
                "^\\d{1,2}:\\d{2} (AM|PM)$",
                Utils.DateFormatter.formatRelativeDatetime(getContext(), withinSameDay, calNow.getTime(), true));

        MoreAsserts.assertContainsRegex(
                "^\\w{3}, \\d{1,2}:\\d{2} (AM|PM)$",
                Utils.DateFormatter.formatRelativeDatetime(getContext(), withinSameWeek, calNow.getTime(), true));

        MoreAsserts.assertContainsRegex(
                "^\\w{3} \\d{1,2}, \\d{1,2}:\\d{2} (AM|PM)$",
                Utils.DateFormatter.formatRelativeDatetime(getContext(), withinSameYear, calNow.getTime(), true));

        MoreAsserts.assertContainsRegex(
                "^\\w{3} \\d{1,2} \\d{4}, \\d{1,2}:\\d{2} (AM|PM)$",
                Utils.DateFormatter.formatRelativeDatetime(getContext(), moreThanSameYear, calNow.getTime(), true));

        /*
        Date start = new Date();
        int i = 0;
        for (; i < 100; i++) {
            Utils.DateFormatter.formatRelativeDatetime(getContext(), within1Minute, calNow.getTime(), true);
            Utils.DateFormatter.formatRelativeDatetime(getContext(), within1Hour, calNow.getTime(), true);
            Utils.DateFormatter.formatRelativeDatetime(getContext(), withinSameDay, calNow.getTime(), true);
            Utils.DateFormatter.formatRelativeDatetime(getContext(), withinSameWeek, calNow.getTime(), true);
            Utils.DateFormatter.formatRelativeDatetime(getContext(), withinSameYear, calNow.getTime(), true);
            Utils.DateFormatter.formatRelativeDatetime(getContext(), moreThanSameYear, calNow.getTime(), true);
        }
        Date end = new Date();
        Log.i(this.getName(), String.format("%d iterations: %d ms", i, end.getTime() - start.getTime()));
        */
    }
}
