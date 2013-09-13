/*
 * Copyright (c) 2013, Psiphon Inc.
 * All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package ca.psiphon.ploggy;

import java.io.IOException;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;

import com.squareup.otto.Subscribe;

public class LocationMonitor implements android.location.LocationListener {

    // TODO: http://stackoverflow.com/questions/3145089/what-is-the-simplest-and-most-robust-way-to-get-the-users-current-location-in-a
    // TODO: http://developer.android.com/guide/topics/location/strategies.html
    // TODO: http://code.google.com/p/android-protips-location/
    
    // public int mLocationUpdatePeriodInSeconds = 600;
    // public int mLocationFixPeriodInSeconds = 60;

    Context mContext;
    Timer mLocationUpdateTimer;
    Timer mLocationFixTimer;
    Location mLastReportedLocation;
    Location mCurrentLocation;
    
    LocationMonitor(Context context) {
        mContext = context;
    }
    
    public void start() throws Utils.ApplicationError {
        mLocationUpdateTimer = new Timer();
        mLocationUpdateTimer.schedule(
            new TimerTask() {          
                @Override
                public void run() {
                	try {
                		startLocationListeners();
                	} catch (Utils.ApplicationError e) {
                		// TODO: ...log
                	}
                }
            },
            0,
            Data.getInstance().getPreferences().mLocationUpdatePeriodInSeconds*1000);        
    }
    
    public void stop() {
        if (mLocationUpdateTimer != null) {
            mLocationUpdateTimer.cancel();
            mLocationUpdateTimer = null;
        }
        if (mLocationFixTimer != null) {
            mLocationFixTimer.cancel();
            mLocationFixTimer = null;
        }
        stopLocationListeners();
    }

    public void restart() throws Utils.ApplicationError {
        stop();
        start();
    }
    
    @Subscribe
    public void handleUpdatedPreferences(
            Events.UpdatedPreferences updatedPreferences) {
    	try {
    		restart();
    	} catch (Utils.ApplicationError e) {
    		// TODO: ...log?
    	}
    }    
    
    public void startLocationListeners() throws Utils.ApplicationError {
        LocationManager locationManager = (LocationManager)mContext.getSystemService(Context.LOCATION_SERVICE);
        
        for (String provider: locationManager.getAllProviders()) {
        	updateCurrentLocation(locationManager.getLastKnownLocation(provider));
        }

        // TODO: min time, min distance
        // TODO: requestSingleUpdate (API 9)
        
        // requesting updates: explicit
        
        if (locationManager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER)) {
            locationManager.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 0, 0, this);    
        }
        
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);    
        }
        
        if (Data.getInstance().getPreferences().mAllowUseNetworkLocationProvider
                && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, this);    
        }
        
        // TODO: no providers?
        
        mLocationFixTimer = new Timer();
        mLocationFixTimer.schedule(
            new TimerTask() {          
                @Override
                public void run() {
                	try {
	                    reportLocation();
	                    stopLocationListeners();
                	} catch (Utils.ApplicationError e) {
                		// TODO: ...log
                	}
                }
            },
            0,
            Data.getInstance().getPreferences().mLocationFixPeriodInSeconds*1000);        
    }
    
    public void stopLocationListeners() {
        LocationManager locationManager = (LocationManager)mContext.getSystemService(Context.LOCATION_SERVICE);
        locationManager.removeUpdates(this);        
    }
        
    public void reportLocation() throws Utils.ApplicationError {
    	if (mCurrentLocation == null) {
    		return;
    	}

    	if (mLastReportedLocation != null &&
    		mLastReportedLocation.distanceTo(mCurrentLocation)
    			<= Data.getInstance().getPreferences().mLocationReportThresholdInMeters) {
    		return;
    	}

		mLastReportedLocation = mCurrentLocation;

    	if (Data.getInstance().getPreferences().mAllowUseGeoCoder) {
            Runnable task = new Runnable() {
                public void run() {
                    Geocoder geocoder = new Geocoder(mContext);
                    List<Address> addresses = null;
					try {
						// TODO: https://code.google.com/p/osmbonuspack/wiki/Overview#Geocoding_and_Reverse_Geocoding
						addresses = geocoder.getFromLocation(
								mLastReportedLocation.getLatitude(),
								mLastReportedLocation.getLongitude(),
								1);
					} catch (IOException e) {
						// TODO: report error
                    }
					
					// TODO: get tiles
					// http://code.google.com/p/osmdroid/source/browse/trunk/osmdroid-android/src/main/java/org/osmdroid/tileprovider/MapTileProviderBasic.java
					
					Address address = (addresses != null && addresses.size() > 0) ? addresses.get(0) : null;
					Events.bus.post(new Events.NewSelfLocation(mLastReportedLocation, address));
                }
            };
            Engine.getInstance().submitTask(task);
    		
    	} else {
    		Events.bus.post(new Events.NewSelfLocation(mLastReportedLocation, null));
    	}
    }

    @Override
    public void onLocationChanged(Location location) {
        updateCurrentLocation(location);
    }

    @Override
    public void onProviderDisabled(String provider) {
    	try {
    		restart();
    	} catch (Utils.ApplicationError e) {
    		// TODO: ...log?
    	}
    }

    @Override
    public void onProviderEnabled(String provider) {
    	try {
    		restart();
    	} catch (Utils.ApplicationError e) {
    		// TODO: ...log?
    	}
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    	try {
    		restart();
    	} catch (Utils.ApplicationError e) {
    		// TODO: ...log?
    	}
    }

    protected void updateCurrentLocation(Location location) {
        if (isBetterLocation(location, mCurrentLocation)) {
        	mCurrentLocation = location;
        }
    }
    
    // http://developer.android.com/guide/topics/location/strategies.html
    protected boolean isBetterLocation(Location location, Location currentBestLocation) {

        final int TWO_MINUTES = 1000 * 60 * 2;

        if (currentBestLocation == null) {
            // A new location is always better than no location
            return true;
        }

        // Check whether the new location fix is newer or older
        long timeDelta = location.getTime() - currentBestLocation.getTime();
        boolean isSignificantlyNewer = timeDelta > TWO_MINUTES;
        boolean isSignificantlyOlder = timeDelta < -TWO_MINUTES;
        boolean isNewer = timeDelta > 0;

        // If it's been more than two minutes since the current location, use the new location
        // because the user has likely moved
        if (isSignificantlyNewer) {
            return true;
        // If the new location is more than two minutes older, it must be worse
        } else if (isSignificantlyOlder) {
            return false;
        }

        // Check whether the new location fix is more or less accurate
        int accuracyDelta = (int) (location.getAccuracy() - currentBestLocation.getAccuracy());
        boolean isLessAccurate = accuracyDelta > 0;
        boolean isMoreAccurate = accuracyDelta < 0;
        boolean isSignificantlyLessAccurate = accuracyDelta > 200;

        // Check if the old and new location are from the same provider
        boolean isFromSameProvider = isSameProvider(location.getProvider(),
                currentBestLocation.getProvider());

        // Determine location quality using a combination of timeliness and accuracy
        if (isMoreAccurate) {
            return true;
        } else if (isNewer && !isLessAccurate) {
            return true;
        } else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) {
            return true;
        }
        return false;
    }

    private boolean isSameProvider(String provider1, String provider2) {
        if (provider1 == null) {
          return provider2 == null;
        }
        return provider1.equals(provider2);
    }
}
