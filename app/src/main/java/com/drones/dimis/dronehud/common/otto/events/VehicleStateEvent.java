package com.drones.dimis.dronehud.common.otto.events;


import com.drones.dimis.dronehud.common.otto.base.BaseOttoDataEvent;
import com.o3dr.services.android.lib.drone.property.State;

public class VehicleStateEvent extends BaseOttoDataEvent<State> {
    @Override
    public Class<State> getDataClassType(){
        return State.class;
    }
}
