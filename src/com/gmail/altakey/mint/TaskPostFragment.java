package com.gmail.altakey.mint;

import android.app.DialogFragment;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.view.LayoutInflater;
import android.widget.TextView;

import java.util.Date;
import java.io.IOException;

public class TaskPostFragment extends DialogFragment {
    private static final int DUE = 86400;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        final LayoutInflater inflater = getActivity().getLayoutInflater();
        final View layout = inflater.inflate(
            R.layout.post_task,
            null);
        final TextView field = (TextView)layout.findViewById(R.id.title);

        builder
            .setView(layout)
            .setTitle("Post task here")
            .setPositiveButton(android.R.string.ok, new PostAction(field));
        return builder.create();
    }

    private class PostAction implements DialogInterface.OnClickListener {
        private TextView mmField;

        public PostAction(TextView field) {
            mmField = field;
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            final Context context = getActivity();
            final Task task = build();
            final DB db = new DB(context);

            try {
                db.openForWriting();
                db.addTask(task);
            } finally {
                db.close();
            }

            final Intent intent = new Intent(context, ToodledoClientService.class);
            intent.setAction(ToodledoClientService.ACTION_SYNC);
            context.startService(intent);

            poke();
        }

        private void poke() {
            final TaskListActivity.TaskListFragment f = (TaskListActivity.TaskListFragment)getTargetFragment();
            if (f != null) {
                f.reload();
            }
        }

        private Task build() {
            final Task t = new Task();
            final int status = new DB.Filter(getActiveFilter()).getStatus();
            t.title = mmField.getText().toString();
            if (status == DB.Filter.UNKNOWN) {
                t.duedate = (new Date().getTime() + DUE * 1000) / 1000;
            } else {
                t.status = String.valueOf(status);
            }
            return t;
        }

        private String getActiveFilter() {
            final TaskListActivity.TaskListFragment f = (TaskListActivity.TaskListFragment)getTargetFragment();
            if (f == null) {
                return null;
            } else {
                return f.getFilter();
            }
        }
    }
}
