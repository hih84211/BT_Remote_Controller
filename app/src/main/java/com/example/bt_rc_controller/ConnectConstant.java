package com.example.bt_rc_controller;

public interface ConnectConstant
{
    public static final int STATE_NONE = 0;     // now listening for incoming connections
    public static final int STATE_CONNECTING = 1; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 2;  // now connected to a remote device
    public static final int STATE_CONNECT_FAILED = 3;
    public static final int STATE__RECONNECTED = 4;
}
