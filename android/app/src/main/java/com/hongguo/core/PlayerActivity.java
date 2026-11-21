package com.hongguo.core;

import android.app.Activity;
import android.app.Dialog;
import android.app.PictureInPictureParams;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Rational;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Full-screen player with quality/speed/gesture controls and episode switching. */
public class PlayerActivity extends Activity {
    private static final String EXTRA_SERIES_ID = "series_id";
    private static final String EXTRA_TITLE = "title";
    private static final String EXTRA_COVER = "cover";
    private static final String EXTRA_VID = "vid";
    private static final String EXTRA_INDEX = "index";
    private static final String EXTRA_EPISODES = "episode_vids";
    private static final String EXTRA_LANDSCAPE = "landscape";
    private static final String EXTRA_POSITION = "start_position";
    private static final float[] SPEEDS = {0.75f, 1f, 1.25f, 1.5f, 2f};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable hideControlsTask = () -> hideControls();
    private final Runnable progressTask = new Runnable() {
        @Override
        public void run() {
            if (destroyed) return;
            refreshProgress();
            handler.postDelayed(this, 500);
        }
    };

    private String seriesId;
    private String title;
    private String cover;
    private String vid;
    private int index;
    private JSONArray episodeVids = new JSONArray();
    private String requestedQuality;

    private PlayerView playerView;
    private int videoWidth;
    private int videoHeight;
    private ExoPlayer player;
    private FrameLayout root;
    private View touchLayer;
    private LinearLayout controls;
    private ImageView playPause;
    private ImageView favoriteIcon;
    private SeekBar seekBar;
    private TextView positionView;
    private TextView durationView;
    private TextView episodeLabel;
    private TextView qualityButton;
    private TextView speedButton;
    private TextView nextButton;
    private TextView scaleButton;
    private TextView fullscreenButton;
    private TextView centerStatus;
    private LinearLayout loadingBox;
    private TextView loadingText;
    private ProgressBar downloadBar;
    private TextView gestureHint;
    private TextView autoNextTip;
    private ProgressBar buffering;

    private boolean destroyed;
    private boolean prepared;
    private boolean controlsVisible = true;
    private boolean draggingSeek;
    private boolean landscape;
    private boolean showingError;
    private boolean favorite;
    private boolean userPaused;
    private boolean resumeAfterPause;
    private boolean fillMode;
    private boolean effectiveFill;
    private boolean manualScale;
    private float speed = 1f;
    private String currentDefinition = "";
    private float initialBrightness = -1f;
    private float downX;
    private float downY;
    private int gestureMode;
    private int verticalEpisodeDirection;
    private boolean verticalHandled;
    private long seekPreview;
    private long pendingResumeMs;
    private long lastSave;
    private List<HongguoApi.TrackInfo> tracks = new ArrayList<>();
    private final Set<String> failedQualities = new HashSet<>();
    private final Set<String> prefetching =
            java.util.Collections.synchronizedSet(new HashSet<>());
    private final Object mediaLock = MediaCache.LOCK;
    private String lastErrorKey = "";
    private String loadRetryVid = "";
    private int loadRetryCount;
    private boolean autoAdvancing;
    private boolean seamlessTransition;
    private boolean suppressControlsOnReady;
    private long prefetchWaitSince;
    private boolean pipActive;
    private boolean pipClosed;
    private boolean pipRequested;
    private boolean episodesLoading;
    private GestureDetector gestureDetector;
    private Runnable autoNextTask;

    public static void open(Context context, String seriesId, String title, String cover,
                            String vid, int index, String episodeJson) {
        Intent intent = new Intent(context, PlayerActivity.class);
        intent.putExtra(EXTRA_SERIES_ID, seriesId);
        intent.putExtra(EXTRA_TITLE, title);
        intent.putExtra(EXTRA_COVER, cover);
        intent.putExtra(EXTRA_VID, vid);
        intent.putExtra(EXTRA_INDEX, index);
        intent.putExtra(EXTRA_EPISODES, episodeJson);
        context.startActivity(intent);
    }

    /** Fluent builder used by the "continue watching" shelf. */
    public static final class IntentBuilder {
        private final Context context;
        private final Intent intent = new Intent();

        public IntentBuilder(Context context, String seriesId, String title, String cover,
                             String vid, int index) {
            this.context = context;
            intent.setClass(context, PlayerActivity.class);
            intent.putExtra(EXTRA_SERIES_ID, seriesId);
            intent.putExtra(EXTRA_TITLE, title);
            intent.putExtra(EXTRA_COVER, cover);
            intent.putExtra(EXTRA_VID, vid);
            intent.putExtra(EXTRA_INDEX, index);
        }

        public IntentBuilder episodes(String json) {
            intent.putExtra(EXTRA_EPISODES, json);
            return this;
        }

        public void launch() {
            context.startActivity(intent);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        landscape = getIntent().getBooleanExtra(EXTRA_LANDSCAPE, false);
        setRequestedOrientation(landscape
                ? android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                : android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Theme.applyWindowBackground(this, Color.BLACK);
        seriesId = getIntent().getStringExtra(EXTRA_SERIES_ID);
        title = getIntent().getStringExtra(EXTRA_TITLE);
        cover = getIntent().getStringExtra(EXTRA_COVER);
        vid = getIntent().getStringExtra(EXTRA_VID);
        index = getIntent().getIntExtra(EXTRA_INDEX, 0);
        try {
            String json = getIntent().getStringExtra(EXTRA_EPISODES);
            episodeVids = new JSONArray(json == null ? "[]" : json);
        } catch (Exception ignored) {
            episodeVids = new JSONArray();
        }
        requestedQuality = AppPrefs.quality(this);
        speed = AppPrefs.playbackSpeed(this);
        fillMode = AppPrefs.fillScreen(this);
        effectiveFill = fillMode;
        favorite = LibraryStore.isFavorite(this, seriesId);
        WindowManager.LayoutParams attrs = getWindow().getAttributes();
        initialBrightness = attrs.screenBrightness;
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            attrs.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(attrs);
        }
        int startPosition = getIntent().getIntExtra(EXTRA_POSITION, 0);
        pendingResumeMs = startPosition > 0 ? startPosition : AppPrefs.position(this, vid);

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        playerView = (PlayerView) LayoutInflater.from(this)
                .inflate(R.layout.player_surface, root, false);
        playerView.setUseController(false);
        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        root.addView(playerView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        touchLayer = new View(this);
        root.addView(touchLayer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        touchLayer.setOnTouchListener(this::onGestureTouch);

        buildOverlay();
        setContentView(root);
        if (fullscreenButton != null) {
            fullscreenButton.setText(landscape ? "竖屏" : "全屏");
        }
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                android.graphics.Insets cut = insets.getInsets(
                        android.view.WindowInsets.Type.displayCutout());
                controls.setPadding(cut.left, cut.top, cut.right, cut.bottom);
                return insets;
            });
        }
        if (speedButton != null) speedButton.setText(speedLabel());
        updateScaleLabel();
        enterImmersive();
        loadEpisode();
        handler.post(progressTask);
    }

    private void buildOverlay() {
        controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(controls);

        controls.addView(buildTopBar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(this, 64)));

