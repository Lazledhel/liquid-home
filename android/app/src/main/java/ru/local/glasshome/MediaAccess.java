package ru.local.glasshome;
import android.service.notification.NotificationListenerService;
public class MediaAccess extends NotificationListenerService {
 public void onListenerConnected(){Home.mediaPermissionChanged();Island.connected(this);}
 public void onListenerDisconnected(){Island.disconnected();Home.mediaPermissionChanged();}
 public void onNotificationPosted(android.service.notification.StatusBarNotification n){Island.posted(this,n,true);}
 public void onNotificationRemoved(android.service.notification.StatusBarNotification n){Island.removed(n.getKey());}
 public void onDestroy(){Island.disconnected();super.onDestroy();}
}
