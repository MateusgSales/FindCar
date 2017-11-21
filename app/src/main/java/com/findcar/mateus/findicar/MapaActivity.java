package com.findcar.mateus.findicar;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.github.clans.fab.FloatingActionButton;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.gson.Gson;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import modules.DirectionFinder;
import modules.DirectionFinderListener;
import modules.Route;

public class MapaActivity extends FragmentActivity implements OnMapReadyCallback,
        LocationListener,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        DirectionFinderListener{

    static final String TAG = "MateusFindcar";
    private final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;
    private static final int GET_LOCATION = 1;

    private Location mLastLocation;
    private GoogleMap mMap;
    private GoogleApiClient mGoogleApiClient;
    private LocationRequest mLocationRequest;

    LatLng fortaleza = new LatLng(-3.733221, -38.496979);
    private FloatingActionButton fabCompartilhar;
    private FloatingActionButton fabRota;
    private FloatingActionButton fabDeletar;
    private LatLng carLocation;
    private LatLng actualLocation;
    SharedPreferences pref;
    SharedPreferences.Editor editor;

    private ProgressDialog progressDialog;
    private List<Marker> originMarkers = new ArrayList<>();
    private List<Marker> destinationMarkers = new ArrayList<>();
    private List<Polyline> polylinePaths = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mapa);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        pref = getApplicationContext().getSharedPreferences("Location", MODE_PRIVATE);
        editor = pref.edit();

        carLocation = getCarLocation();

        fabCompartilhar = findViewById(R.id.fab_compartilhar);
        fabRota = findViewById(R.id.fab_rota);
        fabDeletar = findViewById(R.id.fab_deletar);

        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
        } else {
            Toast.makeText(this, "Não conectado..", Toast.LENGTH_SHORT).show();
        }


        mLocationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(10 * 1000)        // 10 seconds, in milliseconds
                .setFastestInterval(1 * 1000); // 1 second, in milliseconds


        fabCompartilhar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                shareLocation();
            }
        });

        fabRota.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendRequest();
            }
        });

        fabDeletar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                deleteCarLocation();
            }
        });

    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // Adicionar marker na localização atual
        if(carLocation != null){
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(carLocation,18));
        }else{
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(fortaleza,10));
        }

        restoreCarLocation();

        mMap.setOnMapLongClickListener(new GoogleMap.OnMapLongClickListener() {
            @Override
            public void onMapLongClick(LatLng latLng) {

                mMap.clear();

                Drawable carDrawable = getResources().getDrawable(R.drawable.ic_car);
                BitmapDescriptor markerIcon = getMarkerIconFromDrawable(carDrawable);

                mMap.addMarker(new MarkerOptions()
                        .position(latLng)
                        .title("Parei aqui")
                        .icon(markerIcon));
                Toast.makeText(MapaActivity.this, "Localização Salva!", Toast.LENGTH_SHORT).show();
                carLocation = latLng;
                setCarLocation(latLng);
                Log.d(TAG, "Localização Marker: " + "Lat: " + latLng.latitude + " Long: " + latLng.latitude);
            }
        });
    }

    protected void onStart() {
        mGoogleApiClient.connect();
        carLocation = getCarLocation();
        super.onStart();
    }

    protected void onStop() {
        mGoogleApiClient.disconnect();
        super.onStop();
    }

    protected void onResume() {

        super.onResume();
        carLocation = getCarLocation();
        mGoogleApiClient.connect();
    }

    protected  void onPause() {
        super.onPause();
        if (mGoogleApiClient.isConnected()){
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
            mGoogleApiClient.disconnect();
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        actualLocation = new LatLng(location.getLatitude(),location.getLongitude());
        Log.d(TAG, "Nova Localização: " + "Lat: " + location.getLatitude()
                + "Long: " + location.getLongitude());
    }

    @Override
    public void onConnected(Bundle bundle) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    GET_LOCATION);
            return;
        }

        mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);

        if (mLastLocation != null) {
            Log.d(TAG, "Sucesso ao conectar ao serviço de localização!");
            Log.d(TAG,"Ultima Localização: " + "Lat: " + mLastLocation.getLatitude() + "Long: " + mLastLocation.getLongitude());
            actualLocation = new LatLng(mLastLocation.getLatitude(),mLastLocation.getLongitude());
        }else{
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
            Log.d(TAG, "Falha ao conectar ao serviço de localização!");
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
        mGoogleApiClient.connect();
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        if (connectionResult.hasResolution()) {
            try {
                // Start an Activity that tries to resolve the error
                connectionResult.startResolutionForResult(this, CONNECTION_FAILURE_RESOLUTION_REQUEST);
            } catch (IntentSender.SendIntentException e) {
                e.printStackTrace();
            }
        } else {
            Log.i(TAG, "Serviço de localização falhou com o codigo: " + connectionResult.getErrorCode());
        }
    }

    private BitmapDescriptor getMarkerIconFromDrawable(Drawable drawable) {
        Canvas canvas = new Canvas();
        Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        canvas.setBitmap(bitmap);
        drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
        drawable.draw(canvas);
        return BitmapDescriptorFactory.fromBitmap(bitmap);
    }

    public void setCarLocation(LatLng location){
            Gson gson = new Gson();
            String json = gson.toJson(location);
            editor.putString("location", json);
            editor.commit();
    }

    public LatLng getCarLocation(){
        Gson gson = new Gson();
        String json = pref.getString("location", "");
        LatLng location = gson.fromJson(json, LatLng.class);
        return location;
    }

    public void deleteCarLocation(){
        if (carLocation != null){
            editor.clear();
            editor.commit();
            mMap.clear();
            Toast.makeText(MapaActivity.this, "Localização Removida!", Toast.LENGTH_SHORT).show();
        }else{
            Toast.makeText(this, "Sem Localização do veiculo!", Toast.LENGTH_SHORT).show();
        }
    }

    public void restoreCarLocation(){
        Drawable carDrawable = getResources().getDrawable(R.drawable.ic_car);
        BitmapDescriptor markerIcon = getMarkerIconFromDrawable(carDrawable);

        if (carLocation != null){
            mMap.addMarker(new MarkerOptions()
                .position(carLocation)
                .title("Parei aqui")
                .icon(markerIcon));
        }

    }

    public void shareLocation(){

        // Compartilhar SMS
        if(carLocation != null){

            String uri = "http://maps.google.com/maps?saddr=" +carLocation.latitude+","+carLocation.longitude;

            Intent sendIntent = new Intent();
            sendIntent.setAction(Intent.ACTION_SEND);
            sendIntent.putExtra(Intent.EXTRA_TEXT, "Meu carro está aqui!:\n " + uri);
            sendIntent.setType("text/plain");
            startActivity(sendIntent);

        }else{
            Toast.makeText(this, "Sem localização do veiculo!", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDirectionFinderStart() {
        progressDialog = ProgressDialog.show(this, "Aguarde.",
                "Buscando Rota..!", true);

        if (originMarkers != null) {
            for (Marker marker : originMarkers) {
                marker.remove();
            }
        }

        if (destinationMarkers != null) {
            for (Marker marker : destinationMarkers) {
                marker.remove();
            }
        }

        if (polylinePaths != null) {
            for (Polyline polyline:polylinePaths ) {
                polyline.remove();
            }
        }
    }

    @Override
    public void onDirectionFinderSuccess(List<Route> routes) {
        progressDialog.dismiss();
        polylinePaths = new ArrayList<>();
        originMarkers = new ArrayList<>();
        destinationMarkers = new ArrayList<>();

        for (Route route : routes) {
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(route.startLocation, 16));

            Drawable carPositionIcon = getResources().getDrawable(R.drawable.ic_car);
            BitmapDescriptor markerCarIcon = getMarkerIconFromDrawable(carPositionIcon);

            Drawable actualPositionIcon = getResources().getDrawable(R.drawable.ic_girl);
            BitmapDescriptor markerGirlIcon = getMarkerIconFromDrawable(actualPositionIcon);

            originMarkers.add(mMap.addMarker(new MarkerOptions()
                    .icon(markerGirlIcon)
                    .title(route.startAddress)
                    .position(route.startLocation)));
            destinationMarkers.add(mMap.addMarker(new MarkerOptions()
                    .icon(markerCarIcon)
                    .title(route.endAddress)
                    .position(route.endLocation)));

            PolylineOptions polylineOptions = new PolylineOptions().
                    geodesic(true).
                    color(Color.BLUE).
                    width(12);

            for (int i = 0; i < route.points.size(); i++)
                polylineOptions.add(route.points.get(i));

            polylinePaths.add(mMap.addPolyline(polylineOptions));
        }
    }

    private void sendRequest() {
        LatLng carLocation = getCarLocation();
        if(actualLocation != null && carLocation != null){
            try {
                mMap.clear();
                new DirectionFinder(this, actualLocation, carLocation).execute();
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }else {
            Toast.makeText(this, "Sem Localização!", Toast.LENGTH_SHORT).show();
        }
    }

}