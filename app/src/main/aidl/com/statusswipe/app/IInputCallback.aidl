package com.statusswipe.app;

interface IInputCallback {
    void onTouchDown(int slot, float normalizedX, float normalizedY, long timestamp);
    void onTouchMove(int slot, float normalizedX, float normalizedY, long timestamp);
    void onTouchUp(int slot, long timestamp);
    void onDeviceInfo(String path, String name, int minX, int maxX, int minY, int maxY);
    void onError(String message);
}
