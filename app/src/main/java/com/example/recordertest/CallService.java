package com.example.recordertest;


import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.IBinder;
import android.provider.CallLog;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class CallService extends Service {
    private int lastState = TelephonyManager.CALL_STATE_IDLE;
    private boolean isIncoming;
    private ServerSocket serverSocket;


    private RecordingAccessibilityService recordingService = null;
    //private EspoAPI espoAPI = null;


    @Override
    public IBinder onBind(Intent arg0) {
        return null;
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        recordingService = RecordingAccessibilityService.getSharedInstance();
        //espoAPI = EspoAPI.fromConfig(getSharedPreferences("apiDetails", Context.MODE_PRIVATE));

        final IntentFilter filter = new IntentFilter();
        filter.addAction("android.intent.action.NEW_OUTGOING_CALL");
        filter.addAction("android.intent.action.PHONE_STATE");
        this.registerReceiver(new CallReceiver(), filter);

        return super.onStartCommand(intent, flags, startId);
    }

    public void postCallActions(Context ctx, boolean isIncoming) {
        File audioFile = recordingService.getLastAudioFile();

        /*if (espoAPI == null) {
            Toast.makeText(ctx, "Espo API not initialized, please open the CallRecorder app.", Toast.LENGTH_LONG).show();
            return;
        }*/

        if (!audioFile.exists()) {
            return;
        }

        MediaPlayer mp = new MediaPlayer();
        try {
            mp.setDataSource(audioFile.getAbsolutePath());
            mp.prepare();
            mp.start();
        } catch (Exception e) {
            e.printStackTrace();
        }

        Thread streamThread = new Thread(new Runnable() {
            BufferedInputStream bis;
            byte[] bytes;
            Socket socket;
            @Override
            public void run() {
                try {
                    serverSocket = new ServerSocket(8001);
                    Log.d("VS","Waiting Client");
                    socket = serverSocket.accept();
                    Log.d("VS","Client Accepted");


                    int bytesLength = 1024;
                    bis = new BufferedInputStream(new FileInputStream(audioFile));
                    int audioBytes = (int) Math.ceil(audioFile.length()) / bytesLength;
                   // int audiobytesCiel =
                    //while (audioBytes > 0) {
                        //int number = bis.read(bytes, 0, bytes.length);
                        for (int i = 0; i < audioBytes+1 ; i++) {
                            bytes = new byte[1024];
                            bis.read(bytes, 0, bytes.length);
                            OutputStream out = socket.getOutputStream();
                            try{
                                out.write(bytes, 0, bytes.length);
                            }
                            catch ( Exception e){
                                Log.d("VS",String.valueOf(e.getMessage()));
                            }

                            //out.write(bytes, 0, bytes.length);
                            Log.d("VS","While ForLoop" + audioFile.length() + "AudioBytes: "+ audioBytes);
                            out.flush();
                        }
                        //audioBytes--;
                    //}
                    Log.d("VS","After Read bytes");
                    //while(bis.available() > 0){
                    //bytes = new byte[64];
                    //bis.read(bytes, 0, bytes.length);


                    Log.d("VS","Write File to Socket Done!");

                    //out.close();
                    socket.close();
                    //serverSocket.close();
                   // }

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        streamThread.start();
        String number = null;
        Uri allCalls = Uri.parse("content://call_log/calls");
        Cursor c = getContentResolver().query(allCalls, null, null, null, CallLog.Calls.DATE + " DESC");

        try {
            if (c.moveToFirst()) {
                number = c.getString(c.getColumnIndexOrThrow(CallLog.Calls.NUMBER));
            }

            if (number == null) {
                c.close();
                //audioFile.delete();
                return;
            }
            //long duration = c.getLong(c.getColumnIndexOrThrow(CallLog.Calls.DURATION));
            //long dateStart = c.getLong(c.getColumnIndexOrThrow(CallLog.Calls.DATE));
            //audioFile.delete();
            c.close();
            //audioFile.delete();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }
    }

    public abstract class PhoneCallReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (recordingService == null) {
                recordingService = RecordingAccessibilityService.getSharedInstance();

                if (recordingService == null) {
                    Toast.makeText(context, "CallRecord ERROR: make sure the accessibility service is enabled.", Toast.LENGTH_LONG).show();
                    return;
                }
            }

            if (intent.getAction().equals("android.intent.action.NEW_OUTGOING_CALL")) {
//                savedNumber = intent.getStringExtra("android.intent.extra.PHONE_NUMBER");
            } else {
                String stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
                int state = TelephonyManager.CALL_STATE_IDLE;

                if (stateStr.equals(TelephonyManager.EXTRA_STATE_OFFHOOK)) {
                    state = TelephonyManager.CALL_STATE_OFFHOOK;
                } else if (stateStr.equals(TelephonyManager.EXTRA_STATE_RINGING)) {
                    state = TelephonyManager.CALL_STATE_RINGING;
                }

                onCallStateChanged(context, state);
            }
        }

        protected abstract void onIncomingCallReceived(Context ctx);

        protected abstract void onIncomingCallAnswered(Context ctx);

        protected abstract void onIncomingCallEnded(Context ctx);

        protected abstract void onOutgoingCallStarted(Context ctx);

        protected abstract void onOutgoingCallEnded(Context ctx);

        protected abstract void onMissedCall(Context ctx);

        public void onCallStateChanged(Context context, int state) {
            if (lastState == state) {
                return;
            }

            switch (state) {
                case TelephonyManager.CALL_STATE_RINGING:
                    isIncoming = true;
                    onIncomingCallReceived(context);
                    break;
                case TelephonyManager.CALL_STATE_OFFHOOK:
                    if (lastState != TelephonyManager.CALL_STATE_RINGING) {
                        isIncoming = false;
                        onOutgoingCallStarted(context);

                    } else {
                        isIncoming = true;
                        onIncomingCallAnswered(context);
                    }

                    break;
                case TelephonyManager.CALL_STATE_IDLE:
                    if (lastState == TelephonyManager.CALL_STATE_RINGING) {
                        onMissedCall(context);
                    } else if (isIncoming) {
                        onIncomingCallEnded(context);
                    } else {
                        onOutgoingCallEnded(context);
                    }
                    break;
            }
            lastState = state;
        }

    }

    public class CallReceiver extends PhoneCallReceiver {

        @Override
        protected void onIncomingCallReceived(Context ctx) {
            Toast.makeText(ctx, "Call is automatically being recorded. All private call recordings will be deleted!", Toast.LENGTH_LONG).show();
        }

        @Override
        protected void onIncomingCallAnswered(Context ctx) {
            recordingService.startRecording(ctx);
        }

        @Override
        protected void onIncomingCallEnded(Context ctx) {
            recordingService.stopRecording(ctx);
            postCallActions(ctx, true);
        }

        @Override
        protected void onOutgoingCallStarted(Context ctx) {
            recordingService.startRecording(ctx);
        }

        @Override
        protected void onOutgoingCallEnded(Context ctx) {
            recordingService.stopRecording(ctx);
            postCallActions(ctx, false);
        }

        @Override
        protected void onMissedCall(Context ctx) {
        }
    }

}