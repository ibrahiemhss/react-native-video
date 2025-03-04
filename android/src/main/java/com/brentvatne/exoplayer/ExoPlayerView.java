package com.brentvatne.exoplayer;

import android.annotation.TargetApi;
import android.content.Context;
import androidx.core.content.ContextCompat;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.Tracks;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.ui.SubtitleView;
import com.google.android.exoplayer2.video.VideoSize;

import java.util.List;


@TargetApi(16)
public final class ExoPlayerView extends FrameLayout {

    private View surfaceView;
    private final View shutterView;
    private final SubtitleView subtitleLayout;
    private final AspectRatioFrameLayout layout;
    private final ComponentListener componentListener;
    private ExoPlayer player;
    private Context context;
    private ViewGroup.LayoutParams layoutParams;

    private boolean useTextureView = true;
    private boolean useSecureView = false;
    private boolean hideShutterView = false;

    public ExoPlayerView(Context context) {
        this(context, null);
    }

    public ExoPlayerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ExoPlayerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        this.context = context;

        layoutParams = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);

        componentListener = new ComponentListener();

        FrameLayout.LayoutParams aspectRatioParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
        aspectRatioParams.gravity = Gravity.CENTER;
        layout = new AspectRatioFrameLayout(context);
        layout.setLayoutParams(aspectRatioParams);

        shutterView = new View(getContext());
        shutterView.setLayoutParams(layoutParams);
        shutterView.setBackgroundColor(ContextCompat.getColor(context, android.R.color.black));

        subtitleLayout = new SubtitleView(context);
        subtitleLayout.setLayoutParams(layoutParams);
        subtitleLayout.setUserDefaultStyle();
        subtitleLayout.setUserDefaultTextSize();

        updateSurfaceView();

        layout.addView(shutterView, 1, layoutParams);
        layout.addView(subtitleLayout, 2, layoutParams);

        addViewInLayout(layout, 0, aspectRatioParams);
    }

    private void clearVideoView() {
        if (surfaceView instanceof TextureView) {
            player.clearVideoTextureView((TextureView) surfaceView);
        } else if (surfaceView instanceof SurfaceView) {
            player.clearVideoSurfaceView((SurfaceView) surfaceView);
        }
    }

    private void setVideoView() {
        if (surfaceView instanceof TextureView) {
            player.setVideoTextureView((TextureView) surfaceView);
        } else if (surfaceView instanceof SurfaceView) {
            player.setVideoSurfaceView((SurfaceView) surfaceView);
        }
    }

    private void updateSurfaceView() {
        View view;
        if (!useTextureView || useSecureView) {
            view = new SurfaceView(context);
            if (useSecureView) {
                ((SurfaceView)view).setSecure(true);
            }
        } else {
            view = new TextureView(context);
        }
        view.setLayoutParams(layoutParams);

        surfaceView = view;
        if (layout.getChildAt(0) != null) {
            layout.removeViewAt(0);
        }
        layout.addView(surfaceView, 0, layoutParams);

        if (this.player != null) {
            setVideoView();
        }
    }

    private void updateShutterViewVisibility() {
        shutterView.setVisibility(this.hideShutterView ? View.INVISIBLE : View.VISIBLE);
    }

    /**
     * Set the {@link ExoPlayer} to use. The {@link ExoPlayer#addListener} method of the
     * player will be called and previous
     * assignments are overridden.
     *
     * @param player The {@link ExoPlayer} to use.
     */
    public void setPlayer(ExoPlayer player) {
        if (this.player == player) {
            return;
        }
        if (this.player != null) {
            this.player.removeListener(componentListener);
            clearVideoView();
        }
        this.player = player;
        shutterView.setVisibility(this.hideShutterView ? View.INVISIBLE : View.VISIBLE);
        if (player != null) {
            setVideoView();
            player.addListener(componentListener);
        }
    }

    /**
     * Sets the resize mode which can be of value {@link ResizeMode.Mode}
     *
     * @param resizeMode The resize mode.
     */
    public void setResizeMode(@ResizeMode.Mode int resizeMode) {
        if (layout.getResizeMode() != resizeMode) {
            layout.setResizeMode(resizeMode);
            post(measureAndLayout);
        }

    }

    /**
     * Get the view onto which video is rendered. This is either a {@link SurfaceView} (default)
     * or a {@link TextureView} if the {@code use_texture_view} view attribute has been set to true.
     *
     * @return either a {@link SurfaceView} or a {@link TextureView}.
     */
    public View getVideoSurfaceView() {
        return surfaceView;
    }

    public void setUseTextureView(boolean useTextureView) {
        if (useTextureView != this.useTextureView) {
            this.useTextureView = useTextureView;
            updateSurfaceView();
        }
    }

    public void useSecureView(boolean useSecureView) {
        if (useSecureView != this.useSecureView) {
            this.useSecureView = useSecureView;
            updateSurfaceView();
        }
    }

    public void setHideShutterView(boolean hideShutterView) {
        this.hideShutterView = hideShutterView;
        updateShutterViewVisibility();
    }

    private final Runnable measureAndLayout = new Runnable() {
        @Override
        public void run() {
            measure(
                    MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(getHeight(), MeasureSpec.EXACTLY));
            layout(getLeft(), getTop(), getRight(), getBottom());
        }
    };

    private void updateForCurrentTrackSelections(Tracks tracks) {
        if (player == null) {
            return;
        }
        for (Tracks.Group trackGroup : tracks.getGroups()) {
            // Group level information.
            @C.TrackType int trackType = trackGroup.getType();
            boolean trackInGroupIsSelected = trackGroup.isSelected();
            boolean trackInGroupIsSupported = trackGroup.isSupported();
            for (int i = 0; i < trackGroup.length; i++) {
                // Individual track information.
                boolean isSupported = trackGroup.isTrackSupported(i);
                boolean isSelected = trackGroup.isTrackSelected(i);
                Format trackFormat = trackGroup.getTrackFormat(i);
                if (player.getRendererType(i) == C.TRACK_TYPE_VIDEO) {
                    // Video enabled so artwork must be hidden. If the shutter is closed, it will be opened in
                    // onRenderedFirstFrame().
                    return;
                }
            }
        }
        // Video disabled so the shutter must be closed.
        shutterView.setVisibility(this.hideShutterView ? View.INVISIBLE : View.VISIBLE);
    }

    public void invalidateAspectRatio() {
        // Resetting aspect ratio will force layout refresh on next video size changed
        layout.invalidateAspectRatio();
    }

    private final class ComponentListener implements Player.Listener {

        // TextRenderer.Output implementation

        // ExoPlayer.VideoListener implementation

        @Override
        public void onCues(List<Cue> cues) {
            subtitleLayout.setCues(cues);
        }
        @Override
        public void onVideoSizeChanged(VideoSize videoSize) {
            boolean isInitialRatio = layout.getAspectRatio() == 0;
            layout.setAspectRatio(videoSize.height == 0 ? 1 : (videoSize.width * videoSize.pixelWidthHeightRatio) / videoSize.height);

            // React native workaround for measuring and layout on initial load.
            if (isInitialRatio) {
                post(measureAndLayout);
            }
        }

        @Override
        public void onRenderedFirstFrame() {
            shutterView.setVisibility(INVISIBLE);
        }

        // ExoPlayer.EventListener implementation

        @Override
        public void onIsLoadingChanged(boolean isLoading) {
            // Do nothing.
        }

        @Override
        public void onPlaybackStateChanged(int playbackState) {
            // Do nothing.
        }

        @Override
        public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
            // Do nothing.
        }

        @Override
        public void onPlayerError(PlaybackException e) {
            // Do nothing.
        }

        @Override
        public void onPositionDiscontinuity(Player.PositionInfo oldPosition, Player.PositionInfo newPosition, int reason) {
            // Do nothing.
        }

        @Override
        public void onTimelineChanged(Timeline timeline, int reason) {
            // Do nothing.
        }

        @Override
        public void onTracksChanged(Tracks tracks) {
            updateForCurrentTrackSelections(tracks);
        }

        @Override
        public void onPlaybackParametersChanged(PlaybackParameters params) {
            // Do nothing
        }

        @Override
        public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
            // Do nothing.
        }

        @Override
        public void onRepeatModeChanged(int repeatMode) {
            // Do nothing.
        }
    }

}
