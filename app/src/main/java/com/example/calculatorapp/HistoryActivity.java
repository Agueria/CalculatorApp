package com.example.calculatorapp;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;

public class HistoryActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        ArrayList<String> history = getIntent().getStringArrayListExtra("history");
        if (history == null) {
            history = new ArrayList<>();
        }

        ListView listView = findViewById(R.id.history_list);
        TextView emptyView = findViewById(R.id.empty_view);

        // Dark-themed adapter — overrides the default white background of
        // simple_list_item_1 so it matches the app's dark colour scheme.
        final ArrayList<String> finalHistory = history;
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this, android.R.layout.simple_list_item_1, finalHistory) {

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView tv = view.findViewById(android.R.id.text1);
                tv.setTextColor(Color.WHITE);
                tv.setTextSize(16f);
                tv.setPadding(48, 28, 48, 28);
                view.setBackgroundColor(Color.parseColor("#1C1C1E"));
                return view;
            }
        };

        listView.setAdapter(adapter);
        listView.setEmptyView(emptyView);
    }
}
