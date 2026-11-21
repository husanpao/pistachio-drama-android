package com.hongguo.core;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Process-wide background executor and image cache bootstrap. */
public class App extends Application {
    private static ExecutorService io;
    private static Handler main;

    @Override
    public void onCreate() {
        super.onCreate();
        io = Executors.newFixedThreadPool(4);
        main = new Handler(Looper.getMainLooper());
        ImageLoader.init(this);
        io.execute(() -> MediaCache.cleanInvalid(this));
    }

    public static void io(Runnable task) {
        io.execute(task);
    }

    public static void ui(Runnable task) {
        main.post(task);
    }

    public static ExecutorService executor() {
        return io;
    }
}
