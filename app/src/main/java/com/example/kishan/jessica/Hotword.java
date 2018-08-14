package com.example.kishan.jessica;

import android.app.Service;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.service.voice.AlwaysOnHotwordDetector;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.support.annotation.BoolRes;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import edu.cmu.pocketsphinx.Assets;
import edu.cmu.pocketsphinx.Hypothesis;
import edu.cmu.pocketsphinx.RecognitionListener;
import edu.cmu.pocketsphinx.SpeechRecognizer;
import edu.cmu.pocketsphinx.SpeechRecognizerSetup;

public class Hotword extends Service {

    //INTENTS
    public static final String SEND_COMMAND = "com.example.kishan.jessica.SEND_COMMAND";
    public static final String SET_IP = "com.example.kishan.jessica.SET_IP";
    public static final String SET_PORT = "com.example.kishan.jessica.SET_PORT";
    public static final String SERVER_CONNECT = "jessica.SERVER_CONNECT";
    public static final String RESTART = "jessica.RESTART";
    public static final String PAUSE_SPEECH = "jessica.PAUSE";




    /* Named searches allow to quickly reconfigure the decoder */
    private static final String KWS_SEARCH = "wakeup";
    private static final String FORECAST_SEARCH = "forecast";
    private static final String DIGITS_SEARCH = "digits";
    private static final String PHONE_SEARCH = "phones";
    private static final String MENU_SEARCH = "menu";

    /* Keyword we are looking for to activate menu */
    private static final String KEYPHRASE = "jessica";

//    String ipadress = "192.168.1.100";
    String ipadress = "192.168.1.104";
    Intent intent;
    Socket client;
    SocketAddress server;
    int port = 9009;

    OutputStream outtoserver;
    OutputStreamWriter osw;
    BufferedWriter bw;
    JSONObject jsonObject;
    String user="Android";

    BufferedReader br;
    InputStream is;
    InputStreamReader isr;


    TextToSpeech tts;
    Intent googleIntent;

    SpeechRecognizer pocket_speech;
    android.speech.SpeechRecognizer google_speech;

    Pocketsphinx_listen pocketsphinx_listen;
    Google_listen google_listen;


    @Override
    public void onCreate() {
        super.onCreate();



        tts = new TextToSpeech(Hotword.this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int i) {
                if (i== TextToSpeech.SUCCESS)
                {
                    Toast.makeText(Hotword.this, "Speech started", Toast.LENGTH_SHORT).show();
                }
                else
                {
                    //failed to init
                    Toast.makeText(Hotword.this, "failed speech", Toast.LENGTH_SHORT).show();
                }
            }

        });

        pocketsphinx_listen = new Pocketsphinx_listen();
        google_listen = new Google_listen();
        jsonObject = new JSONObject();


        google_speech = android.speech.SpeechRecognizer.createSpeechRecognizer(Hotword.this);
        google_speech.setRecognitionListener(google_listen);


        googleIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        googleIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en");
        googleIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE,
                this.getPackageName());
        googleIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH);
        googleIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        googleIntent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true);
        runRecognizerSetup();

//        checkTTS();


    }









    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
