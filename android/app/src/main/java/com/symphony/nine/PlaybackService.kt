package com.symphony.nine

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat

class PlaybackService : Service() {
    private lateinit var session: MediaSessionCompat
    private val channelId = "symphony_playback"

    override fun onCreate() {
        super.onCreate()
        instance = this
        createChannel()
        session = MediaSessionCompat(this, "SymphonyNo9").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = MainActivity.sendMediaCommand("play")
                override fun onPause() = MainActivity.sendMediaCommand("pause")
                override fun onSkipToNext() = MainActivity.sendMediaCommand("next")
                override fun onSkipToPrevious() = MainActivity.sendMediaCommand("previous")
                override fun onSeekTo(pos: Long) {
                    MainActivity.sendMediaCommand("seek:$pos")
                }
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Android requires a service started with startForegroundService() to call
        // startForeground() within a few seconds, even before audio begins.
        startForeground(9, notification("Symphony No. 9", "Ready to play", false))
        return START_STICKY
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(channelId, "Music playback", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun notification(title: String, artist: String, playing: Boolean): android.app.Notification {
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        val content = PendingIntent.getActivity(this, 0, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val prev = actionIntent("previous", 1)
        val toggle = actionIntent(if (playing) "pause" else "play", 2)
        val next = actionIntent("next", 3)
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title).setContentText(artist)
            .setContentIntent(content).setOnlyAlertOnce(true).setOngoing(playing)
            .addAction(android.R.drawable.ic_media_previous, "Previous", prev)
            .addAction(if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (playing) "Pause" else "Play", toggle)
            .addAction(android.R.drawable.ic_media_next, "Next", next)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0,1,2))
            .build()
    }

    private fun actionIntent(action: String, id: Int): PendingIntent {
        val i = Intent(this, MediaActionReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(this, id, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    companion object {
        fun update(context: Context, title: String, artist: String, playing: Boolean, duration: Double, position: Double) {
            val svc = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            // MainActivity starts the service; if it is not yet created, start it safely.
            if (instance == null) {
                val intent = Intent(context, PlaybackService::class.java)
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
            }
            instance?.let { service ->
                service.session.setMetadata(MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, (duration * 1000).toLong()).build())
                val state = if (playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
                service.session.setPlaybackState(PlaybackStateCompat.Builder().setActions(
                    PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or PlaybackStateCompat.ACTION_SEEK_TO
                ).setState(state, (position * 1000).toLong(), if (playing) 1f else 0f).build())
                val n = service.notification(title, artist, playing)
                if (playing) service.startForeground(9, n) else {
                    service.stopForeground(STOP_FOREGROUND_DETACH)
                    svc.notify(9, n)
                }
            }
        }
        @Volatile private var instance: PlaybackService? = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { instance = null; session.isActive = false; session.release(); super.onDestroy() }
}
