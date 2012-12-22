package com.gmail.altakey.mint;

import android.support.v4.app.ListFragment;
import android.os.Bundle;
import android.widget.*;
import android.view.*;
import android.content.Intent;

import java.util.List;
import java.util.LinkedList;
import java.io.IOException;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.widget.Toast;

import android.app.Activity;
import android.os.AsyncTask;
import android.util.Log;
import java.util.Map;
import java.util.HashMap;
import java.util.WeakHashMap;
import android.widget.BaseAdapter;
import android.widget.SimpleAdapter;

import java.util.Date;
import java.util.Formatter;
import java.util.Queue;

public class TaskListFragment extends ListFragment
{
    private TaskListAdapter mAdapter;
    private ToodledoClient mClient;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mClient = new ToodledoClient(getAuthenticator(), getActivity());
        mAdapter = new TaskListAdapterBuilder().build();

        setHasOptionsMenu(true);
        getActivity().setTitle("Hotlist");
        setListAdapter(mAdapter);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.main, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.main_preferences:
            startActivity(new Intent(getActivity(), ConfigActivity.class));
            return false;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onResume() {
        super.onResume();
        mAdapter.notifyDataSetChanged();
    }

    @Override
    public void onListItemClick(ListView lv, View v, int position, long id) {
        super.onListItemClick(lv, v, position, id);
        final TaskListAdapter adapter = (TaskListAdapter)getListAdapter();
        final Map<String, ?> e = (Map<String, ?>)adapter.getItem(position);
        new TaskCompleteTask((Task)e.get("task")).execute();
    }

    public class TaskListAdapterBuilder {
        public TaskListAdapter build() {
            final List<Map<String, ?>> data = new LinkedList<Map<String, ?>>();
            TaskListAdapter adapter = new TaskListAdapter(
                getActivity(),
                data
            );
            new TaskListLoadTask(data).execute();
            return adapter;
        }
    }

    private class TaskListAdapter extends SimpleAdapter {
        private List<? extends Map<String, ?>> mmmData;

        public TaskListAdapter(android.content.Context ctx, List<? extends Map<String, ?>> data) {
            super(ctx,
                  data,
                  R.layout.list_item,
                  new String[] { "title", "context_0", "context_1", "context_2", "due", "timer_flag" },
                  new int[] { R.id.list_task_title, R.id.list_task_context_0, R.id.list_task_context_1, R.id.list_task_context_2, R.id.list_task_due, R.id.list_task_timer_flag });
            mmmData = data;
        }

        public void removeTask(Task t) {
            Queue<Map<String, ?>> toBeRemoved = new LinkedList<Map<String, ?>>();
            for (Map<String, ?> e: mmmData) {
                if (e.get("task") == t) {
                    toBeRemoved.add(e);
                }
            }
            for (Map<String, ?> e: toBeRemoved) {
                mmmData.remove(e);
            }
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            convertView = super.getView(position, convertView, parent);
            View priority = convertView.findViewById(R.id.list_task_prio);
            Map<String, ?> map = mmmData.get(position);
            final Task task = (Task)map.get("task");

            switch (((Long)map.get("priority")).intValue()) {
            case -1:
                priority.setBackgroundColor(0xff0000ff);
                break;
            case 0:
                priority.setBackgroundColor(0xff00ff00);
                break;
            case 1:
                priority.setBackgroundColor(0xffffff00);
                break;
            case 2:
                priority.setBackgroundColor(0xffff8800);
                break;
            case 3:
            default:
                priority.setBackgroundColor(0xffff0000);
                break;
            }
            if (task.grayedout) {
                convertView.setBackgroundColor(0x80ffffff);
            } else {
                convertView.setBackgroundColor(0x00000000);
            }
            return convertView;
        }
    }

    private abstract class NetworkTask extends AsyncTask<Void, Void, Integer> {
        protected Exception mmError;

        protected static final int OK = 0;
        protected static final int LOGIN_REQUIRED = 1;
        protected static final int LOGIN_FAILED = 2;
        protected static final int FAILURE = 3;

