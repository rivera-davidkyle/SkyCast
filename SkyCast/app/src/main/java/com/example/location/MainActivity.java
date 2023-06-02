package com.example.location;

import static java.lang.Math.PI;
import static java.lang.Math.cos;
import static java.lang.Math.pow;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import android.Manifest;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity
        implements OnMapReadyCallback, SensorEventListener, LocationListener {

    // Request code for location permission request
    private static final int PERMISSION_REQUEST_LOCATION = 123;
    // Initialize handler
    public Handler myHandler = new Handler();

    // Variables for accelerometer data
    float x = 0.0f, y = 0.0f;
    float vx = 0.0f, vy = 0.0f;
    float ax = 0.0f, ay = 0.0f;
    private float init_time = 0.0f;

    // Variables for GPS location data
    double lng = 0, lat = 0;
    double rad = 6378.137;

    // Sensor and Location managers
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private LocationManager locationManager;

    // Google Maps variables
    private SupportMapFragment mapFragment;
    private Marker marker;
    private GoogleMap map;
    private EditText editTemp, editAppTemp, editWindSpd, editHumidity, editWeather, editDateTime;
    private static final String API_ENDPOINT = "https://4v42r4j4c8.execute-api.us-east-2.amazonaws.com/v1";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getSupportActionBar().setTitle("SkyCast");
        editTemp = (EditText) findViewById(R.id.editTemp);
        editAppTemp = (EditText) findViewById(R.id.editAppTemp);
        editHumidity = (EditText) findViewById(R.id.editHumidity);
        editWindSpd = (EditText) findViewById(R.id.editWindSp);
        editWeather = (EditText) findViewById(R.id.editWeather);
        editDateTime = (EditText) findViewById(R.id.editDateTime);

        // Initialize sensor and accelerometer
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = (Sensor) sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);


        // Request location permission from user
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_REQUEST_LOCATION);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_LOCATION);
        }

        // Initialize location manager
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        // Get the SupportMapFragment and call getMapAsync to initialize the map
        mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            // Get the last known location and set the marker on the map
            Location lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (lastLocation != null) {
                lat = lastLocation.getLatitude();
                lng = lastLocation.getLongitude();
                WeatherTask weatherTask = (WeatherTask) new WeatherTask().execute(lat,lng);
                marker = googleMap.addMarker(new MarkerOptions().position(new LatLng(lat, lng)).title("My Location"));
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(lat, lng), 14));
            }
        }
        map = googleMap;
        // Call LocTask every 10 seconds

        myHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                LocTask myTask = new LocTask(lat, lng);
                myHandler.post(myTask);
                myHandler.postDelayed(this, 2000);
            }
        }, 1000);
        myHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                updateLocation upLoc = new updateLocation();
                myHandler.post(upLoc);
                myHandler.postDelayed(this, 10000); // 10 seconds
            }
        }, 10000);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        // Check if the sensor is an accelerometer
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            // Calculate the change in time since the last sensor event
            float dt = (event.timestamp - init_time) / 1e9f;

            // Calculate alpha for the complimentary filter
            float alpha = 0.75f / (0.75f + dt);

            // Reset dt to 0 if this is the first sensor event
            if (init_time == 0.0f) {
                dt = 0.0f;
            }

            // Apply complimentary filter to accelerometer values
            ax = alpha * ax + (1 - alpha) * event.values[0];
            ay = alpha * ay + (1 - alpha) * event.values[1];

            // Calculate velocity and position using the filtered accelerometer values
            vx += ax * dt;
            vy += ay * dt;
            x += vx * dt + 0.5 * ax * pow(dt, 2);
            y += vy * dt + 0.5 * ay * pow(dt, 2);
        }

        // Update the initial time for the next sensor event
        init_time = event.timestamp;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
    private class LocTask implements Runnable{
        //  This puts the processes of updating the pitch, roll, and yaw in the UI in a queue
        double _lat, _lng;
        public LocTask(double lt, double lg){
            this._lat = lt;
            this._lng = lg;
        }
        @Override
        public void run() {
            // Update latitude and longitude using the calculated position
            _lat += y / rad;
            _lng += x / (rad * cos(PI * _lat / 180));

            // Handle latitude and longitude wrapping
            if (_lat > 90) {
                _lat -= 180;
            } else if (_lat < -90) {
                _lat += 180;
            }
            if (_lng > 180) {
                _lng -= 360;
            } else if (_lng < -180) {
                _lng += 360;
            }

            // Update the map marker with the new location
            if (marker != null) {
                marker.setPosition(new LatLng(_lat, _lng));
            }
            // Update the camera position on the map
            CameraPosition cameraPosition = new CameraPosition.Builder()
                    .target(new LatLng(_lat, _lng))
                    .zoom(15)
                    .build();
            map.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
            lat = _lat;
            lng = _lng;
            Log.d("DEAD-RECKONING", "X: " + x + " Y:" + y + " LNG: " + lng + " LAT:" + lat);
        }
    }
    private class updateLocation implements Runnable{
        double _lat, _lng;
        @Override
        public void run() {
            if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
            {
                Location lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (lastLocation != null) {
                    _lat = lastLocation.getLatitude();
                    _lng = lastLocation.getLongitude();
                    WeatherTask weatherTask = (WeatherTask) new WeatherTask().execute(_lat,_lng);
                    marker.setPosition(new LatLng(_lat, _lng));
                    CameraPosition cameraPosition = new CameraPosition.Builder()
                            .target(new LatLng(_lat, _lng))
                            .zoom(15)
                            .build();
                    map.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));
                }
            }
            lat = _lat;
            lng = _lng;
            x = 0;
            vx = 0;
            y = 0;
            vy = 0;
            Log.d("TRUE LOCATION", "X:" + x + " Y:" + y + " LNG:" + lng + " LAT:" + lat);
        }
    }
    private class WeatherTask extends AsyncTask<Double, Void, String> {
        @Override
        protected String doInBackground(Double... floats) {
            Double lat_ = floats[0];
            Double lon_ = floats[1];
            String result = "";

            try {
                JSONObject requestData = new JSONObject();
                requestData.put("lat", lat_);
                requestData.put("lon", lon_);

                URL url = new URL(API_ENDPOINT);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);

                OutputStream outputStream = connection.getOutputStream();
                outputStream.write(requestData.toString().getBytes());
                outputStream.flush();
                outputStream.close();

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    InputStream inputStream = connection.getInputStream();
                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
                    String line;
                    StringBuilder stringBuilder = new StringBuilder();
                    while ((line = bufferedReader.readLine()) != null) {
                        stringBuilder.append(line);
                    }
                    inputStream.close();
                    result = stringBuilder.toString();
                } else {
                    result = "Error: " + responseCode;
                }
            }
            catch (IOException | JSONException e)
            {
                e.printStackTrace();
                result = "Error: " + e.getMessage();
            }
            return result;
        }

        @Override
        protected void onPostExecute(String result) {
            try {
                JSONObject responseJson = new JSONObject(result);
                Integer loginSuccess = responseJson.getInt("statusCode");
                JSONObject bodyJson = new JSONObject(responseJson.getString("body"));

                if (loginSuccess < 300) {
                    SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, hh:mm a", Locale.US);
                    String currentDateTimeString = java.text.DateFormat.getDateTimeInstance().format(new Date());
                    currentDateTimeString = dateFormat.format(new Date());
                    editDateTime.setText(currentDateTimeString);
                    String weather = bodyJson.getString("weather_desc");
                    editWeather.setText(weather);
                    int temp = bodyJson.getInt("temp");
                    editTemp.setText(Integer.toString(temp) + "°F");
                    int apptemp = bodyJson.getInt("app_temp");
                    editAppTemp.setText("Feels like "+Integer.toString(apptemp)+"°F");
                    int windspd = bodyJson.getInt("wind_spd");
                    editWindSpd.setText("Wind Speed: "+Integer.toString(windspd)+" mph");
                    int hmd = bodyJson.getInt("humidity");
                    editHumidity.setText("Humidity: "+Integer.toString(hmd)+"%");
                } else {
                    String message = bodyJson.getString("message");
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                }
            } catch (JSONException e) {
                e.printStackTrace();
                Toast.makeText(MainActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {

    }

    @Override
    protected void onPause() {
        // Unregister the accelerometer sensor listener when the activity is paused
        super.onPause();
        sensorManager.unregisterListener(this, accelerometer);
    }

    @Override
    protected void onResume() {
        // Register the accelerometer sensor listener when the activity is resumed
        super.onResume();
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_FASTEST);
    }
}