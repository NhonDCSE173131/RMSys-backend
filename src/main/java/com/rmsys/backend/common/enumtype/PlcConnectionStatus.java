package com.rmsys.backend.common.enumtype;

/**
 * PLC connection status for runtime connection manager.
 */
public enum PlcConnectionStatus {
    ONLINE,
    CONNECTING,
    DISCONNECTED,
    STALE,
    ERROR,
    DISABLED
}

