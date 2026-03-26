package com.rmsys.backend.infrastructure.adapter;

public interface DeviceAdapter {
    String adapterCode();
    void start();
    void stop();
    boolean supports(String machineVendor);
}

