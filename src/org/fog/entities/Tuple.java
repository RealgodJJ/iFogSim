package org.fog.entities;

import java.util.HashMap;
import java.util.Map;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.UtilizationModel;
import org.cloudbus.cloudsim.core.CloudSim;

public class Tuple extends Cloudlet {

    //Direction分为以下四种
    public static final int UP = 1;
    public static final int DOWN = 2;
    public static final int ACTUATOR = 3;
    //TODO：添加邻居节点传输的方式
//	public static final int NEIGHBOR = 4;

    private String appId;

    private String tupleType;
    private String destModuleName;
    private String srcModuleName;
    private int actualTupleId;
    private int direction;
    private int actuatorId;
    private int sourceDeviceId;
    private int sourceModuleId;
    //TODO：任务是否向邻居节点传递任务
    private boolean isToNeighbor;
    private boolean isFromNeighbor;
    private int beginDeviceId;
    //TODO: 任务的容忍时间
    private double tolerantTime;
    //TODO: 任务生成的时间
    private double produceTime;
    //TODO: 任务剩余的时间
    private double remainTime;
    //TODO: 任务的目标节点
    private int targetId;

    /**
     * Map to keep track of which module instances has a tuple traversed.
     * <p>
     * Map from moduleName to vmId of a module instance
     */
    private Map<String, Integer> moduleCopyMap;

    public Tuple(String appId, int cloudletId, int direction, long cloudletLength, int pesNumber,
                 long cloudletFileSize, long cloudletOutputSize,
                 UtilizationModel utilizationModelCpu,
                 UtilizationModel utilizationModelRam,
                 UtilizationModel utilizationModelBw, double tolerantTime) {
        super(cloudletId, cloudletLength, pesNumber, cloudletFileSize,
                cloudletOutputSize, utilizationModelCpu, utilizationModelRam,
                utilizationModelBw);
        setAppId(appId);
        setDirection(direction);
        setSourceDeviceId(-1);
        setModuleCopyMap(new HashMap<String, Integer>());
        //TODO: 以下为新添加的Tuple属性
        //默认任务不向邻居节点传递
        setToNeighbor(false);
        //默认任务不是从邻居节点传递
        setFromNeighbor(false);
        //设置任务产生的时间
        setProduceTime(CloudSim.clock());
        //设置任务的容忍时间
        setTolerantTime(tolerantTime);
//        setTolerantTime(5000);
    }

    public Tuple(String appId, int cloudletId, int direction, long cloudletLength, int pesNumber,
                 long cloudletFileSize, long cloudletOutputSize,
                 UtilizationModel utilizationModelCpu,
                 UtilizationModel utilizationModelRam,
                 UtilizationModel utilizationModelBw) {
        super(cloudletId, cloudletLength, pesNumber, cloudletFileSize,
                cloudletOutputSize, utilizationModelCpu, utilizationModelRam,
                utilizationModelBw);
        setAppId(appId);
        setDirection(direction);
        setSourceDeviceId(-1);
        setModuleCopyMap(new HashMap<String, Integer>());
        //TODO: 以下为新添加的Tuple属性
        //默认任务不向邻居节点传递
        setToNeighbor(false);
        //默认任务不是从邻居节点传递
        setFromNeighbor(false);
        //设置任务产生的时间
        setProduceTime(CloudSim.clock());
        //设置任务的容忍时间
//        setTolerantTime(tolerantTime);
        setTolerantTime(5000);
    }

    public boolean isToNeighbor() {
        return isToNeighbor;
    }

    public void setToNeighbor(boolean toNeighbor) {
        isToNeighbor = toNeighbor;
    }

    public boolean isFromNeighbor() {
        return isFromNeighbor;
    }

    public void setFromNeighbor(boolean fromNeighbor) {
        isFromNeighbor = fromNeighbor;
    }

    public int getBeginDeviceId() {
        return beginDeviceId;
    }

    public void setBeginDeviceId(int beginDeviceId) {
        this.beginDeviceId = beginDeviceId;
    }

    public int getActualTupleId() {
        return actualTupleId;
    }

    public void setActualTupleId(int actualTupleId) {
        this.actualTupleId = actualTupleId;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getTupleType() {
        return tupleType;
    }

    public void setTupleType(String tupleType) {
        this.tupleType = tupleType;
    }

    public String getDestModuleName() {
        return destModuleName;
    }

    public void setDestModuleName(String destModuleName) {
        this.destModuleName = destModuleName;
    }

    public String getSrcModuleName() {
        return srcModuleName;
    }

    public void setSrcModuleName(String srcModuleName) {
        this.srcModuleName = srcModuleName;
    }

    public int getDirection() {
        return direction;
    }

    public void setDirection(int direction) {
        this.direction = direction;
    }

    public int getActuatorId() {
        return actuatorId;
    }

    public void setActuatorId(int actuatorId) {
        this.actuatorId = actuatorId;
    }

    public int getSourceDeviceId() {
        return sourceDeviceId;
    }

    public void setSourceDeviceId(int sourceDeviceId) {
        this.sourceDeviceId = sourceDeviceId;
    }

    public Map<String, Integer> getModuleCopyMap() {
        return moduleCopyMap;
    }

    public void setModuleCopyMap(Map<String, Integer> moduleCopyMap) {
        this.moduleCopyMap = moduleCopyMap;
    }

    public int getSourceModuleId() {
        return sourceModuleId;
    }

    public void setSourceModuleId(int sourceModuleId) {
        this.sourceModuleId = sourceModuleId;
    }

    public double getTolerantTime() {
        return tolerantTime;
    }

    public void setTolerantTime(double tolerantTime) {
        this.tolerantTime = tolerantTime;
    }


    public double getProduceTime() {
        return produceTime;
    }

    public void setProduceTime(double produceTime) {
        this.produceTime = produceTime;
    }

    public double getRemainTime() {
        return remainTime;
    }

    public void setRemainTime(double remainTime) {
        this.remainTime = remainTime;
    }

    public int getTargetId() {
        return targetId;
    }

    public void setTargetId(int targetId) {
        this.targetId = targetId;
    }

}
