#include "evdev_reader.h"
#include <android/log.h>
#include <fcntl.h>
#include <unistd.h>
#include <linux/input.h>
#include <linux/input-event-codes.h>
#include <sys/ioctl.h>
#include <dirent.h>
#include <cstring>
#include <cerrno>
#include <cmath>
#include <algorithm>

#define LOG_TAG "EvdevReader"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define MAX_SLOTS 10

static volatile bool keep_reading = false;
static int evdev_fd = -1;

static bool test_bit(int bit, const uint8_t* array) {
    return (array[bit / 8] & (1 << (bit % 8))) != 0;
}

DeviceInfo discoverTouchDevice() {
    DeviceInfo info = {"", "", 0, 0, 0, 0, 0};
    
    DIR* dir = opendir("/dev/input");
    if (!dir) {
        LOGE("Failed to open /dev/input directory: %s", strerror(errno));
        return info;
    }

    struct dirent* entry;
    while ((entry = readdir(dir)) != nullptr) {
        if (strncmp(entry->d_name, "event", 5) != 0) {
            continue;
        }

        std::string path = std::string("/dev/input/") + entry->d_name;
        int fd = open(path.c_str(), O_RDONLY | O_NONBLOCK);
        if (fd < 0) {
            continue;
        }

        uint8_t evBits[(EV_MAX + 7) / 8] = {0};
        uint8_t absBits[(ABS_MAX + 7) / 8] = {0};

        if (ioctl(fd, EVIOCGBIT(0, sizeof(evBits)), evBits) >= 0) {
            if (test_bit(EV_ABS, evBits)) {
                if (ioctl(fd, EVIOCGBIT(EV_ABS, sizeof(absBits)), absBits) >= 0) {
                    if (test_bit(ABS_MT_POSITION_X, absBits) && test_bit(ABS_MT_POSITION_Y, absBits)) {
                        char name[256] = "Unknown";
                        if (ioctl(fd, EVIOCGNAME(sizeof(name)), name) < 0) {
                            strncpy(name, "Touchscreen", sizeof(name) - 1);
                        }
                        
                        struct input_absinfo abs_x = {0}, abs_y = {0};
                        ioctl(fd, EVIOCGABS(ABS_MT_POSITION_X), &abs_x);
                        ioctl(fd, EVIOCGABS(ABS_MT_POSITION_Y), &abs_y);

                        info.path = path;
                        info.name = name;
                        info.minX = abs_x.minimum;
                        info.maxX = abs_x.maximum;
                        info.minY = abs_y.minimum;
                        info.maxY = abs_y.maximum;
                        info.eventStructSize = sizeof(struct input_event);
                        
                        LOGI("Discovered touchscreen: %s (%s) X=[%d..%d] Y=[%d..%d]",
                             path.c_str(), name, info.minX, info.maxX, info.minY, info.maxY);

                        close(fd);
                        closedir(dir);
                        return info;
                    }
                }
            }
        }
        close(fd);
    }
    closedir(dir);

    LOGW("No touchscreen with ABS_MT_POSITION_X/Y found in /dev/input");
    return info;
}

int detectEventStructSize(const std::string& path) {
    return sizeof(struct input_event);
}

