package ca.psiphon.ploggy.test;

import android.app.ActionBar;
import android.app.Instrumentation;
import android.content.pm.ActivityInfo;
import android.graphics.Point;
import android.test.ActivityInstrumentationTestCase2;
import android.test.UiThreadTest;
import ca.psiphon.ploggy.ActivityMain;

import com.jayway.android.robotium.solo.Solo;

public class ActivityMainTest extends
        ActivityInstrumentationTestCase2<ActivityMain> {

    private Instrumentation mInstr;
    private ActivityMain mActivity;
    private ActionBar mActionBar;
    private Solo solo;

    public ActivityMainTest() {
        super(ActivityMain.class);
    }

    @Override
    protected void setUp() throws Exception {
      super.setUp();

      solo = new Solo(getInstrumentation(), getActivity());

      setActivityInitialTouchMode(false);

      mInstr = this.getInstrumentation();
      mActivity = getActivity();
      mActionBar = mActivity.getActionBar();
    }

    @Override
    protected void tearDown() throws Exception {
        solo.finishOpenedActivities();
    }

    @UiThreadTest
    public void testStateSaveRestore() {
        //
        // Destroy/Create
        //

        // Check that we're starting on the first tab
        assertEquals(mActionBar.getSelectedNavigationIndex(), 0);

        // Select the second tab
        mActionBar.setSelectedNavigationItem(1);

        // Destroy the activity, which should save the state
        mActivity.finish();

        // Recreate the activity...
        mActivity = this.getActivity();

        // ...which should cause it to restore state
        assertEquals(mActionBar.getSelectedNavigationIndex(), 1);
    }

    @UiThreadTest
    public void testRotation() {
      // Rotate back and forth. Was crashing at one point.
      mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
      mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
      mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
    }

    protected void swipe(boolean left) {
        Point size = new Point();
        mActivity.getWindowManager().getDefaultDisplay().getSize(size);
        int width = size.x;
        float xStart = (left ? (width - 10) : 10);
        float xEnd = (left ? 10 : (width - 10));

        // The value for y doesn't change, as we want to swipe straight across
        solo.drag(xStart, xEnd, size.y / 2, size.y / 2, 1);
    }

    public void testSwipeTabChange() {
        // Select the first tab
        mActionBar.setSelectedNavigationItem(0);

        // Swipe to the next tab
        swipe(true);

        assertEquals(mActionBar.getSelectedNavigationIndex(), 1);
    }
}
