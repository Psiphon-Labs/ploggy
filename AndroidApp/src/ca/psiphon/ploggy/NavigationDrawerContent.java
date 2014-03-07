/*
 * Copyright (c) 2014, Psiphon Inc.
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

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.os.AsyncTask;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

public class NavigationDrawerContent {

    private static final String LOG_TAG = "Navigation Drawer Content";

    public interface Row {

        public static enum Type {
            HEADER,
            ITEM
        };

        public Type getRowType();
        public View getRowView(LayoutInflater inflater, View convertView);
    }

    public static class Adapter extends ArrayAdapter<Row> {

        public Adapter(Context context) {
            super(context, 0);
            update();
        }

        // Execute database queries on background thread
        private static class MakeRowArrayAsyncTask extends AsyncTask<Object, Object, List<Row>> {
            private final Adapter mAdapter;
            MakeRowArrayAsyncTask(Adapter adapter) {
                mAdapter = adapter;
            }
            @Override
            protected List<Row> doInBackground(Object... params) {
                try {
                    Data data = Data.getInstance();
                    Context context = mAdapter.getContext();
                    List<Row> rows = new ArrayList<Row>();
                    rows.add(new Item(
                            new ActivityMain.ViewTag(ActivityMain.ViewType.SELF_DETAIL),
                            R.drawable.ic_navigation_drawer_self_detail,
                            context.getString(R.string.navigation_drawer_item_self_detail));
                    rows.add(new Item(
                            new ActivityMain.ViewTag(ActivityMain.ViewType.GROUP_LIST),
                            R.drawable.ic_navigation_drawer_group_list,
                            context.getString(R.string.navigation_drawer_item_group_list));
                    rows.add(new Item(
                            new ActivityMain.ViewTag(ActivityMain.ViewType.FRIEND_LIST),
                            R.drawable.ic_navigation_drawer_friend_list,
                            context.getString(R.string.navigation_drawer_item_friend_list));
                    // *TODO* only show if > 0 candidate friends
                    rows.add(new Item(
                            new ActivityMain.ViewTag(ActivityMain.ViewType.CANDIDATE_FRIEND_LIST),
                            R.drawable.ic_navigation_drawer_candidate_friend_list,
                            context.getString(R.string.navigation_drawer_item_candidate_friend_list));
                    rows.add(new Item(
                            new ActivityMain.ViewTag(ActivityMain.ViewType.LOG_ENTRIES),
                            R.drawable.ic_navigation_drawer_log_entries,
                            context.getString(R.string.navigation_drawer_item_log_entries));
                    rows.add(
                        new Header(context.getString(R.string.navigation_draw_header_groups)));
                    // TODO: only display N most recent groups/friends
                    // TODO: for groups, display counter with number of unread posts
                    for (Data.Group group : data.getGroupsIterator()) {
                        rows.add(new Item(
                                new ActivityMain.ViewTag(ActivityMain.ViewType.GROUP_DETAIL, group.mGroup.mId),
                                R.drawable.ic_navigation_drawer_group_detail,
                                group.mGroup.mName));
                    }
                    rows.add(
                        new Header(context.getString(R.string.navigation_draw_header_friends)));
                    for (Data.Friend friend : data.getFriendsIterator()) {
                        rows.add(new Item(
                                new ActivityMain.ViewTag(ActivityMain.ViewType.FRIEND_DETAIL, friend.mId),
                                R.drawable.ic_navigation_drawer_friend_detail,
                                friend.mPublicIdentity.mNickname));
                    }
                    return rows;
                } catch (PloggyError e) {
                    Log.addEntry(LOG_TAG, "make navigation drawer content failed");
                    return null;
                }
            }
            @Override
            protected void onPostExecute(List<Row> rows) {
                if (rows != null) {
                    mAdapter.clear();
                    mAdapter.addAll(rows);
                    mAdapter.notifyDataSetChanged();
                }
            }
        }

        public void update() {
            MakeRowArrayAsyncTask task = new MakeRowArrayAsyncTask(this);
            task.execute();
        }

        @Override
        public int getViewTypeCount() {
            return Row.Type.values().length;
        }

        @Override
        public int getItemViewType(int position) {
            return getItem(position).getRowType().ordinal();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LayoutInflater inflater = (LayoutInflater)getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            return getItem(position).getRowView(inflater, convertView);
        }
    }

    public static class Header implements Row {

        private final String mText;

        public Header(String text) {
            mText = text;
        }

        @Override
        public Row.Type getRowType() {
            return Row.Type.HEADER;
        }

        @Override
        public View getRowView(LayoutInflater inflater, View convertView) {
            View view;
            if (convertView == null) {
                view = inflater.inflate(R.layout.navigation_drawer_header, null);
            } else {
                view = convertView;
            }

            TextView text = (TextView) view.findViewById(R.id.navigation_drawer_header_text);
            text.setText(mText);

            return view;
        }
    }

    public static class Item implements Row {

        private final ActivityMain.ViewTag mViewTag;
        private final int mIconResourceId;
        private final String mText;

        public Item(ActivityMain.ViewTag viewTag, int iconResourceId, String text) {
            mViewTag = viewTag;
            mIconResourceId = iconResourceId;
            mText = text;
        }

        public ActivityMain.ViewTag getViewTag() {
            return mViewTag;
        }

        @Override
        public Row.Type getRowType() {
            return Row.Type.ITEM;
        }

        @Override
        public View getRowView(LayoutInflater inflater, View convertView) {
            View view;
            if (convertView == null) {
                view = inflater.inflate(R.layout.navigation_drawer_item, null);
            } else {
                view = convertView;
            }

            ImageView icon = (ImageView) view.findViewById(R.id.navigation_drawer_item_icon);
            TextView text = (TextView) view.findViewById(R.id.navigation_drawer_item_text);
            icon.setImageResource(mIconResourceId);
            text.setText(mText);

            return view;
        }
    }
}