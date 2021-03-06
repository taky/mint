package com.gmail.altakey.mint.fragment;

import android.app.ActionBar;
import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.ListFragment;
import android.app.LoaderManager;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Loader;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.CursorAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import com.example.android.swipedismiss.SwipeDismissListViewTouchListener;
import com.gmail.altakey.mint.R;
import com.gmail.altakey.mint.activity.ConfigActivity;
import com.gmail.altakey.mint.activity.TaskEditActivity;
import com.gmail.altakey.mint.model.Task;
import com.gmail.altakey.mint.provider.TaskProvider;
import com.gmail.altakey.mint.service.ToodledoClientService;
import com.gmail.altakey.mint.util.FilterType;
import com.gmail.altakey.mint.util.LoaderUtil;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Formatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

public class TaskListFragment extends ListFragment
{
    public static final String TAG = "task_list";
    public static final String ARG_FILTER = "filter";

    private TaskListAdapter mAdapter;
    private FilterType mFilterType;
    private ActionMode mActionMode;

    private TaskLoaderManipulator mTaskLoaderManip = new TaskLoaderManipulator();

    public FilterType getFilter() {
        return mFilterType;
    }

    public static TaskListFragment newInstance(FilterType filter) {
        final TaskListFragment f = new TaskListFragment();
        final Bundle args = new Bundle();
        args.putParcelable(ARG_FILTER, filter);
        f.setArguments(args);
        return f;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        final Context context = getActivity();
        final Bundle args = getArguments();

        mAdapter = new TaskListAdapter(getActivity(), null);
        mFilterType = (FilterType)args.getParcelable(ARG_FILTER);

        getActivity().setTitle(mFilterType.getTitle());

        final ListView listView = getListView();
        //listView.setOnItemLongClickListener(new SelectionModeListener());
        listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
        listView.setMultiChoiceModeListener(new TaskSelectionMode());

        final SwipeDismissListViewTouchListener touchListener =
                new SwipeDismissListViewTouchListener(
                        listView,
                        new SwipeDismissListViewTouchListener.DismissCallbacks() {
                            @Override
                            public boolean canDismiss(int position) {
                                return true;
                            }

                            @Override
                            public void onDismiss(ListView listView, int[] reverseSortedPositions) {
                                final Context context = getActivity();
                                if (context != null) {
                                    for (int position : reverseSortedPositions) {
                                        final Cursor c = (Cursor)mAdapter.getItem(position);
                                        final Task t = Task.fromCursor(c, 0);
                                        mAdapter.markToBeRemoved(context, t);
                                    }
                                }
                                //mAdapter.notifyDataSetChanged();
                            }
                        });
        listView.setOnTouchListener(touchListener);
        listView.setOnScrollListener(touchListener.makeScrollListener());

        setHasOptionsMenu(true);
        setListAdapter(mAdapter);
        setListShown(false);
        LoaderUtil.initLoader(1, null, mTaskLoaderManip, getLoaderManager());
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.list, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.main_preferences:
            startActivity(new Intent(getActivity(), ConfigActivity.class));
            return false;
        case R.id.main_post:
            final DialogFragment f = new TaskPostFragment();
            f.setTargetFragment(this, 0);
            f.show(getFragmentManager(), "post_task");
            return false;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onResume() {
        super.onResume();
        LoaderUtil.initLoader(1, null, mTaskLoaderManip, getLoaderManager());
        update();
    }

    @Override
    public void onListItemClick(ListView lv, View v, int position, long id) {
        super.onListItemClick(lv, v, position, id);
        final Intent intent = new Intent(getActivity(), TaskEditActivity.class);
        intent.putExtra(TaskEditActivity.KEY_TASK_ID, id);
        startActivity(intent);
    }

    private void update() {
        final Context context = getActivity();
        final Intent intent = new Intent(context, ToodledoClientService.class);
        intent.setAction(ToodledoClientService.ACTION_SYNC);
        context.startService(intent);
    }

    private class SelectionModeListener implements AdapterView.OnItemLongClickListener {
        @Override
        public boolean onItemLongClick(AdapterView<?> parent, View v, int position, long id) {
            if (mActionMode != null) {
                return false;
            }

            getActivity().startActionMode(new TaskSelectionMode());
            v.setSelected(true);
            return true;
        }
    }

    private class TaskListAdapter extends CursorAdapter {
        private Set<String> mCompleteQueue;

        public TaskListAdapter(Context context, Cursor c) {
            super(context, c);
            init(context);
        }

        public TaskListAdapter(Context context, Cursor c, boolean autoRequery) {
            super(context, c, autoRequery);
            init(context);
        }

        public TaskListAdapter(Context context, Cursor c, int flags) {
            super(context, c, flags);
            init(context);
        }

        private void init(final Context context) {
            final SharedPreferences sp = context.getSharedPreferences("com.gmail.altakey.mint", Context.MODE_PRIVATE);
            mCompleteQueue = sp.getStringSet(ToodledoClientService.KEY_COMPLETE_QUEUE, null);
            if (mCompleteQueue == null) {
                mCompleteQueue = new HashSet<String>();
            }
            processCompleteQueue(context);
        }

        public void markToBeRemoved(final Context context, final Task task) {
            final String contentKey = task.getContentKey();
            mCompleteQueue.add(contentKey);
            final SharedPreferences sp = context.getSharedPreferences("com.gmail.altakey.mint", Context.MODE_PRIVATE);
            sp.edit()
                .putStringSet(ToodledoClientService.KEY_COMPLETE_QUEUE, mCompleteQueue)
                .commit();
            notifyDataSetChanged();
            processCompleteQueue(context);
        }

        private void processCompleteQueue(final Context context) {
            final Intent intent = new Intent(getActivity(), ToodledoClientService.class);
            intent.setAction(ToodledoClientService.ACTION_COMPLETE);
            context.startService(intent);
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            final LayoutInflater inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            final View v = inflater.inflate(R.layout.list_item, parent, false);
            bindView(v, context, cursor);
            return v;
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            final TextView title = (TextView)view.findViewById(R.id.list_task_title);
            final TextView taskFolder = (TextView)view.findViewById(R.id.list_task_folder_0);
            final TextView taskContext = (TextView)view.findViewById(R.id.list_task_context_0);
            final TextView due = (TextView)view.findViewById(R.id.list_task_due);
            final TextView timerFlag = (TextView)view.findViewById(R.id.list_task_timer_flag);
            final View priority = view.findViewById(R.id.list_task_prio);
            final Task task = Task.fromCursor(cursor, 0);
            final Resources res = getResources();

            if (mCompleteQueue.contains(task.getContentKey())) {
                final ViewGroup.LayoutParams lp = view.getLayoutParams();
                lp.height = 1;
                view.setLayoutParams(lp);
            } else {
                final ViewGroup.LayoutParams lp = view.getLayoutParams();
                lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
                view.setLayoutParams(lp);
            }

            title.setText(task.title);
            timerFlag.setText("");

            if (task.duedate > 0) {
                due.setVisibility(View.VISIBLE);
                due.setText(DateUtils.formatDateTime(getActivity(), task.duedate * 1000, DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_NUMERIC_DATE));
            } else {
                due.setVisibility(View.INVISIBLE);
            }

            switch ((int)task.priority) {
            case -1:
                priority.setBackgroundColor(res.getColor(R.color.prio_negative));
                break;
            case 0:
                priority.setBackgroundColor(res.getColor(R.color.prio_low));
                break;
            case 1:
                priority.setBackgroundColor(res.getColor(R.color.prio_normal));
                break;
            case 2:
                priority.setBackgroundColor(res.getColor(R.color.prio_high));
                break;
            case 3:
            default:
                priority.setBackgroundColor(res.getColor(R.color.prio_top));
                break;
            }

            if (task.resolved.folder.isNull()) {
                taskFolder.setVisibility(View.GONE);
            } else {
                taskFolder.setText(task.resolved.folder.name);
                taskFolder.setVisibility(View.VISIBLE);
            }
            if (task.resolved.context.isNull()) {
                taskContext.setVisibility(View.GONE);
            } else {
                taskContext.setText(task.resolved.context.name);
                taskContext.setVisibility(View.VISIBLE);
            }
        }
    }

    private class TaskLoaderManipulator implements LoaderManager.LoaderCallbacks<Cursor> {
        @Override
        public Loader<Cursor> onCreateLoader(int id, Bundle args) {
            setListShown(false);
            return new CursorLoader(
                getActivity(),
                TaskProvider.CONTENT_URI,
                TaskProvider.PROJECTION,
                mFilterType.getSelection(),
                mFilterType.getSelectionArgs(),
                TaskProvider.DEFAULT_ORDER
            );
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
            data.registerContentObserver(new Watcher());
            mAdapter.changeCursor(data);
            setListShown(true);
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {
            mAdapter.changeCursor(null);
        }

        private class Watcher extends ContentObserver {
            public Watcher() {
                super(null);
            }

            @Override
            public void onChange(boolean selfChange) {
                final Activity activity = getActivity();
                if (activity != null) {
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            LoaderUtil.initLoader(1, null, mTaskLoaderManip, getLoaderManager());
                        }
                    });
                }
            }
        }
    }

    private class TaskSelectionMode implements AbsListView.MultiChoiceModeListener {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mActionMode = mode;

            final MenuInflater inflater = mode.getMenuInflater();
            inflater.inflate(R.menu.list_selection, menu);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            switch (item.getItemId()) {
            case R.id.list_selection_delete:
                deleteSelectedItems();
                mode.finish();
                return true;
            default:
                return false;
            }
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            mActionMode = null;
        }

        @Override
        public void onItemCheckedStateChanged(ActionMode mode,
                int position, long id, boolean checked) {
            setTitle(mode);
        }

        private void setTitle(ActionMode mode) {
            final int checkedCount = getListView().getCheckedItemCount();
            switch (checkedCount) {
                case 0:
                    mode.setTitle(null);
                    break;
                case 1:
                    mode.setTitle("One item selected");
                    break;
                default:
                    mode.setTitle(String.format("%d items selected", checkedCount));
                    break;
            }
        }

        private void deleteSelectedItems() {
            final long[] ids = getListView().getCheckedItemIds();

            new AsyncTask<Void, Integer, Void> () {
                @Override
                protected void onPreExecute() {
                    final RemoveTaskDialog dialog = RemoveTaskDialog.newInstance(ids.length);
                    dialog.show(getFragmentManager(), RemoveTaskDialog.TAG);
                }

                @Override
                protected Void doInBackground(Void... params) {
                    final ContentResolver resolver = getActivity().getContentResolver();
                    for (int i=0; i<ids.length; ++i) {
                        final String id = String.valueOf(ids[i]);
                        final Cursor c = resolver.query(
                            TaskProvider.CONTENT_URI,
                            TaskProvider.PROJECTION,
                            TaskProvider.ID_FILTER,
                            new String[] { id },
                            TaskProvider.DEFAULT_ORDER
                            );
                        if (c != null) { 
                            final List<Task> tasks = new ArrayList<Task>();
                            for (c.moveToFirst(); !c.isAfterLast(); c.moveToNext()) {
                                final Task t = Task.fromCursor(c, 0);
                                tasks.add(t);
                            }
                            
                            if (tasks.size() > 0) {
                                final Intent intent = new Intent(getActivity(), ToodledoClientService.class);
                                intent.setAction(ToodledoClientService.ACTION_DELETE);
                                intent.putExtra(ToodledoClientService.EXTRA_TASK, ToodledoClientService.asListOfTasks(tasks));
                                getActivity().startService(intent);
                            }
                        }
                        resolver.delete(TaskProvider.CONTENT_URI, TaskProvider.ID_FILTER, new String[] { id });
                        publishProgress(i);
                    }
                    return null;
                }

                @Override
                protected void onPostExecute(Void ret) {
                    getDialog().dismissAllowingStateLoss();
                }

                @Override
                protected void onProgressUpdate(Integer... progress) {
                    getDialog().setProgress(progress[0]);
                }

                private RemoveTaskDialog getDialog() {
                    return (RemoveTaskDialog)getFragmentManager().findFragmentByTag(RemoveTaskDialog.TAG);
                }
            }.execute();
        }
    }

    public static class RemoveTaskDialog extends DialogFragment {
        public static final String TAG = "remove_task_dialog";

        private static final String KEY_LENGTH = "length";

        public static RemoveTaskDialog newInstance(final int length) {
            final RemoveTaskDialog f = new RemoveTaskDialog();
            final Bundle args = new Bundle();
            args.putInt(KEY_LENGTH, length);
            f.setArguments(args);
            return f;
        }

        @Override
        public Dialog onCreateDialog(final Bundle savedInstanceState) {
            final Bundle args = getArguments();
            final ProgressDialog dialog = new ProgressDialog(getActivity());
            dialog.setMessage("Removing tasks");
            dialog.setIndeterminate(false);
            dialog.setCancelable(false);
            dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            dialog.setMax(args.getInt(KEY_LENGTH));
            return dialog;
        }

        public void setProgress(final int progress) {
            final ProgressDialog dialog = (ProgressDialog)getDialog();
            dialog.setProgress(progress);
        }
    }
}
