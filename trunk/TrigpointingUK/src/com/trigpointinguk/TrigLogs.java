package com.trigpointinguk;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class TrigLogs extends Activity {
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        TextView textview = new TextView(this);
        textview.setText("This is the TrigLogs tab");
        setContentView(textview);
    }
}