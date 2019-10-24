package br.inf.planeta.vlibsample;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;

import br.inf.planeta.Reader;
import br.inf.planeta.VLib;

public class MainActivity extends AppCompatActivity
{
    public boolean RunningTask = false;
    TextView tv;
    private byte[][] ATQ_PROPRIETARY_LIST = new byte[0][0];
    byte[] uid;
    byte[] atq;

    private PendingIntent permissionIntent;
    private Context ctx;
    private VLib vlib;
    Reader reader;
    UsbManager usbManager;
    private static final String ACTION_USB_PERMISSION =
            "br.inf.planeta.USB_PERMISSION";
    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {

        private String TAG = "USB_RECEIVER";


        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if(device != null && device.getProductId() == 87 && device.getVendorId() == 2816)
                        {
                            updateUI(R.string.VLIB_INIT);
                            Thread t= new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        Thread.sleep(100);
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                    new Handler(Looper.getMainLooper()).postAtTime(new Runnable() {
                                        @Override
                                        public void run() {
                                            reader = new Reader(ctx);
                                            vlib = new VLib(reader, ctx.getExternalFilesDir(null).toString());
                                            final Handler handler = new Handler();
                                            Timer timer = new Timer();
                                            TimerTask task = new TimerTask() {
                                                @Override
                                                public void run() {
                                                    handler.post(new Runnable() {
                                                        public void run() {
                                                            if (!RunningTask)
                                                                new detectCard().execute();
                                                        }
                                                    });
                                                }
                                            };
                                            timer.schedule(task, 0, 100); //it executes this every 1000ms
                                        }}, SystemClock.uptimeMillis() + 500);
                                }});

                            t.start();
                        }
                    }
                    else {
                        usbManager.requestPermission(device, permissionIntent);
                        Log.d(TAG, "permission denied for device " + device);
                    }
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ctx = this;

        tv = findViewById(R.id.sample_text);

        UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        HashMap<String, UsbDevice> deviceList = manager.getDeviceList();
        Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
        while(deviceIterator.hasNext()){
            UsbDevice device = deviceIterator.next();
            permissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
            IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
            registerReceiver(usbReceiver, filter);
            manager.requestPermission(device, permissionIntent);
        }

    }

    private void updateUI(final int text)
    {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                tv.setText(text);
            }
        });
    }

    private void waitCardRemove()
    {
        while(true) {
            byte[] ret = reader.SCardTransmit(0, new byte[]{(byte) 0xFF, (byte) 0xCA, (byte) 0xE1, 0, 0});
            if (ret.length <= 2) {
                return;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private class detectCard extends AsyncTask<Void, Void, Integer>
    {
        @Override
        protected Integer doInBackground(Void... voids)
        {
        RunningTask = true;
        uid = new byte[0];
        atq = new byte[0];
        updateUI(R.string.SHOW_CARD);
        /*byte[] ret = reader.SCardTransmit(0, new byte[] {(byte) 0xFF, (byte) 0xCA, (byte) 0xE1, 0, 0});
        if(ret.length >= 2)
        {
            uid = new byte[ret.length -2];
            System.arraycopy(ret, 0, uid, 0, uid.length);*/

            byte[] ret = reader.SCardTransmit(0, new byte[] {(byte) 0xFF, (byte) 0xCA, 1, 0x0E, 0});
            if(ret.length >= 5)
            {
                atq = new byte[3];
                System.arraycopy(ret, 0, atq, 0, atq.length);
                for(int i = 0; i < ATQ_PROPRIETARY_LIST.length; i++)
                {
                    if(atq[0] == ATQ_PROPRIETARY_LIST[i][0] && atq[1] == ATQ_PROPRIETARY_LIST[i][1] && atq[2] == ATQ_PROPRIETARY_LIST[i][2]) {
                        updateUI(R.string.PROPRIETARY_CARD_DETECTED);
                        return ProprietaryCard();
                    }
                }
                updateUI(R.string.EMV_CARD_DETECTED);
                byte[] cfg = new byte[]{ 0x00, 0x61, 0x00, 0x00 };
                int res = vlib.open(atq, cfg);

                if(res < 0)
                {
                    updateUI(R.string.RES_OPEN_NEGATIVE);
                    waitCardRemove();
                    RunningTask = false;
                    return res;
                }

                if((res & 0x8000) != 0)
                {
                    updateUI(R.string.RES_OPEN_NOK);
                    waitCardRemove();
                    RunningTask = false;
                    return res;
                }

                byte[] tdata = new byte[32];
                res = vlib.close(tdata);

                if(res < 0)
                {
                    updateUI(R.string.RES_CLOSE_NEGATIVE);
                    waitCardRemove();
                    RunningTask = false;
                    return res;
                }

                if((res & 0x8000) != 0)
                {
                    updateUI(R.string.RES_CLOSE_NOK);
                    waitCardRemove();
                    RunningTask = false;
                    return res;
                }

                updateUI(R.string.RES_TRANSACTION_OK);
                waitCardRemove();
            }
        //}
        RunningTask = false;
        return 0;
    }
    }

    private int ProprietaryCard()
    {
        return 0;
    }

}
