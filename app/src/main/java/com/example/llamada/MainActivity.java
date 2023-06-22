package com.example.llamada;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    private EditText phoneNumberEditText;
    private Button saveButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        phoneNumberEditText = findViewById(R.id.numtxt);
        saveButton = findViewById(R.id.botonG);

        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String phoneNumber = phoneNumberEditText.getText().toString().trim();

                // Verifica si el número de teléfono no está vacío
                if (!TextUtils.isEmpty(phoneNumber)) {
                    // Guarda el número de teléfono en SharedPreferences
                    SharedPreferences preferences = getSharedPreferences("MyPreferences", Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = preferences.edit();
                    editor.putString("phoneNumber", phoneNumber);
                    editor.apply();

                    // Accede al servicio MyService existente y agrega el número de teléfono
                    MyService myService = MyService.getInstance();
                    if (myService != null) {
                        myService.addRegisteredPhoneNumber(phoneNumber);
                        Toast.makeText(MainActivity.this, "Número guardado " + phoneNumber, Toast.LENGTH_SHORT).show();
                        phoneNumberEditText.setText("");
                    } else {
                        Toast.makeText(MainActivity.this, "Error: el servicio no está disponible", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(MainActivity.this, "Ingrese un número de teléfono válido", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // Iniciar el servicio MyService
        Intent serviceIntent = new Intent(this, MyService.class);
        startService(serviceIntent);
    }
}






