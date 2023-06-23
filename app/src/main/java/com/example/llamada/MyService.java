package com.example.llamada;

import static android.app.Activity.RESULT_OK;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.KeyguardManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.telephony.PhoneStateListener;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;


public class MyService extends Service {

    private Context context;


    public void setActivity(Context context) {
        this.context = context;
    }


    private static final String TAG = "MyService";
    private static final int NOTIFICATION_ID = 1;
    private TelephonyManager telephonyManager;
    private PhoneCallListener phoneCallListener;
    static final int LOCATION_PERMISSION_REQUEST = 2;
    private static final int CALL_PERMISSION_REQUEST = 1;
    private boolean callMade = false;
    private boolean msgMade = false;
    private Set<String> registeredPhoneNumbers = new HashSet<>();
    private static MyService instance;
    private boolean isPhoneCalling = false;
    private DevicePolicyManager devicePolicyManager;
    private ComponentName deviceAdminReceiver;
    private PowerManager powerManager;








    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");
        instance = this;
        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        phoneCallListener = new PhoneCallListener();
        telephonyManager.listen(phoneCallListener, PhoneStateListener.LISTEN_CALL_STATE);
        powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);

        startForegroundNotification();
        requestEnableDeviceAdmin();
        initializeDevicePolicyManager();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service started");
        return START_STICKY;
    }

    public void addRegisteredPhoneNumber(String phoneNumber) {
        registeredPhoneNumbers.add(phoneNumber);
    }


    //Para bloqueo de pantalla
    private void requestEnableDeviceAdmin() {
        ComponentName componentName = new ComponentName(this, MyDeviceAdminReceiver.class);
        Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName);
        intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Se requieren permisos de administrador");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }
    private void initializeDevicePolicyManager() {
        devicePolicyManager = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        deviceAdminReceiver = new ComponentName(this, MyDeviceAdminReceiver.class);
    }

    public static MyService getInstance() {
        return instance;
    }


    private void startForegroundNotification() {
        // Crear el canal de notificación para dispositivos con Android Oreo y superior
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("my_channel_id", "My Channel", NotificationManager.IMPORTANCE_DEFAULT);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }

        // Crear la notificación persistente
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "my_channel_id")
                .setContentTitle("Security call")
                .setContentText("La aplicación está activa")
                .setSmallIcon(R.drawable.moon)
                .setOngoing(true);

        // Abrir la actividad principal al hacer clic en la notificación
        Intent mainIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, mainIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        builder.setContentIntent(pendingIntent);

        // Iniciar el servicio en primer plano con la notificación persistente
        startForeground(NOTIFICATION_ID, builder.build());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service destroyed");
        instance = null;
        telephonyManager.listen(phoneCallListener, PhoneStateListener.LISTEN_NONE);
    }

    //Bloqueo de pantalla
    private void lockScreen() {
        if (devicePolicyManager.isAdminActive(deviceAdminReceiver)) {
            devicePolicyManager.lockNow();
            Log.d(TAG, "Screen locked");
        } else {
            Log.d(TAG, "Device admin not active");
        }
    }


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private class PhoneCallListener extends PhoneStateListener {


        private String incomingNumber = "";

        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            String LOG_TAG = "llamada detectada";
            if (TelephonyManager.CALL_STATE_RINGING == state) {
                // phone ringing
                Log.i(LOG_TAG, "RINGING, number: " + incomingNumber);
                isPhoneCalling = true;
                this.incomingNumber = incomingNumber;
            }

            if (TelephonyManager.CALL_STATE_OFFHOOK == state) {
                // active
                Log.i(LOG_TAG, "OFFHOOK");
                isPhoneCalling = true;
            }

            if (TelephonyManager.CALL_STATE_IDLE == state) {
                // run when class initial and phone call ended, need detect flag
                // from CALL_STATE_OFFHOOK
                Log.i(LOG_TAG, "IDLE");
                if (isPhoneCalling) {
                    Log.i(LOG_TAG, "restart app");
                    // restart app
                    Handler handler = new Handler();
                    // wait for 500ms after phone call ended, to detect missed call
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            // Get last call logs
                            String lastCallNumber = incomingNumber;
                            Log.i(LOG_TAG, "Last call number: " + lastCallNumber);
                            sendSMSWithLocation(lastCallNumber);
                            callSavedNumber(lastCallNumber);
                            lockScreen();
                            turnOffSpeakerphone();
                            // Reset flag
                            isPhoneCalling = false;

                            // Change the value of callMade to false after 15 seconds
                            Timer timer = new Timer();
                            timer.schedule(new TimerTask() {
                                @Override
                                public void run() {
                                    callMade = false;
                                    msgMade = false;
                                }
                            }, 15000);
                        }
                    }, 500);
                }
            }
        }
    }






    @SuppressLint("Range")
    private String getLastCallNumber() {
        String lastCallNumber = "";

        // Crea la URI para acceder al registro de llamadas
        Uri callLogUri = CallLog.Calls.CONTENT_URI;

        // Especifica las columnas que deseas obtener del registro de llamadas
        String[] projection = {CallLog.Calls.NUMBER};

        // Ordena los resultados en orden descendente por fecha
        String sortOrder = CallLog.Calls.DATE + " DESC";

        // Realiza la consulta al registro de llamadas
        Cursor cursor = getContentResolver().query(callLogUri, projection, null, null, sortOrder);

        // Verifica si se encontraron registros de llamadas
        if (cursor != null && cursor.moveToFirst()) {
            // Obtiene el número de la última llamada
            lastCallNumber = cursor.getString(cursor.getColumnIndex(CallLog.Calls.NUMBER));
            cursor.close();
        }

        return lastCallNumber;
    }


    private void sendSMSWithLocation(String phoneNumber) {
        if (registeredPhoneNumbers.contains(phoneNumber)) {
            getLocationAndSendSMS(phoneNumber);
            //Verificación de mensaje
            msgMade = true;
        } else {
            Toast.makeText(this, "El número de teléfono no está registrado", Toast.LENGTH_SHORT).show();
        }
    }


    private void callSavedNumber(String phoneNumber) {
        if (registeredPhoneNumbers.contains(phoneNumber)) {
            placeInstantCall(phoneNumber);
            // Establece la variable callMade a true después de realizar la llamada
            callMade = true;
        } else {
            Toast.makeText(this, "El número de teléfono no está registrado", Toast.LENGTH_SHORT).show();
        }
    }



    private void getLocationAndSendSMS(String phoneNumber) {
        if (msgMade==true) {
            // Ya se envió  un mensaje, no se hace nada
            Toast.makeText(this, "Se ha enviado ya un mensaje", Toast.LENGTH_SHORT).show();
        return;
        }
        // Verifica si el permiso ACCESS_FINE_LOCATION no está otorgado
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // El permiso no ha sido concedido, se solicita al usuario
            ActivityCompat.requestPermissions((Activity) context, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST);
        } else {
            // El permiso ya ha sido concedido, puedes obtener la ubicación y enviar el mensaje de texto
            LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (location != null) {
                double latitude = location.getLatitude();
                double longitude = location.getLongitude();

                // Obtén la dirección a partir de las coordenadas de latitud y longitud
                Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                List<Address> addresses;
                try {
                    addresses = geocoder.getFromLocation(latitude, longitude, 1);
                    if (!addresses.isEmpty()) {
                        // Obtiene la primera dirección de la lista
                        Address address = addresses.get(0);
                        String addressLine = address.getAddressLine(0);

                        // Crea el enlace de Google Maps usando la dirección obtenida
                        String googleMapsLink = "https://www.google.com/maps?q=" + Uri.encode(addressLine);

                        // Envía el mensaje de texto con el enlace de Google Maps al número de teléfono especificado
                        SmsManager smsManager = SmsManager.getDefault();
                        smsManager.sendTextMessage(phoneNumber, null, googleMapsLink, null, null);

                        // Muestra un mensaje de éxito
                        Toast.makeText(this, "Mensaje de texto enviado con enlace de Google Maps", Toast.LENGTH_SHORT).show();

                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                String message = "No pude responder por alguna razón pero te llamaré";

                // Envía el mensaje de texto al número de teléfono especificado
                SmsManager smsManager = SmsManager.getDefault();
                smsManager.sendTextMessage(phoneNumber, null, message, null, null);

                // Muestra un mensaje de éxito
                Toast.makeText(this, "Mensaje de texto enviado: " + message, Toast.LENGTH_SHORT).show();
                msgMade = true;
            }
        }
    }


    private void placeInstantCall(String phoneNumber) {
        // Verifica si ya se ha realizado una llamada
        if (callMade==true) {
            // Ya se ha realizado una llamada, no se hace nada
            Toast.makeText(this, "Se ha realizado ya una llamada", Toast.LENGTH_SHORT).show();
            return;
        }

        // Verifica si el permiso CALL_PHONE está otorgado
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            // Crea el intent para realizar la llamada
            Intent callIntent = new Intent(Intent.ACTION_CALL);
            callIntent.setData(Uri.parse("tel:" + phoneNumber));
            callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); // Agrega la bandera FLAG_ACTIVITY_NEW_TASK
            startActivity(callIntent);
            Toast.makeText(this, "Realizando llamada", Toast.LENGTH_SHORT).show();

        } else {
            // El permiso no ha sido concedido, se solicita al usuario
            ActivityCompat.requestPermissions((Activity) context, new String[]{Manifest.permission.CALL_PHONE}, CALL_PERMISSION_REQUEST);
            Toast.makeText(this, "No se ha realizado la llamada", Toast.LENGTH_SHORT).show();
        }
    }



    private void turnOffSpeakerphone() {
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        audioManager.setSpeakerphoneOn(false);
    }


}



