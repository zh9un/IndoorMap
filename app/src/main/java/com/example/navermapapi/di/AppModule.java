package com.example.navermapapi.di;

import android.content.Context;
import javax.inject.Singleton;
import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;
import com.example.navermapapi.appModule.accessibility.VoiceGuideManager;
import com.example.navermapapi.appModule.location.manager.LocationIntegrationManager;
import com.example.navermapapi.gpsModule.api.GpsLocationProvider;
import com.example.navermapapi.beaconModule.api.BeaconLocationProvider;

@Module
@InstallIn(SingletonComponent.class)
public class AppModule {

    @Provides
    @Singleton
    public VoiceGuideManager provideVoiceGuideManager(
            @ApplicationContext Context context
    ) {
        return new VoiceGuideManager(context);
    }

    @Provides
    @Singleton
    public LocationIntegrationManager provideLocationIntegrationManager(
            @ApplicationContext Context context,
            GpsLocationProvider gpsProvider,
            BeaconLocationProvider beaconProvider
    ) {
        return new LocationIntegrationManager(context, gpsProvider, beaconProvider);
    }

    @Provides
    @Singleton
    public GpsLocationProvider provideGpsLocationProvider(
            @ApplicationContext Context context
    ) {
        return new GpsLocationProvider(context);
    }

    @Provides
    @Singleton
    public BeaconLocationProvider provideBeaconLocationProvider(
            @ApplicationContext Context context
    ) {
        return new BeaconLocationProvider(context);
    }
}