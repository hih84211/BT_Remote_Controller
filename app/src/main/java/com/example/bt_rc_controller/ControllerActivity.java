package com.example.bt_rc_controller;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import java.lang.ref.WeakReference;
import java.util.Objects;

public class ControllerActivity extends AppCompatActivity
{
    private String debug = "Direction debug";    // Android device 中系統logcat 的標籤(測試或除錯用途)
    private String Device;    // 藍芽連線Server端的藍芽實體位置
    private BluetoothController mController = null;
    private BroadcastReceiver BTreceiver = null;
    private boolean isConnected = true;
    private Button b_forward;
    private Button b_backward;
    private Button b_rightForward;
    private Button b_leftForward;
    private Button b_rightBackward;
    private Button b_leftBackward;
    private Button b_right;
    private Button b_left;
    private Button b_stop;
    private Button b_leftSpeedUp;
    private Button b_leftSpeedDown;
    private Button b_rightSpeedUp;
    private Button b_rightSpeedDown;
    private Button b_allSpeedUp;
    private Button b_allSpeedDown;
    private boolean isBtnLongPressed = false;
    static class mHandler extends Handler    // 接收訊息(座標變更、藍芽連線狀況等訊息)的handler 類別
    {
        private final WeakReference<ControllerActivity> mactivity;

        mHandler(ControllerActivity activity)
        {
            mactivity = new WeakReference(activity);
        }

