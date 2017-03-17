/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.audio;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.media.AudioManager;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import android.net.Uri;
import android.os.Handler;
import android.provider.Settings;

/**
 * Receives broadcast events indicating changes to the device's audio capabilities, notifying a
 * {@link Listener} when audio capability changes occur.
 */
public final class AudioCapabilitiesReceiver {

  /**
   * Listener notified when audio capabilities change.
   */
  public interface Listener {

    /**
     * Called when the audio capabilities change.
     *
     * @param audioCapabilities The current audio capabilities for the device.
     */
    void onAudioCapabilitiesChanged(AudioCapabilities audioCapabilities);

  }

  private final Context context;
  private final Listener listener;
  private final BroadcastReceiver receiver;
  // AMZN_CHANGE_BEGIN
  private final ContentResolver resolver;
  private  SurroundSoundSettingObserver observer;
  // AMZN_CHANGE_END
  /* package */ AudioCapabilities audioCapabilities;

  /**
   * @param context A context for registering the receiver.
   * @param listener The listener to notify when audio capabilities change.
   */
  public AudioCapabilitiesReceiver(Context context, Listener listener) {
    this.context = Assertions.checkNotNull(context);
    this.listener = Assertions.checkNotNull(listener);
    this.receiver = Util.SDK_INT >= 21 ? new HdmiAudioPlugBroadcastReceiver() : null;
    // AMZN_CHANGE_BEGIN
    this.resolver = Util.SDK_INT >= 17 ? context.getContentResolver() : null;
    this.observer = new SurroundSoundSettingObserver(null);
    // AMZN_CHANGE_END
  }

  /**
   * Registers the receiver, meaning it will notify the listener when audio capability changes
   * occur. The current audio capabilities will be returned. It is important to call
   * {@link #unregister} when the receiver is no longer required.
   *
   * @return The current audio capabilities for the device.
   */
  @SuppressWarnings("InlinedApi")
  public AudioCapabilities register() {
    Intent stickyIntent = receiver == null ? null
        : context.registerReceiver(receiver, new IntentFilter(AudioManager.ACTION_HDMI_AUDIO_PLUG));
    // AMZN_CHANGE_BEGIN
    audioCapabilities = AudioCapabilities.getCapabilities(context, stickyIntent);
    Uri surroundSoundUri = Settings.Global.getUriFor(AudioCapabilities.EXTERNAL_SURROUND_SOUND_ENABLED);
    if (resolver != null) {
      resolver.registerContentObserver(surroundSoundUri, true, observer);
    }
    // AMZN_CHANGE_END
    return audioCapabilities;
  }

  /**
   * Unregisters the receiver, meaning it will no longer notify the listener when audio capability
   * changes occur.
   */
  public void unregister() {
    if (receiver != null) {
      context.unregisterReceiver(receiver);
    }
    // AMZN_CHANGE_BEGIN
    if (resolver != null) {
      resolver.unregisterContentObserver(observer);
    }
    // AMZN_CHANGE_END
  }

  private final class HdmiAudioPlugBroadcastReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
      if (!isInitialStickyBroadcast()) {
        AudioCapabilities newAudioCapabilities =
                AudioCapabilities.getCapabilities(context, intent); // AMZN_CHANGE_ONELINE
        if (!newAudioCapabilities.equals(audioCapabilities)) {
          audioCapabilities = newAudioCapabilities;
          listener.onAudioCapabilitiesChanged(newAudioCapabilities);
        }
      }
    }

  }
  // AMZN_CHANGE_BEGIN
  @TargetApi(17)
  private final class SurroundSoundSettingObserver extends ContentObserver {
    public SurroundSoundSettingObserver(Handler handler) {
      super(handler);
    }

    @Override
    public boolean deliverSelfNotifications() {
      return true;
    }

    @Override
    public void onChange(boolean selfChange) {
      super.onChange(selfChange);
      AudioCapabilities newAudioCapabilities;
      int isSurroundSoundEnabled = Settings.Global.getInt(resolver,
              AudioCapabilities.EXTERNAL_SURROUND_SOUND_ENABLED, 0);
      if (isSurroundSoundEnabled == 1) {
        newAudioCapabilities = AudioCapabilities.SURROUND_AUDIO_CAPABILITIES;
      } else {
        // fallback to last known audioCapabilities, hopefully updated by HDMI plugged state intent
        newAudioCapabilities = audioCapabilities;
      }
      listener.onAudioCapabilitiesChanged(newAudioCapabilities);
    }
  }
  // AMZN_CHANGE_END

}
