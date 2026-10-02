from pathlib import Path
root = Path("/tmp/omegas-jvm/stubsrc")
p = root / 'androidx/activity/OnBackPressedCallback.java'
p.parent.mkdir(parents=True, exist_ok=True)
p.write_text('package androidx.activity;\npublic abstract class OnBackPressedCallback {public OnBackPressedCallback(boolean b){} public abstract void handleOnBackPressed();}')
p = root / 'androidx/activity/OnBackPressedDispatcher.java'
p.parent.mkdir(parents=True, exist_ok=True)
p.write_text('package androidx.activity;\npublic class OnBackPressedDispatcher {public void addCallback(Object o,OnBackPressedCallback c){} }')
p = root / 'androidx/activity/result/contract/ActivityResultContracts.java'
p.parent.mkdir(parents=True, exist_ok=True)
p.write_text('package androidx.activity.result.contract;\npublic class ActivityResultContracts {public static class Contract<I,O>{} public static class CreateDocument extends Contract<String,android.net.Uri>{public CreateDocument(String s){}} public static class OpenDocument extends Contract<String[],android.net.Uri>{} public static class RequestPermission extends Contract<String,Boolean>{} public static class RequestMultiplePermissions extends Contract<String[],java.util.Map<String,Boolean>>{} }')
p = root / 'androidx/appcompat/app/AppCompatActivity.java'
p.parent.mkdir(parents=True, exist_ok=True)
p.write_text('package androidx.appcompat.app;\npublic class AppCompatActivity extends android.app.Activity {public androidx.activity.OnBackPressedDispatcher getOnBackPressedDispatcher(){return new androidx.activity.OnBackPressedDispatcher();} public interface Callback<O>{void onActivityResult(O o);} public static class Launcher<I>{public void launch(I i){}} public <I,O> Launcher<I> registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.Contract<I,O> c,Callback<O> cb){return new Launcher<I>();} }')
p = root / 'androidx/core/app/NotificationCompat.java'
p.parent.mkdir(parents=True, exist_ok=True)
p.write_text('package androidx.core.app;\npublic class NotificationCompat { public static final String CATEGORY_SERVICE="service"; public static final int FOREGROUND_SERVICE_IMMEDIATE=1; public static class BigTextStyle {public BigTextStyle bigText(CharSequence s){return this;}} public static class Builder {public Builder(android.content.Context c,String id){} public Builder setSmallIcon(int x){return this;} public Builder setContentTitle(CharSequence x){return this;} public Builder setContentText(CharSequence x){return this;} public Builder setStyle(BigTextStyle x){return this;} public Builder setContentIntent(android.app.PendingIntent x){return this;} public Builder setOngoing(boolean x){return this;} public Builder setOnlyAlertOnce(boolean x){return this;} public Builder setSilent(boolean x){return this;} public Builder setCategory(String x){return this;} public Builder setForegroundServiceBehavior(int x){return this;} public Builder setShowWhen(boolean x){return this;} public Builder addAction(int icon,CharSequence title,android.app.PendingIntent p){return this;} public android.app.Notification build(){return null;}} }')
p = root / 'androidx/core/app/NotificationManagerCompat.java'
p.parent.mkdir(parents=True, exist_ok=True)
p.write_text('package androidx.core.app;\npublic class NotificationManagerCompat { public static NotificationManagerCompat from(android.content.Context c){return new NotificationManagerCompat();} public void notify(int id,android.app.Notification n){} public boolean areNotificationsEnabled(){return true;} }')
p = root / 'androidx/core/app/ServiceCompat.java'
p.parent.mkdir(parents=True, exist_ok=True)
p.write_text('package androidx.core.app;\npublic class ServiceCompat { public static void startForeground(android.app.Service s,int id,android.app.Notification n,int type){} }')
p = root / 'androidx/core/content/ContextCompat.java'
p.parent.mkdir(parents=True, exist_ok=True)
p.write_text('package androidx.core.content;\npublic class ContextCompat { public static final int RECEIVER_NOT_EXPORTED=4; public static int checkSelfPermission(android.content.Context c,String s){return 0;} public static android.content.ComponentName startForegroundService(android.content.Context c,android.content.Intent i){return null;} public static android.content.Intent registerReceiver(android.content.Context c,android.content.BroadcastReceiver r,android.content.IntentFilter f,int flags){return null;} }')
p = root / 'androidx/core/graphics/Insets.java'
p.parent.mkdir(parents=True, exist_ok=True)
p.write_text('package androidx.core.graphics;\npublic class Insets {public int left,top,right,bottom;}')
p = root / 'androidx/core/view/ViewCompat.java'
p.parent.mkdir(parents=True, exist_ok=True)
p.write_text('package androidx.core.view;\npublic class ViewCompat {public interface OnApplyWindowInsetsListener {WindowInsetsCompat onApplyWindowInsets(android.view.View v,WindowInsetsCompat i);} public static void setOnApplyWindowInsetsListener(android.view.View v,OnApplyWindowInsetsListener l){} }')
p = root / 'androidx/core/view/WindowCompat.java'
p.parent.mkdir(parents=True, exist_ok=True)
p.write_text('package androidx.core.view;\npublic class WindowCompat {public static void setDecorFitsSystemWindows(android.view.Window w,boolean b){} }')
p = root / 'androidx/core/view/WindowInsetsCompat.java'
p.parent.mkdir(parents=True, exist_ok=True)
p.write_text('package androidx.core.view;\npublic class WindowInsetsCompat {public androidx.core.graphics.Insets getInsets(int t){return new androidx.core.graphics.Insets();} public static class Type {public static int systemBars(){return 1;} public static int displayCutout(){return 2;} public static int ime(){return 4;}}}')
p = root / 'com/hoho/android/usbserial/driver/UsbSerialDriver.java'
p.parent.mkdir(parents=True, exist_ok=True)
p.write_text('package com.hoho.android.usbserial.driver;\npublic interface UsbSerialDriver {java.util.List<UsbSerialPort> getPorts(); android.hardware.usb.UsbDevice getDevice();}')
p = root / 'com/hoho/android/usbserial/driver/UsbSerialPort.java'
p.parent.mkdir(parents=True, exist_ok=True)
p.write_text('package com.hoho.android.usbserial.driver;\npublic interface UsbSerialPort {int STOPBITS_1=1,STOPBITS_2=2,PARITY_ODD=1,PARITY_EVEN=2,PARITY_MARK=3,PARITY_SPACE=4,PARITY_NONE=0; void open(android.hardware.usb.UsbDeviceConnection c); void close(); void setParameters(int b,int d,int s,int p); boolean getDTR(); boolean getRTS(); void setDTR(boolean b); void setRTS(boolean b); void write(byte[] b,int t); boolean purgeHwBuffers(boolean w,boolean r); }')
p = root / 'com/hoho/android/usbserial/driver/UsbSerialProber.java'
p.parent.mkdir(parents=True, exist_ok=True)
p.write_text('package com.hoho.android.usbserial.driver;\npublic class UsbSerialProber {public static UsbSerialProber getDefaultProber(){return new UsbSerialProber();} public UsbSerialDriver probeDevice(android.hardware.usb.UsbDevice d){return null;} }')
p = root / 'com/hoho/android/usbserial/util/SerialInputOutputManager.java'
p.parent.mkdir(parents=True, exist_ok=True)
p.write_text('package com.hoho.android.usbserial.util;\npublic class SerialInputOutputManager implements Runnable {public interface Listener {void onNewData(byte[] b);void onRunError(Exception e);} public SerialInputOutputManager(com.hoho.android.usbserial.driver.UsbSerialPort p,Listener l){} public void stop(){} public void run(){} }')
p = root / 'com/omegas/prohub/BuildConfig.java'
p.parent.mkdir(parents=True, exist_ok=True)
p.write_text('package com.omegas.prohub; public class BuildConfig {public static final boolean DEBUG=true,OMEGAS_AUTOMATIC_CALIBRATION=false;public static final int VERSION_CODE=1;public static final String VERSION_NAME="test",APPLICATION_ID="com.omegas.prohub";public static final String OMEGAS_PRODUCT="test";public static final String OMEGAS_APP_LABEL="test";public static final String OMEGAS_GENERATION="test";public static final String OMEGAS_CHANNEL="test";public static final String OMEGAS_ENGINE="test";public static final String OMEGAS_TELEMETRY_SCHEMA="test";public static final String OMEGAS_LEARNING_SCHEMA="test";public static final String OMEGAS_MAP_SCHEMA="test";public static final String OMEGAS_K_FACTOR_SCHEMA="test";public static final String OMEGAS_K_FACTOR_STATE="test";public static final String OMEGAS_SAFETY_MODE="test";public static final String OMEGAS_BUILD_COMMIT="test";}')
p = root / 'com/omegas/prohub/R.java'
p.parent.mkdir(parents=True, exist_ok=True)
p.write_text('package com.omegas.prohub;\npublic class R {public static class layout {public static final int activity_main=1;} public static class id {public static final int rootContainer=1,hubWebView=2;} public static class drawable {public static final int ic_stat_omegas=1;}}')
