package org.fog.application;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.math3.util.Pair;
import org.cloudbus.cloudsim.CloudletScheduler;
import org.cloudbus.cloudsim.power.PowerVm;
import org.fog.application.selectivity.SelectivityModel;
import org.fog.scheduler.TupleScheduler;
import org.fog.utils.FogUtils;

/**
 * Class representing an application module, the processing elements of the application model of iFogSim.
 *
 * @author Harshit Gupta
 */
public class AppModule extends PowerVm {

    private String name;
    private String appId;
    //执行下一步任务流的路径
    private Map<Pair<String, String>, SelectivityModel> selectivityMap;


    //TODO: 在该模块上执行的任务Tuple名称
    private String tupleType;

//    private TupleScheduler tupleScheduler;

    /**
     * A map from the AppModules sending tuples UP to this module to their instance IDs.
     * If a new instance ID is detected, the number of instances is incremented.
     */
    //模块(String)传入到该模块的次数(Integer)中
    private Map<String, List<Integer>> downInstanceIdsMaps;
    //单次传递的起点模块名称（String）与其传入到当前目的模块的Id列表（Integer）之间的映射
    private Map<String, List<Integer>> neighborInstanceIdsMaps;

    /**
     * Number of instances of this module
     */
    private int numInstances;

    /**
     * Mapping from tupleType emitted by this AppModule to Actuators subscribing to that tupleType
     */
    private Map<String, List<Integer>> actuatorSubscriptions;

    public AppModule(int id, String name, String appId, int userId, double mips, int ram, long bw, long size, String vmm,
                     CloudletScheduler cloudletScheduler, Map<Pair<String, String>, SelectivityModel> selectivityMap/*, int tolerantTime*/) {

        super(id, userId, mips, 2, ram, bw, size, 1, vmm, cloudletScheduler, 300);
        setName(name);
        setId(id);
        setAppId(appId);
        setUserId(userId);
        setUid(getUid(userId, id));
        setMips(mips);
        setNumberOfPes(2);
        setRam(ram);
        setBw(bw);
        setSize(size);
        setVmm(vmm);
        setCloudletScheduler(cloudletScheduler);
        setInMigration(false);
        setBeingInstantiated(true);
        setCurrentAllocatedBw(0);
        setCurrentAllocatedMips(null);
        setCurrentAllocatedRam(0);
        setCurrentAllocatedSize(0);
        setSelectivityMap(selectivityMap);
        setActuatorSubscriptions(new HashMap<String, List<Integer>>());
        setNumInstances(0);
        setDownInstanceIdsMaps(new HashMap<String, List<Integer>>());
        setNeighborInstanceIdsMaps(new HashMap<String, List<Integer>>());
//        setTolerantTime(tolerantTime);
    }

    public AppModule(AppModule operator) {
        //TODO：将pe单元数量变为5（原为1）
//        tupleScheduler = new TupleScheduler(operator.getMips(), 5);
        super(FogUtils.generateEntityId(), operator.getUserId(), operator.getMips(), 1, operator.getRam(),
                operator.getBw(), operator.getSize(), 1, operator.getVmm(),
                new TupleScheduler(operator.getMips(), 1), operator.getSchedulingInterval());
        setName(operator.getName());
        setAppId(operator.getAppId());
        setInMigration(false);
        setBeingInstantiated(true);
        setCurrentAllocatedBw(0);
        setCurrentAllocatedMips(null);
        setCurrentAllocatedRam(0);
        setCurrentAllocatedSize(0);
        setSelectivityMap(operator.getSelectivityMap());
        setDownInstanceIdsMaps(new HashMap<String, List<Integer>>());
        setNeighborInstanceIdsMaps(new HashMap<String, List<Integer>>());
    }

    public void subscribeActuator(int id, String tuplyType) {
        if (!getActuatorSubscriptions().containsKey(tuplyType))
            getActuatorSubscriptions().put(tuplyType, new ArrayList<Integer>());
        getActuatorSubscriptions().get(tuplyType).add(id);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<Pair<String, String>, SelectivityModel> getSelectivityMap() {
        return selectivityMap;
    }

    public void setSelectivityMap(Map<Pair<String, String>, SelectivityModel> selectivityMap) {
        this.selectivityMap = selectivityMap;
    }

//    public double getTolerantTime() {
//        return tolerantTime;
//    }
//
//    public void setTolerantTime(double tolerantTime) {
//        this.tolerantTime = tolerantTime;
//    }
//
//    public double getCalculateAmount() {
//        return calculateAmount;
//    }
//
//    public void setCalculateAmount(double calculateAmount) {
//        this.calculateAmount = calculateAmount;
//    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public Map<String, List<Integer>> getActuatorSubscriptions() {
        return actuatorSubscriptions;
    }

    public void setActuatorSubscriptions(Map<String, List<Integer>> actuatorSubscriptions) {
        this.actuatorSubscriptions = actuatorSubscriptions;
    }

    public Map<String, List<Integer>> getDownInstanceIdsMaps() {
        return downInstanceIdsMaps;
    }

    public void setDownInstanceIdsMaps(Map<String, List<Integer>> downInstanceIdsMaps) {
        this.downInstanceIdsMaps = downInstanceIdsMaps;
    }

    public Map<String, List<Integer>> getNeighborInstanceIdsMaps() {
        return neighborInstanceIdsMaps;
    }

    public void setNeighborInstanceIdsMaps(Map<String, List<Integer>> neighborInstanceIdsMaps) {
        this.neighborInstanceIdsMaps = neighborInstanceIdsMaps;
    }

    public int getNumInstances() {
        return numInstances;
    }

    public void setNumInstances(int numInstances) {
        this.numInstances = numInstances;
    }
}
