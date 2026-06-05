/*
 * Copyright 2020 Google Inc.
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
package com.rawmilk.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

public class Application extends android.app.Application {

  @Override
  public void onCreate() {
      super.onCreate();
      createNotificationChannel();
  }

  private void createNotificationChannel() {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          String channelId = "rawmilk_notifications";
          CharSequence channelName = "Order and Subscription Notifications";
          String description = "Channels for raw milk updates, delivery, and orders";
          int importance = NotificationManager.IMPORTANCE_HIGH;
          Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

          NotificationChannel channel = new NotificationChannel(channelId, channelName, importance);
          channel.setDescription(description);
          channel.enableLights(true);
          channel.enableVibration(true);
          channel.setShowBadge(true);

          AudioAttributes audioAttributes = new AudioAttributes.Builder()
                  .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                  .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                  .build();
          channel.setSound(defaultSoundUri, audioAttributes);

          NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
          if (notificationManager != null) {
              notificationManager.createNotificationChannel(channel);
              Log.d("RawMilkFCM", "[Application] Notification channel created/verified on startup.");
          }
      }
  }
}

