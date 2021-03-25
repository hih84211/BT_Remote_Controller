package com.example.bt_rc_controller;


import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class BluetoothController
{
    private final static String debug = "BTController Debug";  //Android系統日誌的標籤
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");  //藍芽配對用的全球唯一標識符(就只是個識別碼，不用在意它)
    private Handler mHandler;  //指向Activity中正在運作的Handler物件的參照，讓BTController中的執行緒知道訊息要往哪裡傳
    private final BluetoothAdapter mAdapter;  //Android提供，可控制藍芽運作(開關、搜尋、連線...)的介面
    private int mState;  //連線狀態
    private int mNewState;  //新的連線狀態
    private static BluetoothController mController;  //One of a kind controller
    private BTConnectionClientThread mClientThread;  //負責"建立藍芽通道"的執行緒
    private BTConnectedThread mConnectedThread;  //負責"使用藍芽通道"的執行緒

    private BluetoothController(Handler handler)  //BluetoothController的建構子，以Activity的Handler為引數。
    {                                             //將其設定為private，使其無法在外部被呼叫，藉此控制物件被實作的數量。(常用技巧)
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mState = ConnectConstant.STATE_NONE;
        mNewState = mState;
        mHandler = handler;
    }

    static synchronized BluetoothController getInstance(Handler handler)
    {
        if (mController == null)
            mController = new BluetoothController( handler);
        else
            mController.mHandler = handler;
        mController.stateUpdate();
        return mController;
    }

    private synchronized void stateUpdate()  //將藍芽連線狀態，以message傳給Handler，Activity才能做出對應處理
    {
        mState = getState();
        mNewState = mState;
        mHandler.obtainMessage(MessageConstants.MESSAGE_STATE_CHANGE, mNewState, -1).sendToTarget();
    }

    synchronized int getState()  //類別內部成員mState的get方法
    {
        return mState;
    }


    void sendInstructions(String str)  //傳遞字串的函式(類別的內部函式也稱作方法，我有時會不自覺混用)
    {
        if(mState==ConnectConstant.STATE_CONNECTED && mConnectedThread!=null)
        {
            if(str.length()>120)
                mConnectedThread.write("String oversize/0");
            else
                mConnectedThread.write(str);
        }
        else {

            Message msg = mHandler.obtainMessage(MessageConstants.MESSAGE_TOAST);
            Bundle bundle = new Bundle();
            bundle.putString(MessageConstants.TOAST, "無法傳輸字串");
            msg.setData(bundle);
            msg.sendToTarget();
        }

    }

    /**
     * Controller Switch functions
     * 整個BluetoothController都是以實際的"控制器"為設計宗旨(以硬體邏輯編寫軟體，這是OOP有趣的地方)
     * 既然是個(虛擬)硬體設備，就可以幫他設計開關，start以及stop。
     */
    synchronized void start()
    {
        if(mClientThread != null)
        {
            mClientThread.cancel();
            mClientThread = null;
        }
        if(mConnectedThread != null)
        {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }
        stateUpdate();
    }

    synchronized void stop()
    {
        if(mClientThread != null)
        {
            mClientThread.cancel();
            mClientThread = null;
        }
        if(mConnectedThread != null)
        {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }
        mState = ConnectConstant.STATE_NONE;
        stateUpdate();
    }



    /**
     * Connect-situation methods
     * 不同連線狀態所需調用的不通方法
     */
    synchronized void connect(String addr)
    {
        if(mClientThread != null)
        {
            mClientThread.cancel();
            mClientThread = null;
        }
        if(mConnectedThread != null)
        {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }
        mClientThread = new BTConnectionClientThread(addr, true);
        mClientThread.start();
        stateUpdate();
    }

    private void connected(BluetoothSocket socket)
    {
        if(mClientThread != null)
        {
            mClientThread.cancel();
            mClientThread = null;
        }
        if(mConnectedThread != null)
        {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }
        mConnectedThread = new BTConnectedThread(socket);
        mConnectedThread.start();
        mState = ConnectConstant.STATE_CONNECTED;
        stateUpdate();
    }

    private void connectionFailed()
    {
        Message msg = mHandler.obtainMessage(MessageConstants.MESSAGE_TOAST);
        Bundle data = new Bundle();
        data.putString("status", "Failed to connect device");
        msg.setData(data);
        msg.sendToTarget();
        mState = ConnectConstant.STATE_NONE;
        stateUpdate();
        BluetoothController.this.start();
    }

    void connectionLost()
    {
        Message msg = mHandler.obtainMessage(MessageConstants.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(MessageConstants.TOAST, "連接已斷開，請重新連接");
        msg.setData(bundle);
        msg.sendToTarget();
        mState = ConnectConstant.STATE_CONNECT_FAILED;
        stateUpdate();

        BluetoothController.this.start();
    }

    private void reconnected(BluetoothSocket socket)
    {

        if(mClientThread != null)
        {
            mClientThread.cancel();
            mClientThread = null;
        }
        if(mConnectedThread != null)
        {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }
        mConnectedThread = new BTConnectedThread(socket);
        mConnectedThread.start();
        mState = ConnectConstant.STATE__RECONNECTED;
        stateUpdate();
    }

    /**
     * Threads that get things done
     */
    private class BTConnectionClientThread extends Thread  //負責 "以client的身分，與Arduino的藍芽模組建立連線" 的執行緒
    {
        private final BluetoothSocket mBTSocket;
        private final BluetoothDevice mDevice;
        private final Boolean Tag;

        BTConnectionClientThread(String addr, Boolean tag)
        {
            //取得裝置MAC找到連接的藍芽裝置
            mDevice = mAdapter.getRemoteDevice(addr);
            Tag = tag;
            BluetoothSocket tmp = null;
            try
            {
                tmp = mDevice.createRfcommSocketToServiceRecord(MY_UUID);
            }
            catch (IOException e)
            {
                Log.e(debug, "Fail to create the Bluetooth socket");
            }
            mBTSocket = tmp;
            mState = ConnectConstant.STATE_CONNECTING;
        }

        @Override
        public void run()
        {
            mAdapter.cancelDiscovery();
            int count = 0;
            while(count<3 && !mBTSocket.isConnected())
            {
                // Establish the Bluetooth socket connection.
                try
                {
                    mBTSocket.connect(); //建立藍芽連線
                }
                catch (Exception e)
                {
                    Log.e(debug, e.getMessage());
                    try
                    {
                        mBTSocket.close();
                    }
                    catch (IOException e2)
                    {
                        //insert code to deal with this
                        Log.e(debug, e2.getMessage());
                    }
                    connectionFailed();
                    return;
                }
                count++;
            }
            mClientThread = null;
            if(Tag)
                connected(mBTSocket);
            else
                reconnected(mBTSocket);
        }

        private void cancel()
        {
            try
            {
                mBTSocket.close();
            }
            catch (IOException e)
            {
                Log.e(debug, "Failed to close the socket.");
            }
        }
    }

    private class BTConnectedThread extends Thread  //負責 "使用與維護藍芽通道" 的執行緒
    {
        private final BluetoothSocket mBTSocket;
        private final InputStream mInputStream;
        private final OutputStream mOutputStream;

        BTConnectedThread(BluetoothSocket socket)
        {
            mBTSocket = socket;
            InputStream intmp = null;
            OutputStream outtmp = null;

            try
            {
                intmp = mBTSocket.getInputStream();
                outtmp = mBTSocket.getOutputStream();
            }
            catch (IOException e)
            {
                Log.e(debug, "Failed to create IOstream");
            }
            mInputStream = intmp;
            mOutputStream = outtmp;
        }

        @Override
        public void run()
        {
            super.run();
            Log.d(debug, "Message tranfer start");
            byte[] mmBuffer = new byte[1024];
            int numBytes; // bytes returned from read()
            while(mState == ConnectConstant.STATE_CONNECTED)
            {
                try
                {
                    numBytes = mInputStream.available();
                    if (numBytes != 0)
                    {
                        SystemClock.sleep(80);
                        //pause and wait for rest of data
                        numBytes = mInputStream.available();
                        // how many bytes are ready to be read?
                        numBytes = mInputStream.read(mmBuffer, 0, numBytes);
                        // record how many bytes we actually read
                        mHandler.obtainMessage(MessageConstants.MESSAGE_READ, numBytes, -1, mmBuffer)
                                .sendToTarget();
                    }
                }
                catch (IOException e)
                {
                    Log.e(debug,"Something went wrong while transferring data: "
                            + e.getMessage());
                    connectionLost();
                    break;
                }
            }
        }


        private void write(String msg)
        {
            byte[] message = msg.getBytes();
            if (mOutputStream == null)
            {
                Log.e(debug, "Fail to establish OutputStream");
                return;
            }
            try
            {
                mOutputStream.write(message);
            }
            catch (IOException e)
            {
                Log.e(debug, "Fail to write msg");
            }
        }

        private void cancel()
        {
            try
            {
                mBTSocket.close();
            }
            catch (IOException e)
            {
                Log.e(debug, e.getMessage());
            }
        }
    }
}
