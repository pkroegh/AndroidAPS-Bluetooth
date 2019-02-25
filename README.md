# AndroidAPS-Bluetooth
Copy of AndroidAPS found at https://github.com/MilosKozak/AndroidAPS by MilosKozak

This version includes a plugin allowing communication with an ESP32 microcontroller. The ESP32 is intended to be connected to a Medtronic MMT-554, allowing regulation of temp basal. The source code for the ESP32 along with instruction for setup can be found at: https://github.com/pkroegh/ESP32-BluetoothPump

The communication with the ESP32 is done through bluetooth. A plugin has been added to AndroidAPS, containing a user interface and a service for communication with the ESP.
