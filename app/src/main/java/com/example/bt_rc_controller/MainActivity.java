package com.example.bt_rc_controller;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.lang.ref.WeakReference;
import java.util.Set;


public class MainActivity extends AppCompatActivity
{
    private static final int REQUEST_ENABLE_BT = 3;  //藍芽相關權限之自定義請求代碼
    private BluetoothController mController = null;  //專門統一控管藍芽相關鳥事的類別(待會會解釋)
    private BluetoothAdapter BTadapter;  //Android提供，可控制藍芽運作(開關、搜尋、連線...)的介面
    private ListView discoveredBTList;  //列出"搜尋到"，或者"曾經配對過"的藍芽裝置的清單元件(有按鈕可以選)
    private ArrayAdapter<String> mBTArray;  //"搜尋到"，或者"曾經配對過"的藍芽裝置的清單，需放到ListView才會出現在主畫面中
    private String Address;  //Arduino端藍芽硬體address
    TextView BTstatus;  //主畫面中顯示藍芽連線狀態(未連線、連線中、已連線...)的原件
    Button getPaired;  //選擇顯示已配對藍芽裝置之按鈕
    Button findNew;  //選擇顯示搜尋到之藍芽裝置之按鈕
    Button bBackDoor;  //可跳過藍芽連線程序直接開啟導航畫面的後門
    String _recieveData;  //由Arduino接收到的訊息(目前沒有用到，為保留代碼)
    private String Tag = "BLUETOOTH ACTIVITY";  //Android系統日誌的標籤
    static class mHandler extends Handler  //繼承原有的Message Handler為內部類別，負責接收執行緒間溝通之message (可抽出來成為獨立Java class檔案，會比較好管理)
    {                                      //每個Activity都是單一的執行緒，其他執行緒要更改Activity的外觀或內部資源，都要透過Handler這個窗口

        private final WeakReference<MainActivity> mactivity;  //主畫面元件的弱參照(只是證件，不是主畫面本人)
        //為甚麼要這樣做咧? 這個網站寫得還不錯，有空可以看看 https://puretech.iteye.com/blog/2008663

