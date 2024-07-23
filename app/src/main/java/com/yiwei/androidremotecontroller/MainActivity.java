package com.yiwei.androidremotecontroller;

import android.Manifest;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.InputType;
import android.util.Log;
import android.view.DragEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.yiwei.androidremotecontroller.arena.ArenaTileView;
import com.yiwei.androidremotecontroller.arena.ArenaView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private final String TAG = MainActivity.class.getSimpleName();

    private static final UUID BT_MODULE_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); // "random" unique identifier

    // #defines for identifying shared types between calling functions
    private final static int REQUEST_ENABLE_BT = 1; // used to identify adding bluetooth names
    public final static int MESSAGE_READ = 2; // used in bluetooth handler to identify message update
    private final static int CONNECTING_STATUS = 3; // used in bluetooth handler to identify message status

    public enum AlgoType {
        SQ, EX
    }

    // GUI Components
    private TextView mBluetoothStatus;
    public TextView mReadBuffer;
    private TextView mDevicesDialogTitle;
    private ListView mDevicesListView;
    private Dialog mDevicesDialog;
    private Button mTurnLeft;
    private Button mTurnRight;
    private Button mForward;
    private Button mReverse;
    private EditText mCoord;
    private EditText mDirection;
    private EditText mMessage;
    private EditText mObsCoord;
    private EditText mLog;
    private Button mSendMsg;
    private Button mAddObs;
    public Button mStart;
    public CheckBox cbSimulation;
    public ArenaView mArenaView;

    private BluetoothAdapter mBTAdapter;
    private Set<BluetoothDevice> mPairedDevices;
    private ArrayAdapter<String> mBTArrayAdapter;

    private Handler mHandler; // Our main handler that will receive callback notifications
    private ConnectedThread mConnectedThread; // bluetooth background worker thread to send and receive data
    private BluetoothSocket mBTSocket = null; // bi-directional client-to-client data path

    public Menu mainMenu;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mBluetoothStatus = (TextView)findViewById(R.id.bluetooth_status);
        mReadBuffer = (TextView) findViewById(R.id.read_buffer);
        mTurnLeft = (Button) findViewById(R.id.btn_left);
        mTurnRight = (Button) findViewById(R.id.btn_right);
        mForward = (Button) findViewById(R.id.btn_forward);
        mReverse = (Button) findViewById(R.id.btn_reverse);
        mCoord = (EditText) findViewById(R.id.tb_coord);
        mDirection = (EditText) findViewById(R.id.tb_direction);
        //mMessage = (EditText) findViewById(R.id.tb_obs_coord);
        mObsCoord = (EditText) findViewById(R.id.tb_obs_coord);
        //mSendMsg = (Button) findViewById(R.id.btn_add_obs);
        mAddObs = (Button) findViewById(R.id.btn_add_obs);
        mStart = (Button) findViewById(R.id.btn_start);
        cbSimulation = (CheckBox) findViewById(R.id.cb_simulation) ;
        mArenaView = (ArenaView) findViewById(R.id.arena_view);
        mLog = (EditText) findViewById(R.id.tb_log);

        if (mArenaView != null) {
            mArenaView.mainActivity = this;
            mArenaView.onMainActivityProvided();
        }

        mCoord.setFocusable(false);
        mDirection.setFocusable(false);
        mCoord.setInputType(InputType.TYPE_NULL);
        mDirection.setInputType(InputType.TYPE_NULL);

        mBTArrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        mBTAdapter = BluetoothAdapter.getDefaultAdapter(); // get a handle on the bluetooth radio

        // Ask for location permission if not already allowed
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 1);

