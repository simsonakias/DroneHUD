package com.drones.dimis.dronehud.activities;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Spinner;

import com.drones.dimis.dronehud.R;
import com.drones.dimis.dronehud.common.otto.events.AirSpeedEvent;
import com.drones.dimis.dronehud.common.otto.events.AltitudeEvent;
import com.drones.dimis.dronehud.common.otto.events.AttitudeUpdateEvent;
import com.drones.dimis.dronehud.common.otto.events.BatteryUpdateEvent;
import com.drones.dimis.dronehud.common.otto.events.ConnectEvent;
import com.drones.dimis.dronehud.common.otto.events.DistanceFromHomeEvent;
import com.drones.dimis.dronehud.common.otto.events.DroneTypeEvent;
import com.drones.dimis.dronehud.common.otto.events.GpsFixEvent;
import com.drones.dimis.dronehud.common.otto.events.GroundSpeedEvent;
import com.drones.dimis.dronehud.common.otto.events.VehicleStateEvent;
import com.drones.dimis.dronehud.common.utils.Utilities;
import com.drones.dimis.dronehud.fragments.HUDFragment;
import com.drones.dimis.dronehud.fragments.TelemetryFragment;
import com.o3dr.android.client.ControlTower;
import com.o3dr.android.client.Drone;
import com.o3dr.android.client.interfaces.DroneListener;
import com.o3dr.android.client.interfaces.TowerListener;
import com.o3dr.services.android.lib.coordinate.LatLong;
import com.o3dr.services.android.lib.coordinate.LatLongAlt;
import com.o3dr.services.android.lib.drone.attribute.AttributeEvent;
import com.o3dr.services.android.lib.drone.attribute.AttributeType;
import com.o3dr.services.android.lib.drone.connection.ConnectionParameter;
import com.o3dr.services.android.lib.drone.connection.ConnectionResult;
import com.o3dr.services.android.lib.drone.connection.ConnectionType;
import com.o3dr.services.android.lib.drone.property.Altitude;
import com.o3dr.services.android.lib.drone.property.Attitude;
import com.o3dr.services.android.lib.drone.property.Battery;
import com.o3dr.services.android.lib.drone.property.Gps;
import com.o3dr.services.android.lib.drone.property.Home;
import com.o3dr.services.android.lib.drone.property.Speed;
import com.o3dr.services.android.lib.drone.property.State;
import com.o3dr.services.android.lib.drone.property.Type;
import com.o3dr.services.android.lib.drone.property.VehicleMode;