        @Override
        public void handleMessage(Message msg)    // Handler定義跟MainActivity差不多
        {
            super.handleMessage(msg);
            ControllerActivity activity = mactivity.get();

            switch (msg.what)
            {
                case ConnectConstant.STATE__RECONNECTED:
                    activity.setState(true);
                    break;
                case MessageConstants.MESSAGE_READ:    // 若有需要從Arduino 端接收訊息，以下程式碼請自取(不敢保證是否能運作)

                    /*try
                    {
                        readMessage = new String((byte[]) msg.obj, "UTF-8");
                        recieveData = readMessage; //拼湊每次收到的字元成字串
                    }
                    catch (UnsupportedEncodingException e)
                    {
                        e.printStackTrace();
                    }
                    Toast.makeText(MainActivity.this, _recieveData, Toast.LENGTH_SHORT).show();
                    recieveText.setText(_recieveData);*/
                    break;
                case MessageConstants.MESSAGE_WRITE:
                    String instr = (String) msg.obj;
                    Log.d(activity.debug,"Handler:" + instr);
                    if (activity.mController != null)
                        activity.mController.sendInstructions(instr);
                    Toast.makeText(activity, instr, Toast.LENGTH_LONG).show();
                    break;
                case MessageConstants.MESSAGE_TOAST:
                    String error = msg.getData().getString("toast", "");
                    Toast.makeText(activity, error, Toast.LENGTH_SHORT).show();
                    break;
                case MessageConstants.MESSAGE_STATE_CHANGE:
                    switch (msg.arg1)
                    {
                        case ConnectConstant.STATE_CONNECT_FAILED:
                            Toast.makeText(activity, "連線已斷開，請重新連接", Toast.LENGTH_SHORT).show();
                            //activity.finish(); 20190113
                            break;
                    }
                    break;
            }
        }
    }
    private final mHandler mHandler = new mHandler(this);    // 上面是類別，這裡是實做出來的物件


    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_controller);
        find();
        Bundle bundle =this.getIntent().getExtras();
        Device = Objects.requireNonNull(bundle).getString("device");
        //20190113
        if(!"".equals(Device))    // if判斷式是為了後門而存在的
        {
            mController = BluetoothController.getInstance(mHandler);

            BTreceiver = new BroadcastReceiver()    //實作廣播接收器偵測藍芽連線狀況，如果斷線就會呼叫BluetoothController中的connectionLost()方法
            {
                @Override
                public void onReceive(Context context, Intent intent)
                {
                    setState(false);
                    if(mController != null)
                        mController.connectionLost();
                }
            };
            if(mController != null)
            {
                IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED);
                registerReceiver(BTreceiver, filter);
            }

        }
        b_leftSpeedUp.setOnClickListener(clicklistener);
        b_leftSpeedDown.setOnClickListener(clicklistener);
        b_rightSpeedUp.setOnClickListener(clicklistener);
        b_rightSpeedDown.setOnClickListener(clicklistener);
        b_allSpeedUp.setOnClickListener(clicklistener);
        b_allSpeedDown.setOnClickListener(clicklistener);
        b_stop.setOnClickListener(clicklistener);

    }

    public synchronized void setState(boolean state)    // isConnected 的set 方法
    {
        isConnected = state;
    }

    private void find()
    {
        b_forward = findViewById(R.id.b_forward);
        b_backward = findViewById(R.id.b_backward);
        b_left = findViewById(R.id.b_left);
        b_right = findViewById(R.id.b_right);
        b_rightForward = findViewById(R.id.b_rightForward);
        b_leftForward = findViewById(R.id.b_leftForward);
        b_leftBackward = findViewById(R.id.b_leftBackward);
        b_rightBackward = findViewById(R.id.b_rightBackward);
        b_stop = findViewById(R.id.b_stop);
        b_leftSpeedUp = findViewById(R.id.leftSpeedUp);
        b_leftSpeedDown = findViewById(R.id.leftSpeedDown);
        b_allSpeedUp = findViewById(R.id.allSpeedUp);
        b_allSpeedDown = findViewById(R.id.allSpeedDown);
        b_leftSpeedDown = findViewById(R.id.leftSpeedDown);
        b_rightSpeedUp = findViewById(R.id.rightSpeedUp);
        b_rightSpeedDown = findViewById(R.id.rightSpeedDown);

        b_forward.setOnTouchListener(touchlistener);
        b_backward.setOnTouchListener(touchlistener);
        b_leftForward.setOnTouchListener(touchlistener);
        b_rightForward.setOnTouchListener(touchlistener);
        b_left.setOnTouchListener(touchlistener);
        b_right.setOnTouchListener(touchlistener);
        b_leftBackward.setOnTouchListener(touchlistener);
        b_rightBackward.setOnTouchListener(touchlistener);


    }

    private View.OnClickListener clicklistener = new View.OnClickListener()
    {

        public void onClick(View v)
        {
            switch (v.getId())
            {
                case R.id.b_stop:
                    mHandler.obtainMessage(MessageConstants.MESSAGE_WRITE, "9").sendToTarget();
                    mHandler.obtainMessage(MessageConstants.MESSAGE_WRITE, "9").sendToTarget();
                    break;
                case R.id.leftSpeedUp:
                    mHandler.obtainMessage(MessageConstants.MESSAGE_WRITE, "a").sendToTarget();
                    mHandler.obtainMessage(MessageConstants.MESSAGE_WRITE, "a").sendToTarget();
                    break;
                case R.id.leftSpeedDown:
                    mHandler.obtainMessage(MessageConstants.MESSAGE_WRITE, "b").sendToTarget();
                    mHandler.obtainMessage(MessageConstants.MESSAGE_WRITE, "b").sendToTarget();
                    break;
                case R.id.allSpeedUp:
                    mHandler.obtainMessage(MessageConstants.MESSAGE_WRITE, "c").sendToTarget();
                    mHandler.obtainMessage(MessageConstants.MESSAGE_WRITE, "c").sendToTarget();
                    break;
                case R.id.allSpeedDown:
                    mHandler.obtainMessage(MessageConstants.MESSAGE_WRITE, "d").sendToTarget();
                    mHandler.obtainMessage(MessageConstants.MESSAGE_WRITE, "d").sendToTarget();
                    break;
                case R.id.rightSpeedUp:
                    mHandler.obtainMessage(MessageConstants.MESSAGE_WRITE, "e").sendToTarget();
                    mHandler.obtainMessage(MessageConstants.MESSAGE_WRITE, "e").sendToTarget();
                    break;
                case R.id.rightSpeedDown:
                    mHandler.obtainMessage(MessageConstants.MESSAGE_WRITE, "f").sendToTarget();
                    mHandler.obtainMessage(MessageConstants.MESSAGE_WRITE, "f").sendToTarget();
                    break;
            }
        }
    };

    //監聽放開按鈕(detect release button)
    private class touchListener implements View.OnTouchListener
    {

        @Override
        public boolean onTouch(View pView, MotionEvent pEvent)
        {
            if (pEvent.getAction() == MotionEvent.ACTION_UP)
            {
                // Do something when the button is released.
                mHandler.obtainMessage(MessageConstants.MESSAGE_WRITE, "0").sendToTarget();
                mHandler.obtainMessage(MessageConstants.MESSAGE_WRITE, "0").sendToTarget();
                Log.e("log", "release");
            }
            else if(pEvent.getAction() == MotionEvent.ACTION_DOWN)
            {
                switch(pView.getId())
                {
                    case R.id.b_forward:
                        mHandler.obtainMessage(MessageConstants.MESSAGE_WRITE, "1").sendToTarget();
                        mHandler.obtainMessage(MessageConstants.MESSAGE_WRITE, "1").sendToTarget();
                        break;
                    case R.id.b_leftForward:
                        mHandler.obtainMessage(MessageConstants.MESSAGE_WRITE, "2").sendToTarget();
                        mHandler.obtainMessage(MessageConstants.MESSAGE_WRITE, "2").sendToTarget();
                        break;
                    case R.id.b_rightForward:
                        mHandler.obtainMessage(MessageConstants.MESSAGE_WRITE, "3").sendToTarget();
                        mHandler.obtainMessage(MessageConstants.MESSAGE_WRITE, "3").sendToTarget();
                        break;
                    case R.id.b_left:
                        mHandler.obtainMessage(MessageConstants.MESSAGE_WRITE, "4").sendToTarget();
                        mHandler.obtainMessage(MessageConstants.MESSAGE_WRITE, "4").sendToTarget();
                        break;
                    case R.id.b_right:
                        mHandler.obtainMessage(MessageConstants.MESSAGE_WRITE, "5").sendToTarget();
                        mHandler.obtainMessage(MessageConstants.MESSAGE_WRITE, "5").sendToTarget();
                        break;
                    case R.id.b_leftBackward:
                        mHandler.obtainMessage(MessageConstants.MESSAGE_WRITE, "6").sendToTarget();
                        mHandler.obtainMessage(MessageConstants.MESSAGE_WRITE, "6").sendToTarget();
                        break;
                    case R.id.b_rightBackward:
                        mHandler.obtainMessage(MessageConstants.MESSAGE_WRITE, "7").sendToTarget();
                        mHandler.obtainMessage(MessageConstants.MESSAGE_WRITE, "7").sendToTarget();
                        break;
                    case R.id.b_backward:
                        mHandler.obtainMessage(MessageConstants.MESSAGE_WRITE, "8").sendToTarget();
                        mHandler.obtainMessage(MessageConstants.MESSAGE_WRITE, "8").sendToTarget();
                        break;
                }

            }
            return false;
        }

    }
    private touchListener touchlistener = new touchListener();
}