//        mSendMsg.setOnClickListener(view -> {
//            // sendMessageToArena(((EditText)findViewById(R.id.tb_message)).getText().toString());
//
//            // send message to AMD
//            mConnectedThread.write(((EditText)findViewById(R.id.tb_obs_coord)).getText().toString());
//        });

        mAddObs.setOnClickListener(view -> {
            String obsCoord = mObsCoord.getText().toString();
            obsCoord = obsCoord.replaceAll(" ", "");
            int x = Integer.parseInt(obsCoord.split(",")[0]);
            int y = Integer.parseInt(obsCoord.split(",")[1]);
            ArenaTileView tile = mArenaView.getTileFromAxis(x, y);
            this.mArenaView.addObstacle(tile);
        });

        mStart.setOnClickListener(view -> {
            if (this.mArenaView.timer == null) {
                // make sure all obs sides are set
                if (!this.mArenaView.isAllObsSideSet()) {
                    Toast.makeText(this, "Make sure all obstacle sides are set!", Toast.LENGTH_SHORT).show();
                    return;
                }

                mStart.setEnabled(false);

                // start the calculation and timer
                if (getCurrentAlgoType().equals(AlgoType.SQ.name()))
                    this.mArenaView.calculatePathFromInternal();
                else if (getCurrentAlgoType().equals(AlgoType.EX.name()))
                    this.mArenaView.calculatePathFromApi();
                Log.e("MainActivity", "starting timer");
            } else {
                // stop the timer
                Log.e("MainActivity", "stopping timer");
                this.mArenaView.stopTimer();
            }
        });

        mHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg){
                if(msg.what == MESSAGE_READ){
                    String readMessage = null;
                    readMessage = new String((byte[]) msg.obj, StandardCharsets.UTF_8);
                    // mReadBuffer.setText(readMessage);
                    Log.e("MainActivity", "message: " + readMessage);

                    sendMessageToArena(readMessage);
                }

                if(msg.what == CONNECTING_STATUS){
                    char[] sConnected;
                    if(msg.arg1 == 1) {
                        mBluetoothStatus.setText(getString(R.string.BTConnected) + " " + msg.obj);

                        // set status
                        mReadBuffer.setText("BT connected");

                        // send connected message to AMD
                        JSONObject obj = new JSONObject();
                        JSONObject obj2 = new JSONObject();
                        try {
                            obj2.put("status", "BT connected");
                            obj.put("bt", obj2);
                            sendMessageToAMD(obj.toString());
                        } catch (JSONException ignored) {}
                    } else
                        mBluetoothStatus.setText(getString(R.string.BTconnFail));
                }
            }
        };

        if (mBTArrayAdapter == null) {
            // Device does not support Bluetooth
            mBluetoothStatus.setText(getString(R.string.sBTstaNF));
            Toast.makeText(getApplicationContext(),getString(R.string.sBTdevNF),Toast.LENGTH_SHORT).show();
        } else {
            mForward.setOnClickListener(view -> {
                JSONObject obj = new JSONObject();
                try {
                    obj.put("move", "f");
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
                sendMessageToAMD(obj.toString());
                this.mArenaView.moveRobotForward();
            });

            mReverse.setOnClickListener(view -> {
                JSONObject obj = new JSONObject();
                try {
                    obj.put("move", "b");
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
                sendMessageToAMD(obj.toString());
                this.mArenaView.moveRobotBackward();
            });

            mTurnLeft.setOnClickListener(view -> {
                JSONObject obj = new JSONObject();
                try {
                    obj.put("move", "l");
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
                sendMessageToAMD(obj.toString());
                this.mArenaView.moveRobotLeft();
            });

            mTurnRight.setOnClickListener(view -> {
                JSONObject obj = new JSONObject();
                try {
                    obj.put("move", "r");
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
                sendMessageToAMD(obj.toString());
                this.mArenaView.moveRobotRight();
            });
        }

        setupDragListener();

        String algoType = getCurrentAlgoType();
        if (getSupportActionBar() != null && getSupportActionBar().getTitle() != null)
            getSupportActionBar().setTitle(getSupportActionBar().getTitle() + " (Algo" + algoType + ")");

//        IntentFilter filter = new IntentFilter();
//        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
//        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED);
//        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
//        this.registerReceiver(BTReceiver, filter);
    }

    //The BroadcastReceiver that listens for bluetooth broadcasts
//    private final BroadcastReceiver BTReceiver = new BroadcastReceiver() {
//        @Override
//        public void onReceive(Context context, Intent intent) {
//            String action = intent.getAction();
//
//            if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
//                //Do something if connected
//                Toast.makeText(getApplicationContext(), "BT Connected", Toast.LENGTH_SHORT).show();
//            } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
//                //Do something if disconnected
//                Toast.makeText(getApplicationContext(), "BT Disconnected", Toast.LENGTH_SHORT).show();
//            }
//            //else if...
//        }
//    };

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);

        this.mainMenu = menu;

        menu.getItem(9).setChecked(isRobotBindToImage());

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        AlertDialog.Builder builder;

        // Handle item selection.
        switch (item.getItemId()) {
            case R.id.bluetooth_on:
                bluetoothOn();
                return true;
            case R.id.bluetooth_off:
                bluetoothOff();
                return true;
            case R.id.reconnect:
                if (getStoredDeviceAddr().isEmpty() || getStoredDeviceAddr() == null) {
                    Toast.makeText(this, "No previously connected device", Toast.LENGTH_SHORT).show();
                    return true;
                }

                // reconnect to previous device using shared pref
                String deviceName = getStoredDeviceName();
                String deviceAddr = getStoredDeviceAddr();
                Log.e(TAG, "trying direct connect, device name: " + deviceName + ", device addr: " + deviceAddr);
                connectSavedDevice(deviceName, deviceAddr);
                return true;
            case R.id.direct_connect:
                builder = new AlertDialog.Builder(this);
                builder.setTitle("Bluetooth Direct Connect, Enter device BT address");

                // Set up the input
                final EditText input = new EditText(this);
                input.setText(getStoredDeviceAddr());
                builder.setView(input);

                // Set up the buttons
                builder.setPositiveButton("OK", (dialog, which) -> {
                    String addr = input.getText().toString();
                    // save the BT addr
                    storeConnectedDeviceInfo("", addr);
                    Log.e(TAG, "trying direct connect, device addr: " + addr);
                    connectSavedDevice("", addr);
                });
                builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

                builder.show();
                return true;
            case R.id.connect:
                discover();
                return true;
            case R.id.reset_arena:
                mArenaView.reset();
                return true;
            case R.id.algo_sq:
                switchAlgo(AlgoType.SQ);
                return true;
            case R.id.algo_ex:
                switchAlgo(AlgoType.EX);
                return true;
            case R.id.algo_ex_ip:
                builder = new AlertDialog.Builder(this);
                builder.setTitle("Configure AlgoEX Server IP (with port)");

                // Set up the input
                final EditText input2 = new EditText(this);
                input2.setText(getAlgoEXServerIP());
                builder.setView(input2);

                // Set up the buttons
                builder.setPositiveButton("OK", (dialog, which) -> {
                    String ip = input2.getText().toString();
                    // save the ip (with port)
                    saveAlgoEXServerIP(ip);
                });
                builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

                builder.show();
                return true;
            case R.id.cb_bind_robot_to_image:
                setRobotBindToImage(!item.isChecked());
                item.setChecked(!item.isChecked());

                return true;
            case R.id.straight_distance_per_move:
                builder = new AlertDialog.Builder(this);
                builder.setTitle("Set the straight(forward/backward) distance(axis) per move for manual robot movement");

                // Set up the input
                final EditText input3 = new EditText(this);
                input3.setText(getRobotStraightDistancePerMove() + "");
                input3.setInputType(InputType.TYPE_CLASS_NUMBER);
                builder.setView(input3);

                // Set up the buttons
                builder.setPositiveButton("OK", (dialog, which) -> {
                    String distance = input3.getText().toString();
                    int distanceInt = Integer.parseInt(distance);
                    if (distanceInt < 1) {
                        Toast.makeText(this, "Invalid distance!", Toast.LENGTH_SHORT).show();
                    } else {
                        // save the distance
                        setRobotStraightDistancePerMove(distanceInt);
                    }
                });
                builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

                builder.show();
                return true;
            case R.id.turning_distance_per_move:
                builder = new AlertDialog.Builder(this);
                builder.setTitle("Set the turning(left/right) distance(axis)/offset per move for manual robot movement: (side offset,vertical offset)");

                // Set up the input
                final EditText input4 = new EditText(this);
                input4.setText(getRobotTurningDistancePerMove() + "");
                builder.setView(input4);

                // Set up the buttons
                builder.setPositiveButton("OK", (dialog, which) -> {
                    String distance = input4.getText().toString();
                    int x = Integer.parseInt(distance.split(",")[0]);
                    int y = Integer.parseInt(distance.split(",")[1]);
                    // save the distance
                    setRobotTurningDistancePerMove(x, y);
                });
                builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

                builder.show();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    // Enter here after user selects "yes" or "no" to enabling radio
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent Data){
        // Check which request we're responding to
        if (requestCode == REQUEST_ENABLE_BT) {
            // Make sure the request was successful
            if (resultCode == RESULT_OK) {
                // The user picked a contact.
                // The Intent's data Uri identifies which contact was selected.
                mBluetoothStatus.setText(getString(R.string.sEnabled));
            }
            else
                mBluetoothStatus.setText(getString(R.string.sDisabled));
        }
    }

    private void sendMessageToArena(String message) {
        // parse message JSON string and pass the JSON object to ArenaView
        mArenaView.onMessage(message, MainActivity.this);
    }

    public void setDirText(String dir) {
        this.mDirection.setText(dir);
    }

    public void sendMessageToAMD(String message) {
        Log.e(TAG, "sending message to AMD: " + message);
        try {
            mConnectedThread.write(message);
        } catch (Exception ignored) {}
    }

    private void bluetoothOn(){
        if (!mBTAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            mBluetoothStatus.setText(getString(R.string.BTEnable));
            Toast.makeText(getApplicationContext(),getString(R.string.sBTturON),Toast.LENGTH_SHORT).show();

        }
        else{
            Toast.makeText(getApplicationContext(),getString(R.string.BTisON), Toast.LENGTH_SHORT).show();
        }
    }

    private void bluetoothOff(){
        mBTAdapter.disable(); // turn off
        mBluetoothStatus.setText(getString(R.string.sBTdisabl));
        Toast.makeText(getApplicationContext(),"Bluetooth turned Off", Toast.LENGTH_SHORT).show();
    }

    private void discover(){
        mDevicesDialog = new Dialog(MainActivity.this);

        mDevicesDialog.setContentView(R.layout.devices_dialog);
        mDevicesDialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mDevicesDialog.setCancelable(false);
        mDevicesDialog.setCanceledOnTouchOutside(true);
        // dialog.getWindow().getAttributes().windowAnimations = R.style.animation;

        mDevicesDialog.setOnShowListener(dialogInterface -> {
            mDevicesDialogTitle = (TextView) mDevicesDialog.findViewById(R.id.devices_dialog_title);
            mDevicesListView = (ListView) mDevicesDialog.findViewById(R.id.devices_list_view);
            mDevicesListView.setAdapter(mBTArrayAdapter); // assign model to view
            mDevicesListView.setOnItemClickListener(mDeviceClickListener);

            mDevicesDialogTitle.setText("Discover New Devices");

            // Check if the device is already discovering
            if(mBTAdapter.isDiscovering()){
                mBTAdapter.cancelDiscovery();
                Toast.makeText(getApplicationContext(),getString(R.string.DisStop),Toast.LENGTH_SHORT).show();
            }
            else{
                if(mBTAdapter.isEnabled()) {
                    mBTArrayAdapter.clear(); // clear items
                    mBTAdapter.startDiscovery();
                    Toast.makeText(getApplicationContext(), getString(R.string.DisStart), Toast.LENGTH_SHORT).show();
                    registerReceiver(blReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
                }
                else{
                    Toast.makeText(getApplicationContext(), getString(R.string.BTnotOn), Toast.LENGTH_SHORT).show();
                }
            }
        });

        mDevicesDialog.show();
    }

    private void listPairedDevices(){
        mDevicesDialog = new Dialog(MainActivity.this);

        mDevicesDialog.setContentView(R.layout.devices_dialog);
        mDevicesDialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mDevicesDialog.setCancelable(false);
        mDevicesDialog.setCanceledOnTouchOutside(true);
        // dialog.getWindow().getAttributes().windowAnimations = R.style.animation;

        mDevicesDialog.setOnShowListener(dialogInterface -> {
            mDevicesDialogTitle = (TextView) mDevicesDialog.findViewById(R.id.devices_dialog_title);
            mDevicesDialogTitle.setText("Show Paired Devices");

            mBTArrayAdapter.clear();
            mPairedDevices = mBTAdapter.getBondedDevices();
            if(mBTAdapter.isEnabled()) {
                // put it's one to the adapter
                for (BluetoothDevice device : mPairedDevices)
                    mBTArrayAdapter.add(device.getName() + "\n" + device.getAddress());
                Toast.makeText(getApplicationContext(), getString(R.string.show_paired_devices), Toast.LENGTH_SHORT).show();
            }
            else
                Toast.makeText(getApplicationContext(), getString(R.string.BTnotOn), Toast.LENGTH_SHORT).show();
        });

        mDevicesDialog.show();
    }

    final BroadcastReceiver blReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(BluetoothDevice.ACTION_FOUND.equals(action)){
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                // add the name to the list
                mBTArrayAdapter.add(device.getName() + "\n" + device.getAddress());
                mBTArrayAdapter.notifyDataSetChanged();
            }
        }
    };

    private final AdapterView.OnItemClickListener mDeviceClickListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

            if (mDevicesDialog != null) {
                mDevicesDialog.cancel();
            }

            // Get the device MAC address, which is the last 17 chars in the View
            String info = ((TextView) view).getText().toString();
            final String address = info.substring(info.length() - 17);
            final String name = info.substring(0,info.length() - 17);

            Log.e("MainActivity", "name: " + name);
            Log.e("MainActivity", "address: " + address);

            // store the name and addr into sharedpref
            storeConnectedDeviceInfo(name, address);

            connectSavedDevice(name, address);
        }
    };

    private void connectSavedDevice(String name, String address) {
        if (mBTAdapter.isEnabled()) {
            BluetoothDevice device = mBTAdapter.getRemoteDevice(address);

            BluetoothSocket socket = null;

            boolean fail = false;

            try {
                mLog.setText(mLog.getText() + "\nConnecting to: " + address);
                socket = device.createRfcommSocketToServiceRecord(BT_MODULE_UUID);
            } catch (Exception e) {
                Log.e("","Error creating socket");
                mLog.setText(mLog.getText() + "\nError creating socket");
                Toast.makeText(getBaseContext(), getString(R.string.ErrSockCrea), Toast.LENGTH_SHORT).show();
            }

            try {
                socket.connect();
                Log.e("","Connected");
                mLog.setText(mLog.getText() + "\nConnected");
            } catch (IOException e) {
                Log.e("", e.getMessage());
                mLog.setText(mLog.getText() + "\n" + e.getMessage());
                try {
                    Log.e("","trying fallback...");
                    mLog.setText(mLog.getText() + "\ntrying fallback...");

                    socket =(BluetoothSocket) device.getClass().getMethod("createRfcommSocket", new Class[] {int.class}).invoke(device,1);
                    socket.connect();

                    Log.e("","Connected");
                    mLog.setText(mLog.getText() + "\nConnected");
                }
                catch (Exception e2) {
                    Log.e("", "Couldn't establish Bluetooth connection!");
                    mLog.setText(mLog.getText() + "\nCouldn't establish Bluetooth connection!");
                    try {
                        fail = true;
                        Log.e(TAG, e2.getMessage());
                        mLog.setText(mLog.getText() + "\n" + e2.getMessage());
                        socket.close();
                        mHandler.obtainMessage(CONNECTING_STATUS, -1, -1)
                                .sendToTarget();
                    } catch (IOException e3) {
                        //insert code to deal with this
                        mLog.setText(mLog.getText() + "\n" + e3.getMessage());
                        Toast.makeText(getBaseContext(), getString(R.string.ErrSockCrea), Toast.LENGTH_SHORT).show();
                    }
                }
            }

            if(!fail) {
                mConnectedThread = new ConnectedThread(socket, mHandler);
                mConnectedThread.start();

                mHandler.obtainMessage(CONNECTING_STATUS, 1, -1, name)
                        .sendToTarget();
            }
        }
    }

    private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {
        try {
            final Method m = device.getClass().getMethod("createInsecureRfcommSocketToServiceRecord", UUID.class);
            return (BluetoothSocket) m.invoke(device, BT_MODULE_UUID);
        } catch (Exception e) {
            Log.e(TAG, "Could not create Insecure RFComm Connection",e);
        }
        return  device.createRfcommSocketToServiceRecord(BT_MODULE_UUID);
    }

    private void setupDragListener() {
        // Set the drag event listener for the Tile.
        findViewById(R.id.main_layout).setOnDragListener( (v, e) -> {

            // Handle each of the expected events.
            switch(e.getAction()) {

                case DragEvent.ACTION_DRAG_STARTED:

                    // always return true to accept drop
                    return true;

                case DragEvent.ACTION_DRAG_ENTERED:

                    Log.e("MainActivity", "Drag enter MainActivity");

                    // Return true. The value is ignored.
                    return true;

                case DragEvent.ACTION_DROP:

                    Log.e("MainActivity", "Drag dropped into MainActivity");

                    // Get the item containing the dragged data.
                    ClipData.Item item = e.getClipData().getItemAt(0);

                    // Get the obstacle id from the item.
                    CharSequence obstacleIdStr = item.getText();
                    int obstacleId = Integer.parseInt(obstacleIdStr + "");

                    // inform arena to remove obstacle
                    mArenaView.removeObstacle(obstacleId);

                    // Return true. DragEvent.getResult() returns true.
                    return true;

                // An unknown action type is received.
                default:
                    Log.e("MainActivity","Unknown action type received by View.OnDragListener.");
                    break;
            }

            return false;

        });
    }

    private void switchAlgo(AlgoType algoType) {
        SharedPreferences sharedPreferences = getSharedPreferences("MySharedPref", MODE_PRIVATE);
        SharedPreferences.Editor myEdit = sharedPreferences.edit();
        myEdit.putString("algo_type", algoType.name());
        myEdit.apply();

        // restart app manually
        Toast.makeText(getApplicationContext(), "Relaunch app to switch algo type!", Toast.LENGTH_SHORT).show();
    }

    public void storeConnectedDeviceInfo(String name, String addr) {
        // Storing data into SharedPreferences
        SharedPreferences sharedPreferences = getSharedPreferences("MySharedPref", MODE_PRIVATE);

        // Creating an Editor object to edit(write to the file)
        SharedPreferences.Editor myEdit = sharedPreferences.edit();

        // Storing the key and its value as the data
        myEdit.putString("device_name", name);
        myEdit.putString("device_addr", addr);

        // Once the changes have been made, we need to commit to apply those changes made,
        // otherwise, it will throw an error
        myEdit.apply();
    }

    private String getStoredDeviceName() {
        // Retrieving the value using its keys the file name must be same in both saving and retrieving the data
        SharedPreferences sh = getSharedPreferences("MySharedPref", MODE_PRIVATE);

        // We can then use the data
        return sh.getString("device_name", "");
    }

    private String getStoredDeviceAddr() {
        // Retrieving the value using its keys the file name must be same in both saving and retrieving the data
        SharedPreferences sh = getSharedPreferences("MySharedPref", MODE_PRIVATE);

        // We can then use the data
        return sh.getString("device_addr", "");
    }

    private String getCurrentAlgoType() {
        SharedPreferences sh = getSharedPreferences("MySharedPref", MODE_PRIVATE);
        return sh.getString("algo_type", MainActivity.AlgoType.EX.name());
    }

    private void saveAlgoEXServerIP(String ip) {
        SharedPreferences sharedPreferences = getSharedPreferences("MySharedPref", MODE_PRIVATE);
        SharedPreferences.Editor myEdit = sharedPreferences.edit();
        myEdit.putString("server_ip", ip);

        myEdit.apply();
    }

    public String getAlgoEXServerIP() {
        SharedPreferences sh = getSharedPreferences("MySharedPref", MODE_PRIVATE);
        return sh.getString("server_ip", "http://192.168.1.142:5000/path");
    }

    public void setRobotBindToImage(boolean flag) {
        SharedPreferences sh = getSharedPreferences("MySharedPref", MODE_PRIVATE);
        SharedPreferences.Editor myEdit = sh.edit();

        myEdit.putBoolean("robot_bind_to_image", flag);

        myEdit.apply();
    }

    public boolean isRobotBindToImage() {
        SharedPreferences sh = getSharedPreferences("MySharedPref", MODE_PRIVATE);

        return sh.getBoolean("robot_bind_to_image", true);
    }

    /** get the robot distance per move on arena grid, default to 1 */
    public int getRobotStraightDistancePerMove() {
        SharedPreferences sh = getSharedPreferences("MySharedPref", MODE_PRIVATE);

        return sh.getInt("straight_distance_per_move", 1);
    }

    public void setRobotStraightDistancePerMove(int distance) {
        SharedPreferences sh = getSharedPreferences("MySharedPref", MODE_PRIVATE);
        SharedPreferences.Editor myEdit = sh.edit();

        myEdit.putInt("straight_distance_per_move", distance);

        myEdit.apply();
    }

    public String getRobotTurningDistancePerMove() {
        SharedPreferences sh = getSharedPreferences("MySharedPref", MODE_PRIVATE);

        return sh.getString("turning_distance_per_move", "3,1");
    }

    public void setRobotTurningDistancePerMove(int xOffset, int yOffset) {
        SharedPreferences sh = getSharedPreferences("MySharedPref", MODE_PRIVATE);
        SharedPreferences.Editor myEdit = sh.edit();

        myEdit.putString("straight_distance_per_move", xOffset + "," + yOffset);

        myEdit.apply();
    }
}
