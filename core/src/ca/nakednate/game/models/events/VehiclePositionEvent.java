package ca.nakednate.game.models.events;

import ca.nakednate.game.models.vehicle.Vehicle;

public class VehiclePositionEvent extends BaseEvent {

    float mX;
    float mY;
    float mHeading;
    float mPrimaryWeaponHeading;

    public VehiclePositionEvent(Vehicle vehicle) {
        mX = vehicle.getGroup().getX();
        mY = vehicle.getGroup().getY();
        mHeading = vehicle.getGroup().getRotation();
        mPrimaryWeaponHeading = vehicle.getWeapon().getRotation();
    }

    public float getX() {
        return mX;
    }

    public float getY() {
        return mY;
    }

    public float getHeading() {
        return mHeading;
    }

    public float getPrimaryWeaponHeading() {
        return mPrimaryWeaponHeading;
    }
}
