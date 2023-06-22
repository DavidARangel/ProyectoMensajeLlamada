package com.example.llamada;

import android.app.Notification;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NotificationListenerService extends android.service.notification.NotificationListenerService {

    private static final String TAG = "CallNotificationListener";

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        super.onNotificationPosted(sbn);

        if (sbn.getPackageName().equals("com.android.phone")) {
            String title = sbn.getNotification().extras.getString(Notification.EXTRA_TITLE);
            String text = sbn.getNotification().extras.getString(Notification.EXTRA_TEXT);

            // Extraer el número de teléfono de 'title' o 'text' según el formato de la notificación
            String phoneNumber = extractPhoneNumber(title, text);

            if (phoneNumber != null) {
                Log.d(TAG, "Incoming llamada from: " + phoneNumber);

                // Aquí puedes realizar las acciones que necesites con el número de teléfono entrante
            }
        }
    }

    private String extractPhoneNumber(String title, String text) {
        // Implementa la lógica para extraer el número de teléfono de 'title' o 'text'
        // según el formato de la notificación. Puedes utilizar expresiones regulares,
        // análisis de cadenas, etc.

        // Ejemplo: extraer el número de teléfono de una notificación que tiene el formato 'Llamada entrante: xxx-xxx-xxxx'
        Pattern pattern = Pattern.compile("Llamada entrante: (\\d{3}-\\d{3}-\\d{4})");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }
}

