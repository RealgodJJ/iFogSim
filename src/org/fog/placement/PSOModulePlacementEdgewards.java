package org.fog.placement;

import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.application.AppModule;
import org.fog.application.Application;
import org.fog.entities.Actuator;
import org.fog.entities.FogDevice;
import org.fog.entities.Sensor;

import java.util.*;

public class PSOModulePlacementEdgewards extends ModulePlacementEdgewards {
    /* DCNSFog测试实例最初设定将所有的设备放到最底层的边缘服务器进行执行，以减少传输功耗和延时
    但是实际情况下，边缘节点不能容纳放置很多的应用模块 */
    //TODO: 创建基本构造方法

    int P = 10;    // 设置粒子数的范围是[10, 20]
    int T = 40;    // 设置迭代的次数是40
    List<Integer> fogDeviceIds = new ArrayList<>();
    List<String> appModules = new ArrayList<>();
    int numOfService = 0, numOfDevice = 0;    // 确定总设备的数量和总模块的数量
    double[][] v;
    private List<Application> applicationList = new ArrayList<>(); //将所有的应用全部考虑在模块放置策略中

    public PSOModulePlacementEdgewards(List<FogDevice> fogDevices, List<Sensor> sensors, List<Actuator> actuators,
                                       List<Application> applications, ModuleMapping moduleMapping) {
        this.setApplicationList(applications);
        this.setFogDevices(fogDevices);
        this.setModuleMapping(moduleMapping);
        this.setModuleToDeviceMap(new HashMap<String, List<Integer>>());
        this.setDeviceToModuleMap(new HashMap<Integer, List<AppModule>>());
        setSensors(sensors);
        setActuators(actuators);
        setCurrentCpuLoad(new HashMap<Integer, Double>());
        setCurrentModuleMap(new HashMap<Integer, List<String>>());
        setCurrentModuleLoadMap(new HashMap<Integer, Map<String, Double>>());
        setCurrentModuleInstanceNum(new HashMap<Integer, Map<String, Integer>>());
        for (FogDevice dev : getFogDevices()) {
            getCurrentCpuLoad().put(dev.getId(), 0.0);
            getCurrentModuleLoadMap().put(dev.getId(), new HashMap<String, Double>());
            getCurrentModuleMap().put(dev.getId(), new ArrayList<String>());
            getCurrentModuleInstanceNum().put(dev.getId(), new HashMap<String, Integer>());
        }

        //按顺序初始化设备和应用模块的矩阵
        initVelocityMatrix();

        //模块放置——粒子群算法策略
        mapModules();
    }

    private void initVelocityMatrix() {
        //（1）形成设备集合和应用模块集合
        for (int fogDeviceId : getCurrentModuleMap().keySet()) {
            fogDeviceIds.add(fogDeviceId);

            List<String> appModulesInFogDevice = getCurrentModuleMap().get(fogDeviceId);
            for (String appModule : appModulesInFogDevice) {
                if (!appModules.contains(appModule)) {
                    appModules.add(appModule);
                }
            }
        }

        for (Application application : getApplicationList()) {
            for (AppModule appModule : application.getModules()) {
                appModules.add(appModule.getName());
            }
        }


        //（2）将设备集合进行排序
        Collections.sort(fogDeviceIds);
        Collections.reverse(fogDeviceIds);

        System.out.println();
    }

    @Override
    protected void mapModules() {
        //按照初始设定的moduleMapping，先将设备和模块的映射放置好
        initModulePlacement();

        //获取所有的从树根到树叶的设备连接路径
        List<List<Integer>> leafToRootPaths = getLeafToRootPaths();
        for (List<Integer> path : leafToRootPaths) {
            placeModulesInPath(path);
        }

        //对粒子种群进行初始化地设置
        initPopulation();
    }

    private void initPopulation() {
        v = new double[numOfService][numOfDevice];
        for (int k = 0; k < P; k++) {
            for (int i = 0; i < numOfService; i++) {

            }
        }
    }

    public List<Application> getApplicationList() {
        return applicationList;
    }

    public void setApplicationList(List<Application> applicationList) {
        this.applicationList = applicationList;
    }

    //    public ModuleMapping getModuleMapping() {
//        return moduleMapping;
//    }
//
//    public void setModuleMapping(ModuleMapping moduleMapping) {
//        this.moduleMapping = moduleMapping;
//    }
//
//    public List<Sensor> getSensors() {
//        return sensors;
//    }
//
//    public void setSensors(List<Sensor> sensors) {
//        this.sensors = sensors;
//    }
//
//    public List<Actuator> getActuators() {
//        return actuators;
//    }
//
//    public void setActuators(List<Actuator> actuators) {
//        this.actuators = actuators;
//    }
//
//    public Map<Integer, Double> getCurrentCpuLoad() {
//        return currentCpuLoad;
//    }
//
//    public void setCurrentCpuLoad(Map<Integer, Double> currentCpuLoad) {
//        this.currentCpuLoad = currentCpuLoad;
//    }
//
//    public Map<Integer, List<String>> getCurrentModuleMap() {
//        return currentModuleMap;
//    }
//
//    public void setCurrentModuleMap(Map<Integer, List<String>> currentModuleMap) {
//        this.currentModuleMap = currentModuleMap;
//    }
//
//    public Map<Integer, Map<String, Double>> getCurrentModuleLoadMap() {
//        return currentModuleLoadMap;
//    }
//
//    public void setCurrentModuleLoadMap(Map<Integer, Map<String, Double>> currentModuleLoadMap) {
//        this.currentModuleLoadMap = currentModuleLoadMap;
//    }
//
//    public Map<Integer, Map<String, Integer>> getCurrentModuleInstanceNum() {
//        return currentModuleInstanceNum;
//    }
//
//    public void setCurrentModuleInstanceNum(Map<Integer, Map<String, Integer>> currentModuleInstanceNum) {
//        this.currentModuleInstanceNum = currentModuleInstanceNum;
//    }
}