void startReading(const std::string& devicePath, TouchCallback callback) {
    keep_reading = true;
    evdev_fd = open(devicePath.c_str(), O_RDONLY);
    
    if (evdev_fd < 0) {
        LOGE("Failed to open device: %s (%s)", devicePath.c_str(), strerror(errno));
        return;
    }

    struct input_absinfo abs_x = {0}, abs_y = {0};
    ioctl(evdev_fd, EVIOCGABS(ABS_MT_POSITION_X), &abs_x);
    ioctl(evdev_fd, EVIOCGABS(ABS_MT_POSITION_Y), &abs_y);

    float range_x = (abs_x.maximum > abs_x.minimum) ? (float)(abs_x.maximum - abs_x.minimum) : 1080.0f;
    float range_y = (abs_y.maximum > abs_y.minimum) ? (float)(abs_y.maximum - abs_y.minimum) : 2400.0f;

    LOGI("Listening on %s, range X: [%d..%d]=%.0f, Y: [%d..%d]=%.0f",
         devicePath.c_str(), abs_x.minimum, abs_x.maximum, range_x,
         abs_y.minimum, abs_y.maximum, range_y);

    int current_slot = 0;
    
    struct SlotState {
        int tracking_id = -1;
        bool was_active = false;
        float x = 0.0f;
        float y = 0.0f;
        bool position_updated = false;
        bool tracking_updated = false;
    } slots[MAX_SLOTS];

    struct input_event ev_buf[32];
    
    while (keep_reading) {
        ssize_t bytes = read(evdev_fd, ev_buf, sizeof(ev_buf));
        if (bytes < (ssize_t)sizeof(struct input_event)) {
            if (bytes < 0 && errno != EINTR && errno != EAGAIN) {
                LOGE("Error reading from %s: %s", devicePath.c_str(), strerror(errno));
                break;
            }
            continue;
        }

        size_t num_events = bytes / sizeof(struct input_event);

        for (size_t e = 0; e < num_events; ++e) {
            const struct input_event& ev = ev_buf[e];

            if (ev.type == EV_ABS) {
                if (ev.code == ABS_MT_SLOT) {
                    if (ev.value >= 0 && ev.value < MAX_SLOTS) {
                        current_slot = ev.value;
                    }
                } else if (ev.code == ABS_MT_TRACKING_ID) {
                    slots[current_slot].tracking_id = ev.value;
                    slots[current_slot].tracking_updated = true;
                } else if (ev.code == ABS_MT_POSITION_X) {
                    float normX = (float)(ev.value - abs_x.minimum) / range_x;
                    slots[current_slot].x = std::max(0.0f, std::min(1.0f, normX));
                    slots[current_slot].position_updated = true;
                } else if (ev.code == ABS_MT_POSITION_Y) {
                    float normY = (float)(ev.value - abs_y.minimum) / range_y;
                    slots[current_slot].y = std::max(0.0f, std::min(1.0f, normY));
                    slots[current_slot].position_updated = true;
                }
            } else if (ev.type == EV_SYN && ev.code == SYN_REPORT) {
                int64_t timestamp = (int64_t)ev.time.tv_sec * 1000LL + ((int64_t)ev.time.tv_usec / 1000LL);
                
                for (int i = 0; i < MAX_SLOTS; ++i) {
                    bool is_active = (slots[i].tracking_id != -1);
                    
                    if (slots[i].tracking_updated) {
                        if (is_active && !slots[i].was_active) {
                            // Touch DOWN
                            TouchEvent te = {i, TouchAction::DOWN, slots[i].x, slots[i].y, timestamp};
                            callback(te);
                        } else if (!is_active && slots[i].was_active) {
                            // Touch UP
                            TouchEvent te = {i, TouchAction::UP, slots[i].x, slots[i].y, timestamp};
                            callback(te);
                        }
                        slots[i].was_active = is_active;
                        slots[i].tracking_updated = false;
                        slots[i].position_updated = false;
                    } else if (is_active && slots[i].position_updated) {
                        // Touch MOVE
                        TouchEvent te = {i, TouchAction::MOVE, slots[i].x, slots[i].y, timestamp};
                        callback(te);
                        slots[i].position_updated = false;
                    }
                }
            }
        }
    }
    
    if (evdev_fd >= 0) {
        close(evdev_fd);
        evdev_fd = -1;
    }
    LOGI("Stopped reading from %s", devicePath.c_str());
}

void stopReading() {
    keep_reading = false;
    if (evdev_fd >= 0) {
        // Close the fd to immediately unblock the blocking read()
        close(evdev_fd);
        evdev_fd = -1;
    }
}