        @Override
        public void onPostExecute(Integer ret) {
            if (ret == LOGIN_REQUIRED) {
                showLoginRequired();
            } else if (ret == LOGIN_FAILED) {
                showLoginFailed();
            } else if (ret == FAILURE) {
                Log.e("TLF", "fetch failure", mmError);
                Toast.makeText(getActivity(), String.format("fetch failure: %s", mmError.getMessage()), Toast.LENGTH_LONG).show();
            }
        }
    }

    private class TaskListLoadTask extends NetworkTask {
        private List<Map<String, ?>> mmData;

        public TaskListLoadTask(List<Map<String, ?>> data) {
            mmData = data;
        }

        @Override
        public Integer doInBackground(Void... params) {
            try {
                DB db = new DB(getActivity());
                try {
                    db.open();
                    db.update(mClient);

                    for (Task t : db.getHotTasks()) {
                        if (t.completed != 0)
                            continue;

                        Context c = t.resolved.context;
                        Folder f = t.resolved.folder;

                        Map<String, Object> map = new HashMap<String, Object>();
                        map.put("task", t);
                        map.put("title", t.title);
                        map.put("priority", t.priority);

                        if (f != null) {
                            map.put("context_0", String.format("%s", f.name));
                        }
                        if (c != null) {
                            map.put("context_1", String.format("@%s", c.name));
                        }

                        if (t.duedate > 0) {
                            map.put("due", new Formatter().format("%1$tY-%1$tm-%1$td", new Date(t.duedate * 1000)).toString());
                        }
                        //map.put("timer_flag", "(on)");
                        mmData.add(map);
                    }
                    return OK;
                } finally {
                    if (db != null) {
                        db.close();
                    }
                }
            } catch (IOException e) {
                mmError = e;
                return FAILURE;
            } catch (Authenticator.BogusException e) {
                mmError = e;
                return LOGIN_REQUIRED;
            } catch (Authenticator.FailureException e) {
                mmError = e;
                return LOGIN_FAILED;
            } catch (Authenticator.ErrorException e) {
                mmError = e;
                return FAILURE;
            }
        }

        @Override
        public void onPostExecute(Integer ret) {
            super.onPostExecute(ret);
            if (ret == OK) {
                refresh();
            }
        }
    }

    private class TaskCompleteTask extends NetworkTask {
        private Task mmTask;

        public TaskCompleteTask(Task task) {
            mmTask = task;
        }

        @Override
        public void onPreExecute() {
            startStrikeout(mmTask);
            refresh();
        }

        @Override
        public Integer doInBackground(Void... params) {
            try {
                DB db = new DB(getActivity());
                try {
                    mmTask.markAsDone();
                    mClient.updateDone(mmTask);

                    db.open();
                    db.update(mClient);
                } finally {
                    if (db != null) {
                        db.close();
                    }
                }
                return OK;
            } catch (IOException e) {
                mmError = e;
                return FAILURE;
            } catch (Authenticator.BogusException e) {
                mmError = e;
                return LOGIN_REQUIRED;
            } catch (Authenticator.FailureException e) {
                mmError = e;
                return LOGIN_FAILED;
            } catch (Authenticator.ErrorException e) {
                mmError = e;
                return FAILURE;
            }
        }

        @Override
        public void onPostExecute(Integer ret) {
            super.onPostExecute(ret);
            if (ret == OK) {
                completeStrikeout(mmTask);
                refresh();
            } else if (ret == FAILURE) {
                stopStrikeout(mmTask);
                refresh();
            }
        }

        private void completeStrikeout(Task t) {
            mAdapter.removeTask(t);
        }

        private void startStrikeout(Task t) {
            t.grayedout = true;
        }

        private void stopStrikeout(Task t) {
            t.grayedout = false;
        }

    }

    private Authenticator getAuthenticator() {
        return Authenticator.create(getActivity());
    }

    private void refresh() {
        mAdapter.notifyDataSetChanged();
    }

    private void showLoginRequired() {
        getFragmentManager()
            .beginTransaction()
            .replace(R.id.frag, new LoginRequiredFragment())
            .commit();
    }

    private void showLoginFailed() {
        getFragmentManager()
            .beginTransaction()
            .replace(R.id.frag, new LoginFailedFragment())
            .commit();
    }
}
