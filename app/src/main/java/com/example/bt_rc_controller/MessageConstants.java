package com.example.bt_rc_controller;

public interface MessageConstants
{
    // Message types sent from the Bluetooth Handler
    public static final int MESSAGE_STATE_CHANGE = 0;
    public static final int MESSAGE_READ = 1;
    public static final int MESSAGE_TOAST = 2;
    public static final int MESSAGE_WRITE = 3;
    // Key names received from the BluetoothChatService Handler
    public static final String TOAST = "toast";
}
