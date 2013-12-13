/*
 * Copyright (c) 2013, Psiphon Inc.
 * All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package ca.psiphon.ploggy;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import android.content.Intent;
import android.support.v4.app.Fragment;


/**
 * Support for routing onActivityResult to nested fragments.
 */
public class FragmentWithNestedSupport extends Fragment {

    private static final String LOG_TAG = "Fragment With Nested Support";

    private List<Fragment> mChildFragments;

    public FragmentWithNestedSupport() {
        mChildFragments = new ArrayList<Fragment>();
    }
    
    public void registerChildFragment(Fragment fragment) {
        if (!mChildFragments.contains(fragment)) {
            mChildFragments.add(fragment);
        }
    }
    
    public void unregisterChildFragment(Fragment fragment) {
        mChildFragments.remove(fragment);
    }
    
    public void startActivityForResult(Intent intent, int requestCode) {
        getParentFragment().startActivityForResult(intent, requestCode);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        for (Fragment fragment : mChildFragments) {
            fragment.onActivityResult(requestCode, resultCode, data);
        }
    }
}
