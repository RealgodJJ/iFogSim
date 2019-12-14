package org.fog.utils;

import org.cloudbus.cloudsim.power.models.PowerModel;

public class FogQuadraticPowerModel implements PowerModel {
    /**
     * The max power.
     */
    private double maxPower;

    /**
     * The quadratic constant.
     */
    private double quadraticConstant;

//    /**
//     * The linear constant.
//     */
//    private double linearConstant;

    /**
     * The static power.
     */
    private double staticPower;

    /**
     * Instantiates a new linear power model.
     *
     * @param maxPower    the max power
     * @param staticPower the static power
     */
    public FogQuadraticPowerModel(double maxPower, double staticPower) {
        setMaxPower(maxPower);
        setStaticPower(staticPower);
        setQuadraticConstant((getMaxPower() - getStaticPower()) / 10000);
//        setLinearConstant();
    }

    @Override
    public double getPower(double utilization) throws IllegalArgumentException {
        if (utilization < 0 || utilization > 1) {
            throw new IllegalArgumentException("Utilization value must be between 0 and 1");
        }
        return getStaticPower() + getQuadraticConstant() * Math.pow(utilization * 100, 2);
    }

    public double getMaxPower() {
        return maxPower;
    }

    private void setMaxPower(double maxPower) {
        this.maxPower = maxPower;
    }

    public double getQuadraticConstant() {
        return quadraticConstant;
    }

    public void setQuadraticConstant(double quadraticConstant) {
        this.quadraticConstant = quadraticConstant;
    }

//    public double getLinearConstant() {
//        return linearConstant;
//    }

//    public void setLinearConstant(double linearConstant) {
//        this.linearConstant = linearConstant;
//    }

    public double getStaticPower() {
        return staticPower;
    }

    public void setStaticPower(double staticPower) {
        this.staticPower = staticPower;
    }
}