        mHandler(MainActivity activity)
        {
            mactivity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg)  //利用switch-case，將窗口收到的message分類，並實作出對應動作
        {
            super.handleMessage(msg);
            MainActivity activity = mactivity.get();
            switch (msg.what)
            {
                case MessageConstants.MESSAGE_READ:  //接收來自Arduino訊息的程式碼，還用不到

                    /*String readMessage = "";
                    try
                    {
                        readMessage = new String((byte[]) msg.obj, "UTF-8");
                        _recieveData = readMessage; //拼湊每次收到的字元成字串
                    }
                    catch (UnsupportedEncodingException e)
                    {
                        e.printStackTrace();
                    }
                    Toast.makeText(MainActivity.this, _recieveData, Toast.LENGTH_SHORT).show();
                    recieveText.setText(_recieveData);*/
                    break;
                case MessageConstants.MESSAGE_TOAST:  //處理其他執行緒對MainActivity Toast訊息的程式碼(我不知道該怎麼解釋了，懂的人自然明白...)
                    String error = msg.getData().getString("toast", "");
                    Toast.makeText(activity, error, Toast.LENGTH_SHORT).show();
                    break;
                case MessageConstants.MESSAGE_STATE_CHANGE:  // 為避免藍芽連線的一系列工作(通道建立、維護等等)，造成MainActivity的工作停擺(例如:連線建立需要一段時間，
                    switch (msg.arg1)                            // 此時主畫面將不會執行其他工作，使用者會有手機當機的錯覺(按鈕沒反應之類的))，是由額外的執行緒處理。
                    {                                            // 因此，要將藍芽連線狀態顯示在主畫面中，也必須透過Handler。以下為負責顯示連線狀態改變的程式碼。
                        case ConnectConstant.STATE_CONNECTED:
                            activity.BTstatus.setText(R.string.status_connected);
                            Intent intent = new Intent(activity, ControllerActivity.class);
                            Bundle bundle = new Bundle();
                            bundle.putString("device", activity.Address);
                            bundle.putBoolean("try_to_reconnect", false);
                            //將Bundle物件assign給intent
                            intent.putExtras(bundle);
                            //切換Activity
                            activity.startActivity(intent);
                            //mConversationArrayAdapter.clear();
                            break;
                        case ConnectConstant.STATE_CONNECTING:
                            activity.BTstatus.setText(R.string.status_connecting);
                            break;
                        case ConnectConstant.STATE_NONE:
                            activity.BTstatus.setText(R.string.status_not_connected);
                            break;
                    }
                    break;
            }
        }
    }
    private final MainActivity.mHandler mHandler = new MainActivity.mHandler(this);

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        _recieveData = "";
        find();
        BTadapter = BluetoothAdapter.getDefaultAdapter();
        if (BTadapter == null)
        {
            //檢查手機是否支援藍芽通訊
            new AlertDialog.Builder(this).setMessage(R.string.BTunsupport).setPositiveButton(R.string.ok, new DialogInterface.OnClickListener()
            {
                @Override
                public void onClick(DialogInterface dialog, int which)  //按下確認後要做甚麼事可以寫在這裡。(我保留了結束整個APP的指令，不需要的話可以拿掉)
                {
                    //MainActivity.this.finish();
                }
            }).show();
        }
        else{
            //檢查app藍芽權限
            int permission = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION);
            if (permission != PackageManager.PERMISSION_GRANTED)
            {//若無權限則向使用者要求
                ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.ACCESS_FINE_LOCATION}, 1);
                Log.d(Tag,"Permission requested");
            }
            permission = ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH);
            if (permission != PackageManager.PERMISSION_GRANTED)
            {//若無權限則向使用者要求
                ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.BLUETOOTH}, 2);
                Log.d(Tag,"Permission requested");
            }
            if (!BTadapter.isEnabled())
            {//檢查藍芽是否開啟，否則要求使用者開啟
                Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(intent, REQUEST_ENABLE_BT);
            }

        }
        mBTArray = new ArrayAdapter<>(this,android.R.layout.simple_list_item_1);
        discoveredBTList.setAdapter(mBTArray);
        discoveredBTList.setOnItemClickListener(mDeviceClickListener);
    }

    @Override
    protected void onStart()
    {
        super.onStart();
    }

    @Override
    protected void onResume()  //視情況恢復Handler以及BluetoothController
    {
        super.onResume();
        mController = BluetoothController.getInstance(mHandler);
        if(mController.getState() == ConnectConstant.STATE_NONE)
        {
            mController.start();
        }
    }

    @Override
    protected void onDestroy()  //視情況清除BluetoothController(為避免殭屍執行緒殘留，而浪費系統資源)，以及解除藍芽的廣播接收器
    {
        super.onDestroy();
        if(mController != null)
            mController.stop();

        unregisterReceiver(BTreceiver);
    }


    private AdapterView.OnItemClickListener mDeviceClickListener = new AdapterView.OnItemClickListener()  //實作藍芽裝置列表元件的onClick監聽器
    {                                                                                                     //點選裝置及會嘗試與其建立連線
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id)
        {
            String info = ((TextView) view).getText().toString();
            final String address = info.substring(info.length() - 17);  //擷取藍芽裝置實體Address做連線用
            if (BTadapter.isDiscovering())  //若系統正在搜尋新裝置，則停止搜尋
            {
                BTadapter.cancelDiscovery();
                Toast.makeText(getApplicationContext(), "Discovery stopped", Toast.LENGTH_SHORT).show();
            }
            Log.d(Tag,"BTDevice get");
            Address = address;
            mController.connect(address);  //呼叫BluetoothController的connect()函式，開始建立藍芽連線
        }
    };


    private final BroadcastReceiver BTreceiver = new BroadcastReceiver() //實作搜尋藍芽裝置之廣播接收器
    {
        @Override
        public void onReceive(Context context, Intent intent)  //搜尋到藍芽裝置後，系統會呼叫此函式
        {
            String action = intent.getAction();
            if(BluetoothDevice.ACTION_FOUND.equals(action))
            {
                //將搜尋到之藍芽裝置擷取所需資訊，列在MainActivity中藍芽裝置列表中
                BluetoothDevice d = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                mBTArray.add(d.getName()+"\n"+d.getAddress());
                mBTArray.notifyDataSetChanged();
            }
        }
    };

    public void paired(View v)  //getPaired按鈕被點擊後執行的工作，會將已配對過的藍芽裝置列在ListView中
    {

        Set<BluetoothDevice> mPairedDevices = BTadapter.getBondedDevices();
        if(BTadapter.isEnabled())
        {
            mBTArray.clear();
            for (BluetoothDevice device : mPairedDevices)
                mBTArray.add(device.getName() + "\n" + device.getAddress());
            Toast.makeText(getApplicationContext(), "Show Paired Devices", Toast.LENGTH_SHORT).show();
        }
        else
            Toast.makeText(getApplicationContext(), "Bluetooth not on", Toast.LENGTH_SHORT).show();
    }

    public void findNew(View v)  //findNew按鈕被點擊後執行的工作，會將搜尋到的藍芽裝置一一列在ListView中
    {
        if (BTadapter.isDiscovering())  //搜尋開始後將會持續一段時間(不是瞬間就能抓到附近的所有藍芽裝置)，在這段時間內若按鈕再次被點擊，將會停止搜尋
        {
            BTadapter.cancelDiscovery();
            Toast.makeText(getApplicationContext(), "Discovery stopped", Toast.LENGTH_SHORT).show();
        }
        else
        {
            if (BTadapter.isEnabled())  //隨時檢查BTadapter是否仍活著(可能手機藍芽晶片不穩定之類的)
            { //如果尚未連線到任何藍芽裝置且已按下尋找
                mBTArray.clear();  //先將ListView清空
                BTadapter.startDiscovery(); //開始尋找
                Toast.makeText(getApplicationContext(), "Discovery started", Toast.LENGTH_SHORT).show();
                IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);  //設定搜尋藍芽裝置之意圖過濾器，攔截來自系統的藍芽廣播事件
                registerReceiver(BTreceiver, filter);  //將廣播接收器以及意圖過濾器向系統註冊
            }
            else
            {
                Toast.makeText(getApplicationContext(), "Bluetooth not on", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void find()  //取得Activity存放之各原件的記憶體位置。由於元件可能眾多，將程式碼抽離寫在此函式中，可增加程式碼易讀性
    {
        discoveredBTList = findViewById(R.id.discoveredList);
        getPaired = findViewById(R.id.paired);
        findNew = findViewById(R.id.findnew);
        bBackDoor = findViewById(R.id.backdoor);
        BTstatus = findViewById(R.id.status);
    }

    public void backdoor(View v)  //模擬器不支援藍芽功能，因此需裝設後門避開藍芽連線問題，直接進入導航畫面
    {
        Toast.makeText(getApplicationContext(), "Back door", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(MainActivity.this, ControllerActivity.class);
        Bundle bundle = new Bundle();
        bundle.putString("device", "");
        bundle.putBoolean("try_to_reconnect", false);
        intent.putExtras(bundle);
        //切換Activity
        this.startActivity(intent);
    }

}