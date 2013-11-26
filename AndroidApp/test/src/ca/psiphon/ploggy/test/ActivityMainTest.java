package ca.psiphon.ploggy.test;

import android.app.ActionBar;
import android.app.Instrumentation;
import android.content.pm.ActivityInfo;
import android.test.ActivityInstrumentationTestCase2;
import android.test.UiThreadTest;
import ca.psiphon.ploggy.ActivityMain;

public class ActivityMainTest extends
        ActivityInstrumentationTestCase2<ActivityMain> {

    private Instrumentation mInstr;
    private ActivityMain mActivity;
    private ActionBar mActionBar;

    public ActivityMainTest() {
        super(ActivityMain.class);
    }

    @Override
    protected void setUp() throws Exception {
      super.setUp();

      setActivityInitialTouchMode(false);

      mInstr = this.getInstrumentation();
      mActivity = getActivity();
      mActionBar = mActivity.getActionBar();
    }

    public void testPreConditions() {
        assertTrue(true);
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

}
