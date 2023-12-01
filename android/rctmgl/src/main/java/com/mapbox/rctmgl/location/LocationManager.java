package com.mapbox.rctmgl.location;

import android.content.Context;
import android.location.Location;
import android.os.Looper;
import android.util.Log;

import com.mapbox.mapboxsdk.location.engine.LocationEngine;
import com.mapbox.mapboxsdk.location.engine.LocationEngineCallback;

import com.mapbox.mapboxsdk.location.engine.MapboxFusedLocationEngineImpl;
import com.mapbox.mapboxsdk.location.engine.LocationEngineProxy;
import com.mapbox.mapboxsdk.location.engine.LocationEngineDefault;

import com.mapbox.mapboxsdk.location.engine.LocationEngineRequest;
import com.mapbox.mapboxsdk.location.engine.LocationEngineResult;
import com.mapbox.mapboxsdk.location.permissions.PermissionsManager;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


/**
 * Created by nickitaliano on 12/12/17.
 */
// Remove exxesive logging after testing. 

@SuppressWarnings({"MissingPermission"})
public class LocationManager implements LocationEngineCallback<LocationEngineResult> {
    static final long DEFAULT_FASTEST_INTERVAL_MILLIS = 1000;
    static final long DEFAULT_INTERVAL_MILLIS = 1000;

    public static final String LOG_TAG = "LocationManager";

    private LocationEngine locationEngine;
    private Context context;
    private List<OnUserLocationChange> listeners = new ArrayList<>();

    private float mMinDisplacement = 0;
    private boolean isActive = false;
    private Location lastLocation = null;

    private LocationEngineRequest locationEngineRequest = null;

    private static WeakReference<LocationManager> INSTANCE = null;

    public static LocationManager getInstance(Context context) {
        if (INSTANCE == null) {
            INSTANCE = new WeakReference<>(new LocationManager(context));
        }
        return INSTANCE.get();
    }

    public interface OnUserLocationChange {
        void onLocationChange(Location location);
    }

    private LocationManager(Context context) {
        this.context = context;
        this.buildEngineRequest();

    }
    private void buildEngineRequest() {
        try {
            locationEngine = new LocationEngineProxy<>(new MapboxFusedLocationEngineImpl(context.getApplicationContext()));
            Log.d(LOG_TAG, "Location Engine created successfully.");
        } catch (Exception e) {
            Log.e(LOG_TAG, "Error creating Location Engine: " + e.getMessage());
        }
        locationEngineRequest = new LocationEngineRequest.Builder(DEFAULT_INTERVAL_MILLIS)
                .setFastestInterval(DEFAULT_FASTEST_INTERVAL_MILLIS)
                .setPriority(LocationEngineRequest.PRIORITY_HIGH_ACCURACY)
                .setDisplacement(mMinDisplacement)
                .build();
    }

    public void addLocationListener(OnUserLocationChange listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeLocationListener(OnUserLocationChange listener) {
        if (listeners.contains(listener)) {
            listeners.remove(listener);
        }
    }
    public void setMinDisplacement(float minDisplacement) {
        mMinDisplacement = minDisplacement;
    }
    public void enable() {
        if (!PermissionsManager.areLocationPermissionsGranted(context)) {
            return;
        }

        // remove existing listeners
        locationEngine.removeLocationUpdates(this);

        // refresh location engine request with new values
        this.buildEngineRequest();

        // add new listeners
        locationEngine.requestLocationUpdates(
                locationEngineRequest,
                this,
                Looper.getMainLooper()
        );
        try {
            locationEngine.requestLocationUpdates(locationEngineRequest, this, Looper.getMainLooper());
            isActive = true;
            Log.d(LOG_TAG, "Location updates enabled.");
        } catch (Exception e) {
            Log.e(LOG_TAG, "Error enabling location updates: " + e.getMessage());
        }
    }


    public void disable() {
        locationEngine.removeLocationUpdates(this);
        isActive = false;
        Log.d(LOG_TAG, "Location updates disabled");
    }

    public void dispose() {
        if (locationEngine == null) {
            return;
        }
        disable();
        locationEngine.removeLocationUpdates(this);
    }

    public boolean isActive() {
        return locationEngine != null && this.isActive;
    }

    public Location getLastKnownLocation() {
        if (locationEngine == null) {
            return null;
        }
        return lastLocation;
    }


    public void getLastKnownLocation(LocationEngineCallback<LocationEngineResult> callback) {
        if (locationEngine == null) {
            callback.onFailure(new Exception("LocationEngine not initialized"));
        }

        try {
            locationEngine.getLastLocation(callback);
        }
        catch(Exception exception) {
            Log.w(LOG_TAG, "location lastknown " + exception);
            callback.onFailure(exception);
        }
    }

    public LocationEngine getEngine() {
        return locationEngine;
    }

    public void onLocationChanged(Location location) {
        lastLocation = location;
        for (OnUserLocationChange listener : listeners) {
            listener.onLocationChange(location);
        }
        Log.d(LOG_TAG, "Location changed: " + location.getLatitude() + ", " + location.getLongitude());
    }

    @Override
    public void onFailure(Exception exception) {
        Log.e(LOG_TAG, "LocationEngine failure: ", exception);
    }

    @Override
    public void onSuccess(LocationEngineResult result) {
        try {
            onLocationChanged(result.getLastLocation());
            Log.d(LOG_TAG, "LocationEngine success: " + result.getLastLocation().toString());
        } catch (Exception e) {
            Log.e(LOG_TAG, "Error processing location update: " + e.getMessage());
        }
    }
}
