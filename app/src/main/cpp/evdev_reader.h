#pragma once

#include <string>
#include <vector>
#include <cstdint>
#include <functional>

enum class TouchAction {
    DOWN,
    MOVE,
    UP
};

struct TouchEvent {
    int slot;
    TouchAction action;
    float normalizedX;
    float normalizedY;
    int64_t timestamp;
};

struct DeviceInfo {
    std::string path;
    std::string name;
    int32_t minX;
    int32_t maxX;
    int32_t minY;
    int32_t maxY;
    int eventStructSize;
};

using TouchCallback = std::function<void(const TouchEvent&)>;

DeviceInfo discoverTouchDevice();
int detectEventStructSize(const std::string& path);
void startReading(const std::string& devicePath, TouchCallback callback);
void stopReading();
