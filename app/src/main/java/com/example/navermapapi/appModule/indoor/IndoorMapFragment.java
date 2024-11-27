package com.example.navermapapi.appModule.indoor;

import static androidx.constraintlayout.widget.ConstraintLayoutStates.TAG;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.example.navermapapi.R;
import com.example.navermapapi.appModule.main.MainActivity;
import com.example.navermapapi.appModule.main.MainViewModel;
import com.example.navermapapi.appModule.outdoor.OutdoorMapFragment;
import com.example.navermapapi.coreModule.api.environment.model.EnvironmentType;
import com.example.navermapapi.coreModule.api.location.model.LocationData;
import com.example.navermapapi.databinding.FragmentIndoorMapBinding;
import com.naver.maps.geometry.LatLng;
import com.naver.maps.geometry.LatLngBounds;
import com.naver.maps.map.CameraPosition;
import com.naver.maps.map.CameraUpdate;
import com.naver.maps.map.LocationTrackingMode;
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.OnMapReadyCallback;
import com.naver.maps.map.overlay.LocationOverlay;
import com.naver.maps.map.util.FusedLocationSource;

import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class IndoorMapFragment extends Fragment implements OnMapReadyCallback {
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1000;
    private static final double FLOOR_PLAN_WIDTH_METERS = 100.0;
    private static final double FLOOR_PLAN_HEIGHT_METERS = 80.0;

    private static final String NAVER_CLIENT_ID = "9fk3x9j37i";
    private static final String NAVER_CLIENT_SECRET = "UvmuPMa5rUWtSbyvoCKZoNBnvAZSl4kjIyjvtZga";

    private FragmentIndoorMapBinding binding;
    private MainViewModel viewModel;
    private NaverMap naverMap;
    private LocationOverlay locationOverlay;
    private FusedLocationSource locationSource;
    private IndoorMapOverlay indoorMapOverlay;
    private int stepCount = 0;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        locationSource = new FusedLocationSource(this, LOCATION_PERMISSION_REQUEST_CODE);
        indoorMapOverlay = new IndoorMapOverlay();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentIndoorMapBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding.indoorMapView.onCreate(savedInstanceState);
        binding.indoorMapView.getMapAsync(this);

        setupUI();
        setupObservers();
    }

    private void setupUI() {
        binding.overlayOpacitySeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (indoorMapOverlay != null) {
                    float opacity = progress / 100.0f;
                    indoorMapOverlay.setOpacity(opacity);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        binding.overlayRotationSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (indoorMapOverlay != null) {
                    double rotation = progress * 3.6;
                    indoorMapOverlay.setRotation(rotation);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        binding.indoorVoiceGuideButton.setOnClickListener(v -> {
            LocationData currentLocation = viewModel.getCurrentLocation().getValue();
            if (currentLocation != null) {
                announceCurrentStatus();
            }
        });
    }

    private void setupObservers() {
        viewModel.getCurrentLocation().observe(getViewLifecycleOwner(), this::updateLocationUI);
        viewModel.getCurrentEnvironment().observe(getViewLifecycleOwner(), this::handleEnvironmentChange);
    }

    @Override
    public void onMapReady(@NonNull NaverMap naverMap) {
        this.naverMap = naverMap;

        setupMapSettings(naverMap);
        setupLocationOverlay(naverMap);

        String targetAddress = "서울 성동구 살곶이길 200";
        new Thread(() -> {
            LatLng coordinates = getCoordinatesFromAddress(targetAddress);
            if (coordinates != null) {
                requireActivity().runOnUiThread(() -> {
                    setupIndoorMapOverlay(coordinates);
                    naverMap.moveCamera(CameraUpdate.scrollTo(coordinates));
                    naverMap.moveCamera(CameraUpdate.zoomTo(18.0));
                });
            } else {
                Log.e("IndoorMapFragment", "Failed to get coordinates for address: " + targetAddress);
            }
        }).start();

        LocationData lastLocation = viewModel.getCurrentLocation().getValue();
        if (lastLocation != null) {
            updateLocationUI(lastLocation);
        }
    }

    private LatLng getCoordinatesFromAddress(String address) {
        try {
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .writeTimeout(10, TimeUnit.SECONDS)
                    .build();

            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl("https://naveropenapi.apigw.ntruss.com/")
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();

            GeocodingService service = retrofit.create(GeocodingService.class);

            Response<GeocodingResponse> response = service.getCoordinates(
                    address,
                    NAVER_CLIENT_ID,
                    NAVER_CLIENT_SECRET
            ).execute();

            if (response.isSuccessful() && response.body() != null) {
                List<GeocodingResponse.Address> addresses = response.body().getAddresses();
                if (addresses != null && !addresses.isEmpty()) {
                    GeocodingResponse.Address addr = addresses.get(0);
                    Log.d(TAG, "Successfully geocoded address: " + addr.getRoadAddress());
                    return new LatLng(addr.getLat(), addr.getLng());
                } else {
                    Log.e(TAG, "No addresses found in response");
                }
            } else {
                Log.e(TAG, "Geocoding API error - Code: " + response.code() +
                        ", Message: " + response.message() +
                        ", Error body: " + (response.errorBody() != null ?
                        response.errorBody().string() : "null"));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting coordinates", e);
        }

        // 기본 좌표값 반환 (한양여대)
        Log.w(TAG, "Returning default coordinates (한양여대)");
        return new LatLng(37.5579175887117, 127.0493218973167);
    }

    private void setupMapSettings(@NonNull NaverMap naverMap) {
        naverMap.setLocationSource(locationSource);
        naverMap.setLocationTrackingMode(LocationTrackingMode.Follow);
        naverMap.setIndoorEnabled(true);

        naverMap.setMinZoom(17.0);
        naverMap.setMaxZoom(20.0);

        naverMap.getUiSettings().setIndoorLevelPickerEnabled(true);
        naverMap.getUiSettings().setZoomControlEnabled(true);
        naverMap.getUiSettings().setCompassEnabled(true);
    }

    private void setupLocationOverlay(@NonNull NaverMap naverMap) {
        locationOverlay = naverMap.getLocationOverlay();
        locationOverlay.setVisible(true);
    }

    private void setupIndoorMapOverlay(LatLng center) {
        try {
            Bitmap floorPlanBitmap = BitmapFactory.decodeResource(
                    getResources(),
                    R.drawable.indoor_floor_plan_3f
            );

            LatLngBounds bounds = calculateFloorPlanBounds(
                    center,
                    FLOOR_PLAN_WIDTH_METERS,
                    FLOOR_PLAN_HEIGHT_METERS
            );

            indoorMapOverlay.setFloorPlan(floorPlanBitmap);
            indoorMapOverlay.setBounds(bounds);
            indoorMapOverlay.setMap(naverMap);
            indoorMapOverlay.setOpacity(0.7f);

        } catch (Exception e) {
            Log.e("IndoorMapFragment", "Error setting up indoor map overlay", e);
        }
    }

    private LatLngBounds calculateFloorPlanBounds(LatLng center, double widthMeters, double heightMeters) {
        double metersPerLat = 111320.0;
        double metersPerLng = 111320.0 * Math.cos(Math.toRadians(center.latitude));

        double latOffset = (heightMeters / 2) / metersPerLat;
        double lngOffset = (widthMeters / 2) / metersPerLng;

        return new LatLngBounds(
                new LatLng(center.latitude - latOffset, center.longitude - lngOffset),
                new LatLng(center.latitude + latOffset, center.longitude + lngOffset)
        );
    }

    private void updateLocationUI(LocationData location) {
        if (location == null || naverMap == null || locationOverlay == null) return;

        LatLng position = new LatLng(location.getLatitude(), location.getLongitude());
        locationOverlay.setPosition(position);
        locationOverlay.setBearing(location.getBearing());

        if (viewModel.isAutoTrackingEnabled()) {
            CameraPosition cameraPosition = new CameraPosition(
                    position,
                    18.0,
                    0,
                    location.getBearing()
            );
            naverMap.setCameraPosition(cameraPosition);
        }

        updateStepCount();
        updateDirectionInfo(location.getBearing());

        if (indoorMapOverlay != null && indoorMapOverlay.getBounds() != null) {
            boolean isInsideFloorPlan = indoorMapOverlay.getBounds().contains(position);
            handleLocationInFloorPlan(isInsideFloorPlan, position);
        }
    }

    private void handleLocationInFloorPlan(boolean isInside, LatLng position) {
        if (isInside) {
            indoorMapOverlay.setVisible(true);
            updateRelativePosition(position);
        } else {
            binding.locationWarning.setVisibility(View.VISIBLE);
            binding.locationWarning.setText(R.string.outside_floor_plan_warning);
        }
    }

    private void updateRelativePosition(LatLng currentPosition) {
        if (indoorMapOverlay.getBounds() == null) return;

        LatLngBounds bounds = indoorMapOverlay.getBounds();
        double relativeX = (currentPosition.longitude - bounds.getSouthWest().longitude) /
                (bounds.getNorthEast().longitude - bounds.getSouthWest().longitude);
        double relativeY = (currentPosition.latitude - bounds.getSouthWest().latitude) /
                (bounds.getNorthEast().latitude - bounds.getSouthWest().latitude);

        binding.relativePositionText.setText(String.format(
                getString(R.string.relative_position_format),
                Math.round(relativeX * 100),
                Math.round(relativeY * 100)
        ));
    }

    private void handleEnvironmentChange(EnvironmentType environment) {
        if (environment == EnvironmentType.OUTDOOR) {
            if (indoorMapOverlay != null) {
                indoorMapOverlay.setVisible(false);
            }
            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.nav_host_fragment, new OutdoorMapFragment())
                    .commit();
        } else {
            if (indoorMapOverlay != null) {
                indoorMapOverlay.setVisible(true);
            }
        }
    }

    private void updateStepCount() {
        stepCount++;
        binding.indoorStepCount.setText(getString(R.string.step_count_format, stepCount));
    }

    private void updateDirectionInfo(float bearing) {
        String direction = getDirectionText(bearing);
        binding.indoorDirection.setText(getString(R.string.direction_format, direction));
    }

    private String getDirectionText(float degrees) {
        String[] directions = {"북", "북동", "동", "남동", "남", "남서", "서", "북서"};
        int index = Math.round(degrees / 45f) % 8;
        return directions[index];
    }

    private void announceCurrentStatus() {
        LocationData location = viewModel.getCurrentLocation().getValue();
        if (location != null) {
            String direction = getDirectionText(location.getBearing());
            String announcement = String.format(
                    getString(R.string.current_status_announcement),
                    direction,
                    stepCount
            );
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).announceVoiceGuide(announcement);
            }
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        binding.indoorMapView.onSaveInstanceState(outState);
        outState.putInt("stepCount", stepCount);
        outState.putFloat("overlayOpacity",
                binding.overlayOpacitySeekBar.getProgress() / 100.0f);
        outState.putFloat("overlayRotation",
                binding.overlayRotationSeekBar.getProgress() * 3.6f);
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        if (savedInstanceState != null) {
            stepCount = savedInstanceState.getInt("stepCount", 0);
            float opacity = savedInstanceState.getFloat("overlayOpacity", 0.7f);
            float rotation = savedInstanceState.getFloat("overlayRotation", 0f);

            binding.overlayOpacitySeekBar.setProgress((int)(opacity * 100));
            binding.overlayRotationSeekBar.setProgress((int)(rotation / 3.6f));

            if (indoorMapOverlay != null) {
                indoorMapOverlay.setOpacity(opacity);
                indoorMapOverlay.setRotation(rotation);
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        binding.indoorMapView.onStart();
    }

    @Override
    public void onResume() {
        super.onResume();
        binding.indoorMapView.onResume();
    }

    @Override
    public void onPause() {
        binding.indoorMapView.onPause();
        super.onPause();
    }

    @Override
    public void onStop() {
        binding.indoorMapView.onStop();
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (indoorMapOverlay != null) {
            indoorMapOverlay.cleanup();
        }
        binding.indoorMapView.onDestroy();
        binding = null;
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        binding.indoorMapView.onLowMemory();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (locationSource.onRequestPermissionsResult(
                requestCode, permissions, grantResults)) {
            if (!locationSource.isActivated()) {
                naverMap.setLocationTrackingMode(LocationTrackingMode.None);
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
}