//        Log.v("Myapp","Service Playback OnstartCommand");

        String action = "";
        int seek_time = 0;
        Boolean Toggle = false;
        if(intent != null) {
            action = intent.getAction();

            Toggle = intent.getBooleanExtra("Toggle", false);

            try {

                switch (action) {
                    case SEND_COMMAND:
                        sendCommands(intent.getStringExtra("message"));
                        break;
                    case SET_IP:
                        break;
                    case SERVER_CONNECT:
                        socketInit();
                        break;
                    case RESTART:
                        restartspeech();
                        break;
                    case PAUSE_SPEECH:
                        tts.stop();
                        break;


//                case ACTION_NEXT:
//                    //next song
////                    media_player.reset();
//                    playNext();
//                    break;
//                case ACTION_PREVIOUS:
//                    //previous
////                    media_player.reset();
//                    playPrev();
//                    break;
//                case ACTION_UPDATE_SEEK:
//                    if (seek_time != 0) {
//                        media_player.seekTo(seek_time * 1000);
////                    Log.v("Seeked to",""+seek_time*1000);
//                    }
//                    break;
//                case ACTION_STOP_FOREGROUND:
//                    MediaPlayerpause();
//                    stopForeground(true);
//                    stopSelf();
//                    break;

                    default:
                        break;
                }
            }
            catch (Exception e){
                e.printStackTrace();
            }
        }
        return START_STICKY;
    }















    @Override
    public void onDestroy() {
        super.onDestroy();
        stopSelf();
        tts.shutdown();
//        tts.stop();

    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }



    private void runRecognizerSetup() {
        // Recognizer initialization is a time-consuming and it involves IO,
        // so we execute it in async task
        new AsyncTask<Void, Void, Exception>() {
            @Override
            protected Exception doInBackground(Void... params) {
                try {
                    Assets assets = new Assets(Hotword.this);
                    File assetDir = assets.syncAssets();
                    setupRecognizer(assetDir);

                } catch (IOException e) {
                    return e;
                }
                return null;
            }

            @Override
            protected void onPostExecute(Exception result) {
                if (result != null) {
                    Toast.makeText(Hotword.this, "Failed to init recognizer ", Toast.LENGTH_SHORT).show();
                } else {
                    switchSearch(KWS_SEARCH);
                }


            }
        }.execute();

    }



    private void setupRecognizer(File assetsDir) throws IOException {
        // The recognizer can be configured to perform multiple searches
        // of different kind and switch between them

        pocket_speech = SpeechRecognizerSetup.defaultSetup()
                .setAcousticModel(new File(assetsDir, "en-us-ptm"))
                .setDictionary(new File(assetsDir, "cmudict-en-us.dict"))
                .setKeywordThreshold(1e-10f)
                .setRawLogDir(assetsDir) // To disable logging of raw audio comment out this call (takes a lot of space on the device)
                .getRecognizer();
        pocket_speech.addListener(pocketsphinx_listen);
        pocket_speech.addKeyphraseSearch("keywordSearch",KEYPHRASE);
        pocket_speech.startListening("keywordSearch");
        /** In your application you might not need to add all those searches.
         * They are added here for demonstration. You can leave just one.
         */
//
//        // Create keyword-activation search.
//        recognizer.addKeyphraseSearch(KWS_SEARCH, KEYPHRASE);
////
//        // Create grammar-based search for selection between demos
//        File menuGrammar = new File(assetsDir, "menu.gram");
//        recognizer.addGrammarSearch(MENU_SEARCH, menuGrammar);
//
//        // Create grammar-based search for digit recognition
//        File digitsGrammar = new File(assetsDir, "digits.gram");
//        recognizer.addGrammarSearch(DIGITS_SEARCH, digitsGrammar);
//
//        // Create language model search
//        File languageModel = new File(assetsDir, "weather.dmp");
//        recognizer.addNgramSearch(FORECAST_SEARCH, languageModel);
//
//        // Phonetic search
//        File phoneticModel = new File(assetsDir, "en-phone.dmp");
//        recognizer.addAllphoneSearch(PHONE_SEARCH, phoneticModel);
    }


    private void switchSearch(String searchName) {
//        recognizer.stop();
//        recognizer.startListening(KWS_SEARCH, 10000);
    }








    public class Pocketsphinx_listen implements RecognitionListener {
        String LOG_TAG = "Pocketsphinx Listener";
        @Override
        public void onBeginningOfSpeech() {

        }

        @Override
        public void onEndOfSpeech() {
            //dont call start gooogle speech here
        }

        @Override
        public void onPartialResult(Hypothesis hypothesis) {
            if (hypothesis == null)
                return;
            String text = hypothesis.getHypstr();
            if (text.equals(KEYPHRASE)) {
                //  do something and restart listening


                speak("Yess Boss");
                while(tts.isSpeaking())
                pocket_speech.stop();
                google_speech.startListening(googleIntent);
            }
        }

        @Override
        public void onResult(Hypothesis hypothesis) {
            if (hypothesis != null) {
                String text = hypothesis.getHypstr();

            }
        }

        @Override
        public void onError(Exception e) {

        }

        @Override
        public void onTimeout() {

        }
    }














    public class Google_listen implements android.speech.RecognitionListener{
        String LOG_TAG = "Google Listener";
        Boolean key = true;
        @Override
        public void onReadyForSpeech(Bundle bundle) {
            Log.i(LOG_TAG, "onReadyForSpeech");
        }

        @Override
        public void onBeginningOfSpeech() {
            key = true;
            Log.i(LOG_TAG, "onBeginnning of speech");
        }

        @Override
        public void onRmsChanged(float v) {
            Log.i(LOG_TAG, "onRmsChanged: " + v);
        }

        @Override
        public void onBufferReceived(byte[] bytes) {

        }

        @Override
        public void onEndOfSpeech() {
            Log.i(LOG_TAG,"onEndOfSpeech");
            restartspeech();
        }

        @Override
        public void onError(int i) {
            if (key == true) {
                String errorMessage = getErrorText(i);
                Log.d(LOG_TAG, "FAILED " + errorMessage);
            }
            key = false;
            startPocketpshinx();
        }

        @Override
        public void onResults(Bundle bundle) {
            Log.i(LOG_TAG, "onResults");
            ArrayList<String> matches = bundle
                    .getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION);
            String text = "";
            for (String result : matches)
                text += result + "\n";
//            speak(matches.get(0));
            sendCommands(matches.get(0).toString().toLowerCase());
            Log.v(LOG_TAG,"Results  "+matches.get(0) );
        }

        @Override
        public void onPartialResults(Bundle bundle) {
            Log.i(LOG_TAG, "onPartialResults");
        }

        @Override
        public void onEvent(int i, Bundle bundle) {
            Log.i(LOG_TAG, "onEvent");
        }



        public String getErrorText(int errorCode) {
            String message;
            switch (errorCode) {
                case android.speech.SpeechRecognizer.ERROR_AUDIO:
                    message = "Audio recording error";

                    break;
                case android.speech.SpeechRecognizer.ERROR_CLIENT:
                    message = "Client side error";

                    break;
                case android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                    message = "Insufficient permissions";

                    break;
                case android.speech.SpeechRecognizer.ERROR_NETWORK:
                    message = "Network error";

                    break;
                case android.speech.SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                    message = "Network timeout";

                    break;
                case android.speech.SpeechRecognizer.ERROR_NO_MATCH:
                    message = "No match";
                    break;
                case android.speech.SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                    message = "RecognitionService busy";
                    break;

                case android.speech.SpeechRecognizer.ERROR_SERVER:
                    message = "error from server";


                    break;
                case android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                    message = "No speech input";

                    break;
                default:
                    message = "Didn't understand, please try again.";

                    break;
            }

            return message;
        }


    }


    void speak(String text){

        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);

    }



    void startPocketpshinx(){
        pocket_speech.startListening("keywordSearch");
    }






    void connectToHost(String ip, int port){
        //connect to host
        try {
            client = new Socket();
            server = new InetSocketAddress(ip, port);
            client.connect(server);
            if(client.isConnected()) Toast.makeText(Hotword.this, "Server Connected", Toast.LENGTH_SHORT).show();
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

    void sendCommands(final String c){
        //send commands to host


        new Thread(new Runnable() {
            @Override
            public void run() {
                if (client.isConnected()) {
                    try {

//                        jsonObject.put("user", user);
//                        jsonObject.put("command",c);
                        outtoserver = client.getOutputStream();
                        //write in output stream
                        osw = new OutputStreamWriter(outtoserver);
                        //now write in output buffer
                        bw = new BufferedWriter(osw);
                        bw.write(c);
                        bw.flush();

                    } catch (Exception e) {

                        e.printStackTrace();

                    }
                }
            }
        }).start();
    }

    void socketInit(){



        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    client = new Socket();
                    SocketAddress server = new InetSocketAddress(ipadress,port);
                    client.connect(server);
                    outtoserver = client.getOutputStream();
                    osw=new OutputStreamWriter(outtoserver);
                    bw=new BufferedWriter(osw);
                    bw.write("Hello from android\n");
                    bw.flush();
                    is = client.getInputStream();
                    isr = new InputStreamReader(is);
                    br = new BufferedReader(isr);
                    try{
                        for(;;){

                            final String message =  br.readLine();
                            if(message.length() != 0){
                                Log.i("SERVER_RESPONSE",""+message);
                                speak(message);
                            }
                        }
                    }
                    catch(Exception e){
                        e.printStackTrace();
                    }


                }
                catch (UnknownHostException e2) {
                    e2.printStackTrace();
                } catch (IOException e1) {
                    e1.printStackTrace();
                    Log.d("Time out", "Time");
                }
            }
        }).start();
    }

    void setip(String ip, int port){

    }



    void restartspeech(){
        google_speech.stopListening();
        pocket_speech.startListening("keywordSearch");
    }



    void checkTTS(){
        try {
            String TAG = "VOICES";
            Log.i(TAG, "default engine : " + tts.getDefaultEngine());
            Log.i(TAG, "Engines : " + tts.getEngines());
            Log.i(TAG, "Engines size : " + tts.getEngines().size());
            Log.i(TAG, "One voice : " + tts.getVoice().getName());
            Log.i(TAG, "Available Voices  size : " + tts.getVoices().size());
            Log.i(TAG, "Default Voice : " + tts.getDefaultVoice().getName());
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

}
