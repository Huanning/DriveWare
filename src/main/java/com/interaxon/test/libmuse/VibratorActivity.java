package com.wakeupdriver.wakeupdriver;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Vibrator;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.interaxon.test.libmuse.R;

/**
 * Created by user on 2016-03-26.
 */
public class VibratorActivity extends Activity {
    public Vibrator vibrator;

    private Button one = null;
    private MediaPlayer mp = null;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        one.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                mp.start();
            }
        });
    }
}
