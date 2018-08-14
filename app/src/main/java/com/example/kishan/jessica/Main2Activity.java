package com.example.kishan.jessica;

import android.content.ComponentName;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;


public class Main2Activity extends AppCompatActivity {


    TextView textview;
    Intent hotword_intent;
    ComponentName check_service;
    EditText messagebox;
    Button send_command, cnclBtn;
    Intent ai_service;
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);

        textview = (TextView) findViewById(R.id.result);
        messagebox = (EditText) findViewById(R.id.editText2);
        send_command = (Button) findViewById(R.id.button2);
        cnclBtn = (Button) findViewById(R.id.cancel);

        hotword_intent = new Intent(Main2Activity.this, Hotword.class);
        ai_service = new Intent(Main2Activity.this, Hotword.class);

        run_function();


        cnclBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ai_service.setAction(Hotword.PAUSE_SPEECH);
                startService(ai_service);
            }
        });

        send_command.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(messagebox.getText().toString().isEmpty()){
                    messagebox.setError("Enter some text");
                    return;
                }

                ai_service.setAction(Hotword.SEND_COMMAND);
                ai_service.putExtra("message",messagebox.getText().toString());
                startService(ai_service);

            }
        });
        textview.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ai_service.setAction(Hotword.RESTART);
                startService(ai_service);
            }
        });
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopService(hotword_intent);
    }

    void run_function(){
        //Toggling service starting and stopping
        if(check_service != null) {
            Toast.makeText(getBaseContext(), "Service is already running", Toast.LENGTH_SHORT).show();
            stopService(hotword_intent);
            check_service = null;
        }
        else {

//            Toast.makeText(getBaseContext(), "Initiating jessica", Toast.LENGTH_SHORT).show();
            hotword_intent.setAction(Hotword.SERVER_CONNECT);
            check_service = startService(hotword_intent);

        }
    }
}
