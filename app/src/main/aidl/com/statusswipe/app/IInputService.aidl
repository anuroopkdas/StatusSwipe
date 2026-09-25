package com.statusswipe.app;

import com.statusswipe.app.IInputCallback;

interface IInputService {
    void startObserving();
    void stopObserving();
    boolean isObserving();
    void registerCallback(IInputCallback callback);
}