        FrameLayout middle = new FrameLayout(this);
        controls.addView(middle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        buffering = new ProgressBar(this);
        buffering.setIndeterminate(true);
        buffering.setVisibility(View.GONE);
        FrameLayout.LayoutParams bufferParams = new FrameLayout.LayoutParams(
                Theme.dp(this, 30), Theme.dp(this, 30),
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        bufferParams.topMargin = Theme.dp(this, 118);
        root.addView(buffering, bufferParams);

        playPause = new ImageView(this);
        playPause.setImageResource(R.drawable.ic_play);
        playPause.setContentDescription("暂停");
        playPause.setColorFilter(Color.WHITE);
        playPause.setBackground(Theme.oval(0x80000000));
        playPause.setPadding(Theme.dp(this, 14), Theme.dp(this, 14),
                Theme.dp(this, 14), Theme.dp(this, 14));
        playPause.setOnClickListener(v -> togglePlay());
        FrameLayout.LayoutParams playParams = new FrameLayout.LayoutParams(
                Theme.dp(this, 58), Theme.dp(this, 58), Gravity.CENTER);
        middle.addView(playPause, playParams);

        controls.addView(buildBottomBar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        loadingBox = new LinearLayout(this);
        loadingBox.setOrientation(LinearLayout.VERTICAL);
        loadingBox.setGravity(Gravity.CENTER);
        loadingBox.setBackgroundColor(0x66000000);
        ProgressBar loading = new ProgressBar(this);
        loading.setIndeterminate(true);
        loadingBox.addView(loading);
        loadingText = Ui.text(this, "准备播放…", 13, Color.WHITE);
        loadingText.setGravity(Gravity.CENTER);
        loadingText.setPadding(0, Theme.dp(this, 10), 0, 0);
        loadingBox.addView(loadingText);
        root.addView(loadingBox, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        centerStatus = Ui.text(this, "", 13, Color.WHITE);
        centerStatus.setGravity(Gravity.CENTER);
        centerStatus.setPadding(Theme.dp(this, 36), Theme.dp(this, 24),
                Theme.dp(this, 36), Theme.dp(this, 24));
        centerStatus.setVisibility(View.GONE);
        centerStatus.setOnClickListener(v -> {
            if (showingError) {
                showingError = false;
                loadRetryVid = "";
                loadRetryCount = 0;
                centerStatus.setVisibility(View.GONE);
                loadEpisode();
            }
        });
        root.addView(centerStatus, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));

        downloadBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        downloadBar.setMax(100);
        downloadBar.setVisibility(View.GONE);
        FrameLayout.LayoutParams barParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(this, 2), Gravity.TOP);
        root.addView(downloadBar, barParams);

        gestureHint = Ui.medium(this, "", 13, Color.WHITE);
        gestureHint.setGravity(Gravity.CENTER);
        gestureHint.setBackground(Theme.round(0x99000000, Theme.dp(this, 12)));
        gestureHint.setPadding(Theme.dp(this, 16), Theme.dp(this, 8),
                Theme.dp(this, 16), Theme.dp(this, 8));
        gestureHint.setVisibility(View.GONE);
        FrameLayout.LayoutParams hintParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        hintParams.topMargin = Theme.dp(this, 110);
        root.addView(gestureHint, hintParams);

        autoNextTip = Ui.medium(this, "", 12, Color.WHITE);
        autoNextTip.setGravity(Gravity.CENTER);
        autoNextTip.setBackground(Theme.round(0xCC1C1C1E, Theme.dp(this, 18)));
        autoNextTip.setPadding(Theme.dp(this, 16), Theme.dp(this, 9),
                Theme.dp(this, 16), Theme.dp(this, 9));
        autoNextTip.setVisibility(View.GONE);
        autoNextTip.setOnClickListener(v -> cancelAutoNext());
        FrameLayout.LayoutParams nextParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        nextParams.bottomMargin = Theme.dp(this, 120);
        root.addView(autoNextTip, nextParams);

        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent event) {
                if (showingError) return false;
                toggleControls();
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent event) {
                if (!showingError) togglePlay();
                return true;
            }
        });

        showControls();
    }

    private View buildTopBar() {
        FrameLayout bar = new FrameLayout(this);
        bar.setBackgroundResource(R.drawable.bg_scrim_top);
        LinearLayout row = Ui.row(this);
        row.setPadding(Theme.dp(this, 10), Theme.dp(this, 8),
                Theme.dp(this, 12), Theme.dp(this, 8));
        ImageView back = new ImageView(this);
        back.setImageResource(R.drawable.ic_back);
        back.setContentDescription("返回");
        back.setColorFilter(Color.WHITE);
        back.setBackground(Theme.oval(0x66000000));
        back.setPadding(Theme.dp(this, 7), Theme.dp(this, 7),
                Theme.dp(this, 7), Theme.dp(this, 7));
        back.setOnClickListener(v -> finish());
        row.addView(back, new LinearLayout.LayoutParams(
                Theme.dp(this, 44), Theme.dp(this, 44)));

        LinearLayout names = Ui.column(this);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        nameParams.leftMargin = Theme.dp(this, 10);
        row.addView(names, nameParams);
        TextView name = Ui.medium(this, title == null ? "" : title, 14, Color.WHITE);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        names.addView(name);
        episodeLabel = Ui.text(this, "", 10.5f, 0xCCFFFFFF);
        names.addView(episodeLabel);

        favoriteIcon = new ImageView(this);
        favoriteIcon.setImageResource(R.drawable.ic_heart);
        favoriteIcon.setColorFilter(favorite ? Theme.BRAND : Color.WHITE);
        favoriteIcon.setPadding(Theme.dp(this, 8), Theme.dp(this, 8),
                Theme.dp(this, 8), Theme.dp(this, 8));
        favoriteIcon.setOnClickListener(v -> toggleFavorite());
        row.addView(favoriteIcon, new LinearLayout.LayoutParams(
                Theme.dp(this, 44), Theme.dp(this, 44)));
        bar.addView(row);
        return bar;
    }

    private View buildBottomBar() {
        FrameLayout wrap = new FrameLayout(this);
        wrap.setBackgroundResource(R.drawable.bg_scrim_bottom);
        LinearLayout column = Ui.column(this);
        column.setPadding(Theme.dp(this, 14), Theme.dp(this, 18),
                Theme.dp(this, 14), Theme.dp(this, 12));
        wrap.addView(column);

        seekBar = new SeekBar(this);
        seekBar.setMax(1000);
        seekBar.setProgressDrawable(getDrawable(R.drawable.seekbar_progress));
        seekBar.setThumb(getDrawable(R.drawable.seekbar_thumb));
        seekBar.setSplitTrack(false);
        seekBar.setPadding(0, 0, 0, 0);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser && player != null) {
                    seekPreview = player.getDuration() * progress / 1000L;
                    positionView.setText(formatTime(seekPreview));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
                draggingSeek = true;
                handler.removeCallbacks(hideControlsTask);
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                draggingSeek = false;
                if (player != null) player.seekTo((int) seekPreview);
                scheduleHide();
            }
        });
        column.addView(seekBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(this, 26)));

        LinearLayout times = Ui.row(this);
        positionView = Ui.text(this, "00:00", 10, 0xCCFFFFFF);
        times.addView(positionView);
        View spacer = new View(this);
        times.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        durationView = Ui.text(this, "00:00", 10, 0xCCFFFFFF);
        times.addView(durationView);
        column.addView(times);

        LinearLayout actions = Ui.row(this);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionParams.topMargin = Theme.dp(this, 6);
        column.addView(actions, actionParams);

        addAction(actions, R.drawable.ic_list, "选集", v -> showEpisodes());
        qualityButton = addAction(actions, 0, "480P", v -> showQuality());
        speedButton = addAction(actions, 0, "1.0x", v -> showSpeed());
        scaleButton = addAction(actions, 0, "铺满", v -> toggleScaleMode());
        nextButton = addAction(actions, R.drawable.ic_next, "下一集", v -> playNext());
        fullscreenButton = addAction(actions, R.drawable.ic_fullscreen, "全屏",
                v -> toggleOrientation());
        return wrap;
    }

    private TextView addAction(LinearLayout parent, int iconRes, String label,
                               View.OnClickListener click) {
        LinearLayout item = Ui.column(this);
        item.setGravity(Gravity.CENTER);
        item.setPadding(Theme.dp(this, 4), Theme.dp(this, 6),
                Theme.dp(this, 4), Theme.dp(this, 6));
        if (iconRes != 0) {
            item.addView(Ui.icon(this, iconRes, 18, Color.WHITE));
        }
        TextView text = Ui.medium(this, label, 11, Color.WHITE);
        text.setGravity(Gravity.CENTER);
        if (iconRes != 0) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.topMargin = Theme.dp(this, 2);
            item.addView(text, params);
        } else {
            text.setPadding(0, Theme.dp(this, 5), 0, Theme.dp(this, 5));
            item.addView(text);
        }
        item.setOnClickListener(click);
        parent.addView(item, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return text;
    }

    private boolean onGestureTouch(View view, MotionEvent event) {
        if (gestureDetector != null) gestureDetector.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                gestureMode = 0;
                verticalEpisodeDirection = 0;
                verticalHandled = false;
                seekPreview = player == null ? 0 : player.getCurrentPosition();
                return true;
            case MotionEvent.ACTION_MOVE: {
                if (player == null) return true;
                float dx = event.getX() - downX;
                float dy = event.getY() - downY;
                if (gestureMode == 0 && (Math.abs(dx) > Theme.dp(this, 12)
                        || Math.abs(dy) > Theme.dp(this, 12))) {
                    gestureMode = Math.abs(dx) > Math.abs(dy) ? 1 : 2;
                }
                if (gestureMode == 1) {
                    long duration = Math.max(player.getDuration(), 1);
                    long delta = (long) (dx / Math.max(getResources().getDisplayMetrics().widthPixels, 1)
                            * duration * 0.9);
                    seekPreview = Math.max(0, Math.min(duration, seekPreview + delta));
                    downX = event.getX();
                    showGestureHint(formatTime(seekPreview) + " / " + formatTime(duration));
                    if (seekBar != null) {
                        seekBar.setProgress((int) (seekPreview * 1000 / duration));
                    }
                } else if (gestureMode == 2 && !verticalHandled) {
                    float threshold = getResources().getDisplayMetrics().heightPixels * 0.10f;
                    if (Math.abs(dy) >= threshold) {
                        verticalHandled = true;
                        if (dy < 0 && hasNext()) {
                            verticalEpisodeDirection = 1;
                            showGestureHint("松手播放下一集");
                        } else if (dy > 0 && hasPrevious()) {
                            verticalEpisodeDirection = -1;
                            showGestureHint("松手播放上一集");
                        } else {
                            verticalEpisodeDirection = 0;
                            showGestureHint(dy < 0 ? "已经是最后一集" : "已经是第一集");
                        }
                    }
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (gestureMode == 1 && player != null) {
                    player.seekTo((int) seekPreview);
                } else if (gestureMode == 2 && verticalEpisodeDirection != 0) {
                    int direction = verticalEpisodeDirection;
                    verticalEpisodeDirection = 0;
                    gestureMode = 0;
                    gestureHint.setVisibility(View.GONE);
                    switchEpisode(index + direction);
                    return true;
                }
                verticalEpisodeDirection = 0;
                gestureMode = 0;
                handler.postDelayed(() -> gestureHint.setVisibility(View.GONE), 700);
                return true;
            default:
                return false;
        }
    }

    private void showGestureHint(String text) {
        gestureHint.setText(text);
        gestureHint.setVisibility(View.VISIBLE);
    }

    private void refreshTracks() {
        final String currentVid = vid;
        App.io(() -> {
            try {
                List<HongguoApi.TrackInfo> available =
                        new HongguoApi(this).play(seriesId, currentVid);
                if (!available.isEmpty()) {
                    App.ui(() -> {
                        if (!destroyed && currentVid.equals(vid)) tracks = available;
                    });
                }
            } catch (Throwable ignored) {
            }
        });
    }

    private HongguoApi.TrackInfo choosePrefetchTrack(List<HongguoApi.TrackInfo> available,
                                                     String preferred) {
        List<HongguoApi.TrackInfo> usable = new ArrayList<>();
        for (HongguoApi.TrackInfo track : available) {
            if (isSupportedCodec(track)
                    && !failedQualities.contains(track.definition.toLowerCase(Locale.US))) {
                usable.add(track);
            }
        }
        if (usable.isEmpty()) return null;
        for (HongguoApi.TrackInfo track : usable) {
            if (track.definition.equalsIgnoreCase(preferred)) return track;
        }
        for (String fallback : new String[]{"720p", "1080p", "480p", "360p"}) {
            for (HongguoApi.TrackInfo track : usable) {
                if (fallback.equals(track.definition)) return track;
            }
        }
        return usable.get(0);
    }

    private void downloadSilent(String url, File target) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(60000);
        connection.setRequestProperty("User-Agent", Http.UA);
        try (InputStream input = connection.getInputStream();
             FileOutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[65536];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                output.write(buffer, 0, count);
            }
        } finally {
            connection.disconnect();
        }
    }

    private void loadEpisode() {
        if (vid == null || vid.isEmpty()) {
            showError("缺少视频 ID");
            return;
        }
        ensureEpisodes();
        refreshEpisodeHeader();
        if (autoAdvancing && prefetching.contains(vid)) {
            long now = System.currentTimeMillis();
            if (prefetchWaitSince == 0) prefetchWaitSince = now;
            if (now - prefetchWaitSince < 12_000) {
                handler.postDelayed(this::loadEpisode, 180);
                return;
            }
            prefetchWaitSince = 0;
        } else if (!autoAdvancing) {
            prefetchWaitSince = 0;
        }
        prepared = false;
        showingError = false;
        File cached = findCached();
        if (cached != null) {
            if (autoAdvancing) seamlessTransition = true;
            if (!seamlessTransition) showLoading("准备播放…");
            playLocal(cached, qualityFromFile(cached.getName()));
            refreshTracks();
            return;
        }
        showLoading("准备播放…");
        App.io(() -> {
            try {
                List<HongguoApi.TrackInfo> fetched = new HongguoApi(this).play(seriesId, vid);
                if (fetched.isEmpty()) throw new IllegalStateException("没有可用轨道");
                final List<HongguoApi.TrackInfo> available = fetched;
                HongguoApi.TrackInfo track = chooseTrack(available);
                if (track == null) {
                    throw new IllegalStateException("当前剧集编码不受设备支持");
                }
                App.ui(() -> {
                    tracks = available;
                    updateQualityLabel(track.definition);
                });
                File target = MediaCache.playable(this, vid, track.definition);
                if (target.isFile() && target.length() > 100_000
                        && !CencMp4.isPlayable(target)) {
                    //noinspection ResultOfMethodCallIgnored
                    target.delete();
                }
                if (!(target.isFile() && target.length() > 100_000)) {
                    downloadAndDecrypt(track, target);
                }
                App.ui(() -> playLocal(target, track.definition));
            } catch (Throwable error) {
                App.ui(() -> {
                    if (loadRetryCount < 2) {
                        loadRetryCount++;
                        showLoading("正在重试…");
                        handler.postDelayed(this::loadEpisode, 350L * loadRetryCount);
                    } else {
                        loadRetryCount = 0;
                        showError("加载失败：" + message(error) + "\n点击重试");
                    }
                });
            }
        });
    }

    private void refreshEpisodeHeader() {
        if (episodeLabel == null) return;
        episodeLabel.setText(episodeVids.length() > 0
                ? "第 " + (index + 1) + " 集 / 共 " + episodeVids.length() + " 集"
                : "第 " + (index + 1) + " 集");
        nextButton.setAlpha(hasNext() ? 1f : 0.35f);
    }

    private void ensureEpisodes() {
        if (episodeVids.length() > 0 || episodesLoading
                || seriesId == null || seriesId.isEmpty()) return;
        episodesLoading = true;
        App.io(() -> {
            try {
                DetailInfo detail = new HongguoApi(this).detailInfo(seriesId, title, cover, "");
                JSONArray ids = new JSONArray();
                for (Episode episode : detail.episodes) {
                    if (episode.vid != null && !episode.vid.isEmpty()) ids.put(episode.vid);
                }
                App.ui(() -> {
                    episodesLoading = false;
                    if (destroyed || ids.length() == 0) return;
                    episodeVids = ids;
                    if (vid != null) {
                        for (int i = 0; i < ids.length(); i++) {
                            if (vid.equals(ids.optString(i))) {
                                index = i;
                                break;
                            }
                        }
                    }
                    if (index < 0) index = 0;
                    if (index >= ids.length()) index = ids.length() - 1;
                    refreshEpisodeHeader();
                    saveHistory();
                    prefetchNext();
                });
            } catch (Throwable error) {
                App.ui(() -> episodesLoading = false);
            }
        });
    }

    private void prefetchNext() {
        if (!AppPrefs.autoPreload(this) || !hasNext() || episodeVids == null) return;
        String wanted = currentDefinition == null ? "" : currentDefinition.toLowerCase(Locale.US);
        if (wanted.isEmpty() || "缓存".equals(wanted)) wanted = requestedQuality;
        final String preferred = wanted;
        final List<String> queue = new ArrayList<>();
        for (int delta = 1; delta <= 3; delta++) {
            int position = index + delta;
            if (position >= episodeVids.length()) break;
            String nextVid = episodeVids.optString(position);
            if (nextVid == null || nextVid.isEmpty() || nextVid.equals(vid)) continue;
            if (findCachedFor(nextVid) != null) continue;
            if (!prefetching.add(nextVid)) continue;
            queue.add(nextVid);
        }
        if (queue.isEmpty()) return;
        App.io(() -> {
            for (String nextVid : queue) {
                if (destroyed) break;
                try {
                    prefetchOne(nextVid, preferred);
                } catch (Throwable ignored) {
                } finally {
                    prefetching.remove(nextVid);
                }
            }
        });
    }

    private void prefetchOne(String nextVid, String preferred) {
        File encrypted = null;
        try {
            List<HongguoApi.TrackInfo> available =
                    new HongguoApi(this).play(seriesId, nextVid);
            HongguoApi.TrackInfo track = choosePrefetchTrack(available, preferred);
            if (track == null) return;
            synchronized (mediaLock) {
                if (findCachedFor(nextVid) != null) return;
                File target = MediaCache.playable(this, nextVid, track.definition);
                if (target.isFile() && target.length() > 100_000) return;
                encrypted = MediaCache.encrypted(this, nextVid, track.definition);
                downloadSilent(track.url, encrypted);
                if (track.encrypted && track.spadeA != null && !track.spadeA.isEmpty()) {
                    File plain = MediaCache.decrypted(this, nextVid, track.definition);
                    String key = Spade.spadeToKey(track.spadeA);
                    CencMp4.Stats stats = CencMp4.decryptFile(encrypted, plain, key);
                    if (stats.videoSamples > 0 && stats.videoNalOk != stats.videoSamples) {
                        //noinspection ResultOfMethodCallIgnored
                        plain.delete();
                        return;
                    }
                    if (!plain.renameTo(target)) {
                        //noinspection ResultOfMethodCallIgnored
                        plain.delete();
                        return;
                    }
                    //noinspection ResultOfMethodCallIgnored
                    encrypted.delete();
                } else {
                    if (!encrypted.renameTo(target)) {
                        //noinspection ResultOfMethodCallIgnored
                        encrypted.delete();
                        return;
                    }
                }
                if (!CencMp4.isPlayable(target)) {
                    //noinspection ResultOfMethodCallIgnored
                    target.delete();
                }
            }
        } catch (Throwable ignored) {
        } finally {
            if (encrypted != null) {
                //noinspection ResultOfMethodCallIgnored
                encrypted.delete();
            }
        }
    }

    private HongguoApi.TrackInfo chooseTrack(List<HongguoApi.TrackInfo> available) {
        List<HongguoApi.TrackInfo> usable = new ArrayList<>();
        for (HongguoApi.TrackInfo track : available) {
            if (isSupportedCodec(track)
                    && !failedQualities.contains(track.definition.toLowerCase(Locale.US))) {
                usable.add(track);
            }
        }
        if (usable.isEmpty()) return null;
        String wanted = requestedQuality;
        if (wanted == null || wanted.isEmpty() || "auto".equals(wanted)) wanted = "720p";
        for (HongguoApi.TrackInfo track : usable) {
            if (wanted.equals(track.definition)) return track;
        }
        for (String fallback : new String[]{"720p", "1080p", "480p", "360p"}) {
            for (HongguoApi.TrackInfo track : usable) {
                if (fallback.equals(track.definition)) return track;
            }
        }
        return usable.get(0);
    }

    private static boolean isSupportedCodec(HongguoApi.TrackInfo track) {
        String codec = track.codec == null ? "" : track.codec.toLowerCase(Locale.US);
        return !(codec.contains("bytevc2") || codec.contains("bvc2")
                || codec.contains("vvc") || codec.contains("h266"));
    }

    private File findCached() {
        return findCachedFor(vid);
    }

    private File findCachedFor(String targetVid) {
        if (targetVid == null || targetVid.isEmpty()) return null;
        String preferred = requestedQuality;
        if (preferred != null && !preferred.isEmpty() && !"auto".equals(preferred)
                && !failedQualities.contains(preferred.toLowerCase(Locale.US))) {
            File exact = MediaCache.playable(this, targetVid, preferred);
            if (usableCache(exact)) return exact;
        }
        for (String quality : new String[]{"720p", "1080p", "480p", "360p"}) {
            if (failedQualities.contains(quality)) continue;
            File file = MediaCache.playable(this, targetVid, quality);
            if (usableCache(file)) return file;
        }
        File legacy = MediaCache.legacyPlayable(this, targetVid);
        if (usableCache(legacy)) return legacy;
        // Track definitions may be returned as 1080P instead of 1080p; scan
        // by vid prefix so prefetched files are found on case-sensitive FS.
        File[] files = MediaCache.dir(this).listFiles();
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                if (name.startsWith(targetVid + "_") && name.endsWith(".mp4")
                        && usableCache(file)) {
                    return file;
                }
            }
        }
        return null;
    }

    private boolean usableCache(File file) {
        if (!file.isFile() || file.length() <= 100_000) return false;
        String codec = CencMp4.videoCodec(file);
        if (codec.isEmpty() || CencMp4.isPlayable(file)) return true;
        //noinspection ResultOfMethodCallIgnored
        file.delete();
        OfflineStore.remove(this, vid, qualityFromFile(file.getName()).toLowerCase(Locale.US));
        return false;
    }

    private String qualityFromFile(String name) {
        int start = name.lastIndexOf('_');
        int end = name.lastIndexOf('.');
        if (start < 0 || end <= start) return "缓存";
        return name.substring(start + 1, end).toUpperCase(Locale.US);
    }

    private void downloadAndDecrypt(HongguoApi.TrackInfo track, File target) throws Exception {
        synchronized (mediaLock) {
            if (target.isFile() && target.length() > 100_000 && CencMp4.isPlayable(target)) {
                return;
            }
            File encrypted = MediaCache.encrypted(this, vid, track.definition);
            App.ui(() -> showLoading("下载 " + track.definition + "…"));
            download(track.url, encrypted);
            if (track.encrypted && track.spadeA != null && !track.spadeA.isEmpty()) {
                App.ui(() -> showLoading("解密中…"));
                File plain = MediaCache.decrypted(this, vid, track.definition);
                String key = Spade.spadeToKey(track.spadeA);
                CencMp4.Stats stats = CencMp4.decryptFile(encrypted, plain, key);
                if (stats.videoSamples > 0 && stats.videoNalOk != stats.videoSamples) {
                    //noinspection ResultOfMethodCallIgnored
                    plain.delete();
                    throw new IllegalStateException("解密校验失败");
                }
                if (!plain.renameTo(target)) throw new IllegalStateException("无法保存视频");
                //noinspection ResultOfMethodCallIgnored
                encrypted.delete();
            } else {
                if (!encrypted.renameTo(target)) throw new IllegalStateException("无法保存视频");
            }
            if (!CencMp4.isPlayable(target)) {
                //noinspection ResultOfMethodCallIgnored
                target.delete();
                throw new IllegalStateException("该清晰度编码不受设备支持");
            }
            OfflineStore.add(this, seriesId, title, cover, vid, index,
                    track.definition, target.length());
        }
    }

    private void download(String url, File target) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(60000);
        connection.setRequestProperty("User-Agent", Http.UA);
        int total = connection.getContentLength();
        try (InputStream input = connection.getInputStream();
             FileOutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[65536];
            long written = 0;
            int count;
            while ((count = input.read(buffer)) >= 0) {
                output.write(buffer, 0, count);
                written += count;
                if (total > 0) {
                    int percent = (int) (written * 100 / total);
                    App.ui(() -> {
                        downloadBar.setVisibility(View.VISIBLE);
                        downloadBar.setProgress(percent);
                        showLoading("下载 " + percent + "%");
                    });
                }
            }
        } finally {
            connection.disconnect();
        }
        App.ui(() -> downloadBar.setVisibility(View.GONE));
    }

    private void playLocal(File file, String quality) {
        if (destroyed) return;
        playLocalInternal(file, quality);
    }

    private void playLocalInternal(File file, String quality) {
        runOnUiThread(() -> {
            if (destroyed) return;
            updateQualityLabel(quality);
            loadRetryVid = "";
            loadRetryCount = 0;
            autoAdvancing = false;
            prefetchWaitSince = 0;
            if (!seamlessTransition) showLoading("开始播放…");
            saveHistory();
            if (seriesId != null && !seriesId.isEmpty() && vid != null && !vid.isEmpty()
                    && file.getParentFile() != null
                    && file.getParentFile().equals(MediaCache.dir(this))) {
                String normalized = quality == null ? "缓存" : quality.toLowerCase(Locale.US);
                OfflineStore.add(this, seriesId, title, cover, vid, index, normalized, file.length());
            }
            startPlayback(file, quality);
            seamlessTransition = false;
        });
    }

    private void startPlayback(File file, String quality) {
        releasePlayer();
        try {
            player = new ExoPlayer.Builder(this).build();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(), true);
            player.setHandleAudioBecomingNoisy(true);
            player.addListener(playerListener);
            playerView.setPlayer(player);
            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)));
            player.setPlaybackSpeed(speed);
            prepared = false;
            player.prepare();
            player.play();
        } catch (Throwable error) {
            showError("播放失败：" + message(error));
            releasePlayer();
        }
    }

    private void releasePlayer() {
        if (player != null) {
            try {
                player.removeListener(playerListener);
                player.release();
            } catch (Throwable ignored) {
            }
            player = null;
        }
        if (playerView != null) playerView.setPlayer(null);
        prepared = false;
    }

    private final Player.Listener playerListener = new Player.Listener() {
        @Override
        public void onPlaybackStateChanged(int state) {
            if (destroyed) return;
            if (state == Player.STATE_BUFFERING) {
                if (buffering != null) buffering.setVisibility(View.VISIBLE);
                return;
            }
            if (buffering != null) buffering.setVisibility(View.GONE);
            if (state == Player.STATE_READY && !prepared) {
                onPlayerReady();
            } else if (state == Player.STATE_ENDED) {
                onCompleted();
            }
        }

        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            if (destroyed) return;
            playPause.setImageResource(isPlaying
                    ? R.drawable.ic_pause : R.drawable.ic_play);
            playPause.setContentDescription(isPlaying ? "暂停" : "播放");
            if (!isPlaying && !showingError && !autoAdvancing
                    && !suppressControlsOnReady) showControls();
        }

        @Override
        public void onPlayerError(PlaybackException error) {
            handlePlaybackError(error);
        }

        @Override
        public void onVideoSizeChanged(VideoSize videoSize) {
            videoWidth = videoSize.width;
            videoHeight = videoSize.height;
            applyVideoTransform();
        }
    };

    private void applyVideoTransform() {
        if (playerView == null || videoWidth <= 0 || videoHeight <= 0) return;
        int viewWidth = playerView.getWidth();
        int viewHeight = playerView.getHeight();
        if (viewWidth <= 0 || viewHeight <= 0) {
            playerView.post(this::applyVideoTransform);
            return;
        }
        double videoAspect = videoWidth / (double) videoHeight;
        double viewAspect = viewWidth / (double) viewHeight;
        if (manualScale) {
            effectiveFill = fillMode;
        } else if (landscape) {
            // Widescreen clips often have burned-in subtitles near the bottom.
            // In landscape keep the full frame (letterbox) unless the user taps 铺满.
            effectiveFill = false;
        } else {
            double diff = Math.abs(videoAspect - viewAspect)
                    / Math.max(videoAspect, viewAspect);
            effectiveFill = diff < 0.25;
        }
        playerView.setResizeMode(effectiveFill
                ? AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                : AspectRatioFrameLayout.RESIZE_MODE_FIT);
        updateScaleLabel();
    }

    private void onPlayerReady() {
        if (destroyed || player == null) return;
        prepared = true;
        userPaused = false;
        LibraryStore.markWatched(this, seriesId, index);
        prefetchNext();
        loadingBox.setVisibility(View.GONE);
        downloadBar.setVisibility(View.GONE);
        durationView.setText(formatTime(player.getDuration()));
        applySpeed();
        if (pendingResumeMs > 3000 && pendingResumeMs < player.getDuration() - 3000) {
            player.seekTo(pendingResumeMs);
        }
        player.play();
        if (suppressControlsOnReady) {
            suppressControlsOnReady = false;
            hideControlsNow();
        } else {
            showControls();
        }
        scheduleHide();
        handler.removeCallbacks(progressTask);
        handler.post(progressTask);
    }

    private void onCompleted() {
        if (destroyed) return;
        playPause.setImageResource(R.drawable.ic_play);
        playPause.setContentDescription("播放");
        if (hasNext() && AppPrefs.autoNext(this)) {
            autoAdvancing = true;
            hideControlsNow();
            startAutoNext();
        } else {
            showControls();
            showCenterTip(hasNext() ? "已播完，可点下方“下一集”" : "已播完");
        }
    }

    private void hideControlsNow() {
        controlsVisible = false;
        controls.animate().alpha(0f).setDuration(120)
                .withEndAction(() -> {
                    if (!controlsVisible) controls.setVisibility(View.GONE);
                }).start();
    }

    private void startAutoNext() {
        cancelAutoNext();
        autoNextTip.setVisibility(View.GONE);
        playNext();
    }

    private void cancelAutoNext() {
        autoNextTip.setVisibility(View.GONE);
        if (autoNextTask != null) {
            handler.removeCallbacks(autoNextTask);
            autoNextTask = null;
        }
    }

    private void showCenterTip(String text) {
        centerStatus.setText(text);
        centerStatus.setVisibility(View.VISIBLE);
        handler.postDelayed(() -> centerStatus.setVisibility(View.GONE), 1800);
    }

    private void togglePlay() {
        if (showingError) return;
        if (player == null) return;
        playPause.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
        if (player.isPlaying()) {
            player.pause();
            userPaused = true;
            resumeAfterPause = false;
            playPause.setImageResource(R.drawable.ic_play);
            playPause.setContentDescription("播放");
            showControls();
            handler.removeCallbacks(hideControlsTask);
            savePosition();
        } else {
            player.play();
            userPaused = false;
            resumeAfterPause = false;
            applySpeed();
            playPause.setImageResource(R.drawable.ic_pause);
            playPause.setContentDescription("暂停");
            scheduleHide();
        }
    }

    private void playNext() {
        cancelAutoNext();
        if (!hasNext()) {
            Ui.toast(this, "已经是最后一集了");
            return;
        }
        switchEpisode(index + 1);
    }

    private void switchEpisode(int target) {
        cancelAutoNext();
        if (target < 0 || target >= episodeVids.length()) return;
        savePosition();
        index = target;
        vid = episodeVids.optString(target);
        pendingResumeMs = AppPrefs.position(this, vid);
        AppPrefs.saveLastEpisode(this, seriesId, vid, index);
        autoNextTip.setVisibility(View.GONE);
        tracks = new ArrayList<>();
        userPaused = false;
        resumeAfterPause = false;
        // Keep the last frame while a cached next episode is prepared; do not
        // tear down the surface and immediately show a spinner.
        seamlessTransition = findCached() != null;
        suppressControlsOnReady = autoAdvancing || seamlessTransition;
        if (player != null) player.pause();
        loadEpisode();
    }

    private boolean hasNext() {
        return episodeVids != null && index + 1 < episodeVids.length();
    }

    private boolean hasPrevious() {
        return index > 0 && episodeVids.length() > 0;
    }

    private void showSpeed() {
        Dialog dialog = new Dialog(this, R.style.Theme_Hongguo_Sheet);
        LinearLayout rootView = Ui.column(this);
        rootView.setBackgroundResource(R.drawable.bg_sheet);
        rootView.setPadding(Theme.dp(this, 18), Theme.dp(this, 16),
                Theme.dp(this, 18), Theme.dp(this, 18));
        rootView.addView(Ui.medium(this, "播放速度", 17, Theme.TEXT));
        FlowLayout options = new FlowLayout(this);
        options.setPadding(0, Theme.dp(this, 12), 0, 0);
        for (float value : SPEEDS) {
            TextView chip = Ui.chip(this, speedText(value),
                    Math.abs(value - speed) < 0.01f);
            chip.setOnClickListener(v -> {
                setSpeed(value);
                dialog.dismiss();
            });
            options.addView(chip);
        }
        rootView.addView(options);
        dialog.setContentView(rootView);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setGravity(Gravity.BOTTOM);
        }
        dialog.show();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void setSpeed(float value) {
        speed = value;
        AppPrefs.setPlaybackSpeed(this, speed);
        applySpeed();
        if (speedButton != null) speedButton.setText(speedLabel());
    }

    private void toggleScaleMode() {
        if (manualScale) {
            fillMode = !fillMode;
        } else {
            manualScale = true;
            fillMode = !effectiveFill;
        }
        if (!landscape) AppPrefs.setFillScreen(this, fillMode);
        applyVideoTransform();
        updateScaleLabel();
        Ui.toast(this, effectiveFill ? "画面铺满" : "画面适应");
    }

    private void updateScaleLabel() {
        if (scaleButton != null) scaleButton.setText(effectiveFill ? "铺满" : "适应");
    }

    private String speedText(float value) {
        if (Math.abs(value - 1f) < 0.01f) return "1.0x";
        return String.format(Locale.US, "%.2f", value).replaceAll("0$", "") + "x";
    }

    private String speedLabel() {
        return speedText(speed);
    }

    private void applySpeed() {
        if (player != null) player.setPlaybackSpeed(speed);
        if (speedButton != null) speedButton.setText(speedLabel());
    }

    private void showQuality() {
        if (tracks.isEmpty()) {
            refreshTracks();
            Ui.toast(this, "清晰度加载中，请稍后再试");
            return;
        }
        Dialog dialog = new Dialog(this, R.style.Theme_Hongguo_Sheet);
        LinearLayout rootView = Ui.column(this);
        rootView.setBackgroundResource(R.drawable.bg_sheet);
        rootView.setPadding(Theme.dp(this, 18), Theme.dp(this, 16),
                Theme.dp(this, 18), Theme.dp(this, 18));
        rootView.addView(Ui.medium(this, "清晰度", 17, Theme.TEXT));
        FlowLayout options = new FlowLayout(this);
        options.setPadding(0, Theme.dp(this, 14), 0, 0);
        List<HongguoApi.TrackInfo> unique = new ArrayList<>();
        Map<String, HongguoApi.TrackInfo> byDefinition = new LinkedHashMap<>();
        for (HongguoApi.TrackInfo track : tracks) {
            if (!isSupportedCodec(track)) continue;
            if (failedQualities.contains(track.definition.toLowerCase(Locale.US))) continue;
            if (!byDefinition.containsKey(track.definition)) {
                byDefinition.put(track.definition, track);
                unique.add(track);
            }
        }
        if (unique.isEmpty()) {
            Ui.toast(this, "当前剧集没有设备支持的清晰度");
            return;
        }
        for (HongguoApi.TrackInfo track : unique) {
            boolean active = track.definition.equalsIgnoreCase(currentDefinition);
            TextView chip = Ui.chip(this, track.definition, active);
            options.addView(chip);
            chip.setOnClickListener(v -> {
                dialog.dismiss();
                switchQuality(track.definition);
            });
        }
        rootView.addView(options);
        TextView hint = Ui.text(this, "切换清晰度会重新加载当前集并恢复进度", 10.5f,
                Theme.TEXT_TERTIARY);
        hint.setPadding(0, Theme.dp(this, 12), 0, 0);
        rootView.addView(hint);
        dialog.setContentView(rootView);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setGravity(Gravity.BOTTOM);
        }
        dialog.show();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void switchQuality(String definition) {
        if (definition == null || definition.isEmpty()) return;
        savePosition();
        pendingResumeMs = player == null ? pendingResumeMs : player.getCurrentPosition();
        requestedQuality = definition;
        AppPrefs.setQuality(this, definition);
        releasePlayer();
        loadEpisode();
    }

    private void showEpisodes() {
        if (episodeVids.length() == 0) {
            Ui.toast(this, "暂无选集信息");
            return;
        }
        Dialog dialog = new Dialog(this, R.style.Theme_Hongguo_Sheet);
        java.util.Set<Integer> seen = LibraryStore.watched(this, seriesId);
        LinearLayout rootView = Ui.column(this);
        rootView.setBackgroundResource(R.drawable.bg_sheet);
        rootView.setPadding(Theme.dp(this, 18), Theme.dp(this, 16),
                Theme.dp(this, 18), Theme.dp(this, 16));
        rootView.addView(Ui.medium(this, "选集", 17, Theme.TEXT));
        final int pageSize = 100;
        final int pageCount = Math.max(1, (episodeVids.length() + pageSize - 1) / pageSize);
        final int[] currentPage = {Math.min(index / pageSize, pageCount - 1)};
        LinearLayout rangeRow = Ui.row(this);
        rangeRow.setPadding(0, Theme.dp(this, 10), 0, 0);
        HorizontalScrollView rangeScroll = new HorizontalScrollView(this);
        rangeScroll.setHorizontalScrollBarEnabled(false);
        rangeScroll.addView(rangeRow);
        LinearLayout.LayoutParams rangeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rangeScroll.setLayoutParams(rangeParams);
        rootView.addView(rangeScroll);
        if (pageCount <= 1) rangeScroll.setVisibility(View.GONE);

        android.widget.GridLayout grid = new android.widget.GridLayout(this);
        grid.setColumnCount(5);
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.addView(grid);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Theme.dp(this, 300));
        scrollParams.topMargin = Theme.dp(this, 8);
        rootView.addView(scroll, scrollParams);

        final TextView[] rangeChips = new TextView[pageCount];
        Runnable renderPage = () -> {
            int start = currentPage[0] * pageSize;
            int end = Math.min(episodeVids.length(), start + pageSize);
            for (int p = 0; p < rangeChips.length; p++) {
                if (rangeChips[p] != null) Ui.selectChip(rangeChips[p], p == currentPage[0]);
            }
            grid.removeAllViews();
            scroll.scrollTo(0, 0);
            for (int i = start; i < end; i++) {
                final int target = i;
                boolean watched = seen.contains(i);
                TextView button = Ui.medium(this,
                        String.valueOf(i + 1) + (watched ? " ✓" : ""), 12,
                        i == index ? Theme.BRAND : watched ? Theme.TEXT_TERTIARY : Theme.TEXT);
                button.setGravity(Gravity.CENTER);
                button.setBackgroundResource(i == index
                        ? R.drawable.bg_chip_selected
                        : watched ? R.drawable.bg_chip_watched : R.drawable.bg_chip_normal);
                android.widget.GridLayout.LayoutParams params =
                        new android.widget.GridLayout.LayoutParams();
                params.width = 0;
                params.height = Theme.dp(this, 40);
                params.columnSpec = android.widget.GridLayout.spec(
                        android.widget.GridLayout.UNDEFINED, 1f);
                params.setMargins(Theme.dp(this, 3), Theme.dp(this, 4),
                        Theme.dp(this, 3), Theme.dp(this, 4));
                grid.addView(button, params);
                button.setOnClickListener(v -> {
                    dialog.dismiss();
                    switchEpisode(target);
                });
            }
        };

        for (int p = 0; p < pageCount; p++) {
            final int page = p;
            int from = p * pageSize + 1;
            int to = Math.min(episodeVids.length(), (p + 1) * pageSize);
            TextView chip = Ui.chip(this, from + "-" + to, p == currentPage[0]);
            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            chipParams.rightMargin = Theme.dp(this, 7);
            rangeRow.addView(chip, chipParams);
            rangeChips[p] = chip;
            chip.setOnClickListener(v -> {
                currentPage[0] = page;
                renderPage.run();
            });
        }
        renderPage.run();
        dialog.setContentView(rootView);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setGravity(Gravity.BOTTOM);
        }
        dialog.show();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void toggleFavorite() {
        favoriteIcon.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
        JSONObject item = new JSONObject();
        try {
            item.put("series_id", seriesId);
            item.put("title", title);
            item.put("cover", cover);
            item.put("vid", vid);
            item.put("index", index);
        } catch (Exception ignored) {
        }
        favorite = LibraryStore.toggleFavorite(this, item);
        favoriteIcon.setColorFilter(favorite ? Theme.BRAND : Color.WHITE);
        Ui.toast(this, favorite ? "已收藏" : "已取消收藏");
    }

    private void updateQualityLabel(String definition) {
        if (definition != null) currentDefinition = definition;
        if (qualityButton != null) {
            String value = definition == null ? "" : definition.toUpperCase(Locale.US);
            qualityButton.setText(value.isEmpty() ? "清晰度" : value);
        }
    }

    private void showLoading(String text) {
        loadingText.setText(text);
        loadingBox.setVisibility(View.VISIBLE);
        centerStatus.setVisibility(View.GONE);
    }

    private boolean handlePlaybackError(PlaybackException error) {
        String key = vid + ":" + currentDefinition;
        if (!key.equals(lastErrorKey)) {
            lastErrorKey = key;
            showLoading("重试播放…");
            releasePlayer();
            handler.postDelayed(this::loadEpisode, 350);
            return true;
        }
        if (tryFallbackQuality()) return true;
        showError("播放失败：" + (error == null ? "未知错误" : error.getErrorCodeName())
                + "\n点击重试");
        return true;
    }

    private boolean tryFallbackQuality() {
        String failed = currentDefinition == null || currentDefinition.isEmpty()
                ? requestedQuality : currentDefinition;
        failed = failed == null ? "" : failed.toLowerCase(Locale.US);
        if (failed.isEmpty() || "缓存".equals(failed)) return false;
        failedQualities.add(failed);
        File failedFile = MediaCache.playable(this, vid, failed);
        if (failedFile.isFile()) {
            //noinspection ResultOfMethodCallIgnored
            failedFile.delete();
            OfflineStore.remove(this, vid, failed);
        }
        if (tracks.isEmpty()) {
            showLoading("查找可用清晰度…");
            App.io(() -> {
                try {
                    List<HongguoApi.TrackInfo> fetched = new HongguoApi(this).play(seriesId, vid);
                    App.ui(() -> {
                        if (destroyed) return;
                        if (fetched.isEmpty()) {
                            showError("播放失败，点击重试");
                            return;
                        }
                        tracks = fetched;
                        tryFallbackQuality();
                    });
                } catch (Throwable error) {
                    App.ui(() -> showError("播放失败：" + message(error)));
                }
            });
            return true;
        }
        int currentRank = qualityRank(failed);
        HongguoApi.TrackInfo best = null;
        int bestRank = -1;
        for (HongguoApi.TrackInfo track : tracks) {
            if (!isSupportedCodec(track)) continue;
            String definition = track.definition == null ? ""
                    : track.definition.toLowerCase(Locale.US);
            if (failedQualities.contains(definition)) continue;
            int rank = qualityRank(definition);
            if (rank < currentRank && rank > bestRank) {
                best = track;
                bestRank = rank;
            }
        }
        if (best == null) {
            for (HongguoApi.TrackInfo track : tracks) {
                if (!isSupportedCodec(track)) continue;
                String definition = track.definition == null ? ""
                        : track.definition.toLowerCase(Locale.US);
                if (failedQualities.contains(definition)) continue;
                int rank = qualityRank(definition);
                if (rank > bestRank) {
                    best = track;
                    bestRank = rank;
                }
            }
        }
        if (best == null) {
            showError("该视频编码/清晰度不被设备支持");
            return true;
        }
        requestedQuality = best.definition;
        showLoading("切换 " + best.definition + " 重试…");
        releasePlayer();
        loadEpisode();
        return true;
    }

    private static int qualityRank(String definition) {
        if ("360p".equals(definition)) return 0;
        if ("480p".equals(definition)) return 1;
        if ("540p".equals(definition)) return 2;
        if ("720p".equals(definition)) return 3;
        if ("1080p".equals(definition)) return 4;
        return 5;
    }

    private void showError(String text) {
        loadingBox.setVisibility(View.GONE);
        downloadBar.setVisibility(View.GONE);
        buffering.setVisibility(View.GONE);
        showingError = true;
        centerStatus.setText(text);
        centerStatus.setVisibility(View.VISIBLE);
        showControls();
    }

    private void toggleControls() {
        if (controlsVisible) hideControls();
        else showControls();
    }

    private void showControls() {
        controlsVisible = true;
        controls.animate().alpha(1f).setDuration(160).start();
        controls.setVisibility(View.VISIBLE);
        scheduleHide();
    }

    private void hideControls() {
        if (player != null && !player.isPlaying()) return;
        if (draggingSeek) return;
        controlsVisible = false;
        controls.animate().alpha(0f).setDuration(160)
                .withEndAction(() -> {
                    if (!controlsVisible) controls.setVisibility(View.GONE);
                }).start();
    }

    private void scheduleHide() {
        handler.removeCallbacks(hideControlsTask);
        handler.postDelayed(hideControlsTask, 3800);
    }

    private void refreshProgress() {
        if (player == null || draggingSeek) return;
        long duration = player.getDuration();
        long position = player.getCurrentPosition();
        if (duration > 0) {
            seekBar.setProgress((int) (position * 1000L / duration));
            positionView.setText(formatTime(position));
            durationView.setText(formatTime(duration));
        }
        if (System.currentTimeMillis() - lastSave > 5000) {
            savePosition();
        }
    }

    private void savePosition() {
        if (player != null && vid != null) {
            AppPrefs.savePosition(this, vid, player.getCurrentPosition());
            lastSave = System.currentTimeMillis();
        }
    }

    private void saveHistory() {
        try {
            JSONObject item = new JSONObject();
            item.put("series_id", seriesId == null ? "" : seriesId);
            item.put("title", title == null ? "" : title);
            item.put("cover", cover == null ? "" : cover);
            item.put("vid", vid == null ? "" : vid);
            item.put("index", index);
            item.put("episode_cnt", episodeVids == null ? 0 : episodeVids.length());
            item.put("episode_vids", episodeVids == null ? "[]" : episodeVids.toString());
            LibraryStore.addHistory(this, item);
            AppPrefs.saveLastEpisode(this, seriesId, vid, index);
        } catch (Exception ignored) {
        }
    }

    private void toggleOrientation() {
        boolean targetLandscape = !landscape;
        int position = player != null
                ? (int) Math.max(0, player.getCurrentPosition())
                : (int) Math.max(0, pendingResumeMs);
        savePosition();
        releasePlayer();
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra(EXTRA_SERIES_ID, seriesId);
        intent.putExtra(EXTRA_TITLE, title);
        intent.putExtra(EXTRA_COVER, cover);
        intent.putExtra(EXTRA_VID, vid);
        intent.putExtra(EXTRA_INDEX, index);
        intent.putExtra(EXTRA_EPISODES, episodeVids == null ? "[]" : episodeVids.toString());
        intent.putExtra(EXTRA_LANDSCAPE, targetLandscape);
        intent.putExtra(EXTRA_POSITION, Math.max(0, position));
        startActivity(intent);
        finish();
        overridePendingTransition(0, 0);
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        landscape = newConfig.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        updateScaleLabel();
        if (fullscreenButton != null) fullscreenButton.setText(landscape ? "竖屏" : "全屏");
        handler.postDelayed(this::applyVideoTransform, 100);
        handler.postDelayed(this::enterImmersive, 200);
    }

    private void enterImmersive() {
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false);
            android.view.WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.hide(android.view.WindowInsets.Type.statusBars()
                        | android.view.WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(
                        android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        }
        window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) enterImmersive();
    }

    @Override
    protected void onResume() {
        super.onResume();
        pipClosed = false;
        enterImmersive();
        if (resumeAfterPause && !userPaused && player != null) {
            player.play();
            resumeAfterPause = false;
            playPause.setImageResource(R.drawable.ic_pause);
            playPause.setContentDescription("暂停");
            scheduleHide();
        }
    }

    @Override
    protected void onStop() {
        boolean leavePip = pipActive || pipClosed;
        pipActive = false;
        pipClosed = false;
        if (leavePip) {
            savePosition();
            releasePlayer();
            finishAndRemoveTask();
        } else if (!isInPictureInPictureMode()) {
            savePosition();
            if (player != null && player.isPlaying()) player.pause();
        }
        super.onStop();
    }

    @Override
    public void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (player == null || !player.isPlaying() || userPaused) return;
        if (Build.VERSION.SDK_INT < 26) return;
        try {
            int width = player.getVideoSize().width;
            int height = player.getVideoSize().height;
            Rational ratio = width > 0 && height > 0
                    ? new Rational(width, height) : new Rational(16, 9);
            PictureInPictureParams params = new PictureInPictureParams.Builder()
                    .setAspectRatio(ratio)
                    .build();
            pipRequested = true;
            enterPictureInPictureMode(params);
        } catch (Throwable ignored) {
            pipRequested = false;
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean inPictureInPicture,
                                              Configuration configuration) {
        super.onPictureInPictureModeChanged(inPictureInPicture, configuration);
        if (inPictureInPicture) {
            pipRequested = false;
            pipActive = true;
            pipClosed = false;
            controlsVisible = false;
            if (controls != null) controls.setVisibility(View.GONE);
            if (gestureHint != null) gestureHint.setVisibility(View.GONE);
            if (autoNextTip != null) autoNextTip.setVisibility(View.GONE);
            if (loadingBox != null) loadingBox.setVisibility(View.GONE);
            if (buffering != null) buffering.setVisibility(View.GONE);
        } else if (controls != null) {
            if (pipActive) pipClosed = true;
            pipActive = false;
            controlsVisible = true;
            controls.setVisibility(View.VISIBLE);
            controls.animate().alpha(1f).setDuration(120).start();
            scheduleHide();
        }
    }

    @Override
    protected void onPause() {
        if (!pipRequested && !isInPictureInPictureMode() && player != null && player.isPlaying()) {
            resumeAfterPause = !userPaused;
            savePosition();
            player.pause();
        } else {
            savePosition();
        }
        super.onPause();
    }

    private String formatTime(long millis) {
        long seconds = Math.max(millis, 0) / 1000;
        return String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60);
    }

    private String message(Throwable error) {
        String text = error.getMessage();
        return text == null || text.isEmpty() ? "请稍后再试" : text;
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        handler.removeCallbacksAndMessages(null);
        savePosition();
        try {
            releasePlayer();
        } catch (Throwable ignored) {
        }
        if (initialBrightness >= 0) {
            WindowManager.LayoutParams params = getWindow().getAttributes();
            params.screenBrightness = initialBrightness;
            getWindow().setAttributes(params);
        }
        super.onDestroy();
    }
}