public class MainActivity extends Activity implements DroneListener, TowerListener,
        TelemetryFragment.OnTelemetryFragmentInteractionListener, HUDFragment.OnHUDFragmentInteractionListener {

    private Drone drone;
    private int droneType = Type.TYPE_PLANE;
    private ControlTower controlTower;
    private final Handler handler = new Handler();

    private final int DEFAULT_UDP_PORT = 14550;
    private final int DEFAULT_USB_BAUD_RATE = 57600;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (savedInstanceState != null) {
            return;
        }
        final Context context = getApplicationContext();
        this.controlTower = new ControlTower(context);
        this.drone = new Drone();

        android.app.FragmentTransaction ft = getFragmentManager().beginTransaction();

        if (findViewById(R.id.hud_fragment_container) != null) {
            HUDFragment hud = HUDFragment.newInstance("", "");
            ft.replace(R.id.hud_fragment_container, hud);
        }

        if (findViewById(R.id.telemetry_fragment_container) != null) {
            TelemetryFragment tlm = TelemetryFragment.newInstance(this.droneType, "");
            ft.replace(R.id.telemetry_fragment_container, tlm);
        }
        ft.commit();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public void onStart() {
        super.onStart();
        this.controlTower.connect(this);

    }

    @Override
    public void onStop() {
        super.onStop();
        if (this.drone.isConnected()) {
            this.drone.disconnect();
            updateConnectedButton(false);
        }
        this.controlTower.unregisterDrone(this.drone);
        this.controlTower.disconnect();
    }

    // 3DR Services Listener
    // ==========================================================

    @Override
    public void onTowerConnected() {
        Utilities.alertUser("3DR Services Connected", getApplicationContext());
        this.controlTower.registerDrone(this.drone, this.handler);
        this.drone.registerDroneListener(this);
    }

    @Override
    public void onTowerDisconnected() {
        Utilities.alertUser("3DR Service Interrupted", getApplicationContext());
    }

    // Drone Listener
    // ==========================================================

    @Override
    public void onDroneEvent(String event, Bundle extras) {

        switch (event) {
            case AttributeEvent.STATE_CONNECTED:
                Utilities.alertUser("Drone Connected", getApplicationContext());
                updateConnectedButton(this.drone.isConnected());
                updateDroneState();
                break;
            case AttributeEvent.STATE_DISCONNECTED:
                Utilities.alertUser("Drone Disconnected", getApplicationContext());
                updateConnectedButton(this.drone.isConnected());
                updateDroneState();
                break;
            case AttributeEvent.STATE_UPDATED:
            case AttributeEvent.STATE_ARMING:
            case AttributeEvent.STATE_VEHICLE_MODE:
                updateDroneState();
                break;
            case AttributeEvent.TYPE_UPDATED:
                Type newDroneType = this.drone.getAttribute(AttributeType.TYPE);
                if (newDroneType.getDroneType() != this.droneType) {
                    this.droneType = newDroneType.getDroneType();

                    DroneTypeEvent droneTypeEv = new DroneTypeEvent();
                    droneTypeEv.setData(this.droneType);
                    DroneHUDApplication.busPost(droneTypeEv);
                }
                break;
            case AttributeEvent.SPEED_UPDATED:
                updateAltitude();
                updateSpeed();
                break;

            case AttributeEvent.HOME_UPDATED:
                updateDistanceFromHome();
                break;

            case AttributeEvent.GPS_FIX:
                updateGpsFix();
                break;

            case AttributeEvent.BATTERY_UPDATED:
                updateBattery();
                break;
            case AttributeEvent.ATTITUDE_UPDATED:
                updateAttitude();
                break;
            default:
                Log.i("DRONE_EVENT", event);
                break;
        }

    }

    @Override
    public void onDroneConnectionFailed(ConnectionResult result) {
        Utilities.alertUser("Connection Failed:" + result.getErrorMessage(), getApplicationContext());
    }

    @Override
    public void onDroneServiceInterrupted(String errorMsg) {
        Utilities.alertUser("Service Interrupted:" + errorMsg, getApplicationContext());
    }


    public void onArmButtonTap(View view) {
        State vehicleState = this.drone.getAttribute(AttributeType.STATE);
        if (vehicleState.isFlying()) {
            // Land
            this.drone.changeVehicleMode(VehicleMode.COPTER_LAND);
        } else if (vehicleState.isArmed()) {
            // Take off
            this.drone.doGuidedTakeoff(10);
        } else if (!vehicleState.isConnected()) {
            // Connect
            Utilities.alertUser("Connect to a drone first", getApplicationContext());
        } else if (vehicleState.isConnected() && !vehicleState.isArmed()) {
            // Connected but not Armed
            this.drone.arm(true);
        }
    }

    public void onBtnConnectTap(View view) {
        if (this.drone.isConnected()) {
            this.drone.disconnect();
        } else {
            Bundle extraParams = new Bundle();
            Spinner connectionSelector = (Spinner) findViewById(R.id.selectConnectionType);
            int selectedConnectionType = connectionSelector.getSelectedItemPosition();

            if (selectedConnectionType == ConnectionType.TYPE_USB) {
                extraParams.putInt(ConnectionType.EXTRA_USB_BAUD_RATE, DEFAULT_USB_BAUD_RATE); // Set default baud rate to 57600
            } else {
                extraParams.putInt(ConnectionType.EXTRA_UDP_SERVER_PORT, DEFAULT_UDP_PORT); // Set default baud rate to 14550
            }
            ConnectionParameter connectionParams = new ConnectionParameter(selectedConnectionType, extraParams, null);
            this.drone.connect(connectionParams);
        }
    }

    // UI updating
    // ==========================================================

    protected void updateConnectedButton(Boolean isConnected) {
        ConnectEvent ev = new ConnectEvent();
        ev.setData(isConnected);
        DroneHUDApplication.busPost(ev);
    }

    protected void updateDroneState() {

        State vehicleState = this.drone.getAttribute(AttributeType.STATE);
        VehicleStateEvent ev = new VehicleStateEvent();
        ev.setData(vehicleState);
        DroneHUDApplication.busPost(ev);

    }

    protected void updateAltitude() {
        Altitude droneAltitude = this.drone.getAttribute(AttributeType.ALTITUDE);
        AltitudeEvent ev = new AltitudeEvent();
        ev.setData(droneAltitude.getAltitude());
        DroneHUDApplication.busPost(ev);
    }

    protected void updateSpeed() {
        Speed droneSpeed = this.drone.getAttribute(AttributeType.SPEED);
        GroundSpeedEvent grEv = new GroundSpeedEvent();
        grEv.setData(droneSpeed.getGroundSpeed());
        DroneHUDApplication.busPost(grEv);
        AirSpeedEvent airEv = new AirSpeedEvent();
        airEv.setData(droneSpeed.getAirSpeed());
        DroneHUDApplication.busPost(airEv);
    }

    protected void updateDistanceFromHome() {

        Altitude droneAltitude = this.drone.getAttribute(AttributeType.ALTITUDE);
        double vehicleAltitude = droneAltitude.getAltitude();
        Gps droneGps = this.drone.getAttribute(AttributeType.GPS);
        LatLong vehiclePosition = droneGps.getPosition();

        double distanceFromHome = 0;

        if (droneGps.isValid()) {
            LatLongAlt vehicle3DPosition = new LatLongAlt(vehiclePosition.getLatitude(), vehiclePosition.getLongitude(), vehicleAltitude);
            Home droneHome = this.drone.getAttribute(AttributeType.HOME);
            distanceFromHome = Utilities.distanceBetweenPoints(droneHome.getCoordinate(), vehicle3DPosition);
        } else {
            distanceFromHome = 0;
        }

        DistanceFromHomeEvent ev = new DistanceFromHomeEvent();
        ev.setData(distanceFromHome);
        DroneHUDApplication.busPost(ev);
    }

    public void updateAttitude() {
        Attitude attitude = this.drone.getAttribute(AttributeType.ATTITUDE);
        AttitudeUpdateEvent attitudeEvent = new AttitudeUpdateEvent();
        attitudeEvent.setData(attitude);
        DroneHUDApplication.busPost(attitudeEvent);
    }

    public void updateBattery() {
        Battery battery = this.drone.getAttribute(AttributeType.BATTERY);
        BatteryUpdateEvent batEv = new BatteryUpdateEvent();
        batEv.setData(battery.getBatteryVoltage());
        DroneHUDApplication.busPost(batEv);
    }

    public void updateGpsFix() {
        Gps droneGps = this.drone.getAttribute(AttributeType.GPS);
        GpsFixEvent gpsEv = new GpsFixEvent();
        gpsEv.setData(droneGps.getFixStatus());
        DroneHUDApplication.busPost(gpsEv);
    }

    // UI Telemetry Events
    // ==========================================================
    @Override
    public void onTelemetryFragmentInteraction(VehicleMode vehicleMode) {
        this.drone.changeVehicleMode(vehicleMode);
    }


    // UI HUD Events
    // ==========================================================

    @Override
    public void onHUDFragmentInteraction(Uri uri) {

    }
}
