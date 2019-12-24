package org.fog.placement;

import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.application.AppLoop;
import org.fog.application.AppModule;
import org.fog.application.Application;
import org.fog.entities.Actuator;
import org.fog.entities.FogDevice;
import org.fog.entities.Sensor;

import java.util.*;

public class PSOModulePlacementEdgewards extends ModulePlacementEdgewards {
    /* DCNSFog测试实例最初设定将所有的设备放到最底层的边缘服务器进行执行，以减少传输功耗和延时
    但是实际情况下，边缘节点不能容纳放置很多的应用模块 */
    List<List<Integer>> leafToRootPaths = new ArrayList<>();
    //TODO: 创建基本构造方法
    private double totalEnergyConsumption;
    int P = 10;    // 设置粒子数的范围是[10, 20]
    int T = 40;    // 设置迭代的次数是40
    double w = 0.9;    //设置初始惯性因子（w = (wmax - wmin) / Tmax * t）
    List<Integer> fogDeviceIds = new ArrayList<>();
    List<AppModule> appModules = new ArrayList<>();
    int numOfService = 0, numOfDevice = 0;    // 确定总设备的数量和总模块的数量
    //    double[][] v;
    List<double[][]> vList = new ArrayList<>();
    private List<Application> applicationList = new ArrayList<>(); //将所有的应用全部考虑在模块放置策略中

    public PSOModulePlacementEdgewards(List<FogDevice> fogDevices, List<Sensor> sensors, List<Actuator> actuators,
                                       List<Application> applications, ModuleMapping moduleMapping) {
        this.setApplication(applications.get(0));
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
            numOfDevice++;
//            List<String> appModulesInFogDevice = getCurrentModuleMap().get(fogDeviceId);
//            for (String appModule : appModulesInFogDevice) {
//                if (!appModules.contains(appModule)) {
//                    appModules.add(appModule);
//                }
//            }
        }

        for (Application application : getApplicationList()) {
            for (AppModule appModule : application.getModules()) {
                appModules.add(appModule);
                numOfService++;
            }
        }

        //（2）将设备集合进行排序
        Collections.sort(fogDeviceIds);
        Collections.reverse(fogDeviceIds);
    }

    @Override
    protected void mapModules() {
        //(1)按照初始设定的moduleMapping
        initModulePlacement();

        //获取所有的从树根到树叶的设备连接路径
        leafToRootPaths = getLeafToRootPaths();

        //对粒子种群进行初始化地设置（先将设备和模块的映射放置好）
        initPopulation();

        for (List<Integer> path : leafToRootPaths) {
            placeModulesInPath(path);
        }

        //TODO: 更新整体系统架构的能量
        estimateEnergyConsumption();
    }

    private void initPopulation() {
        double[][] v = new double[numOfDevice][numOfService];
        AppLoop appLoop1 = getApplicationList().get(0).getLoops().get(0);
        AppLoop appLoop2 = getApplicationList().get(1).getLoops().get(0);
        List<AppModule> appModules1 = getApplicationList().get(0).getModules();
        List<AppModule> appModules2 = getApplicationList().get(1).getModules();

        for (int k = 0; k < P; k++) {
            double dmax = 0;
            for (int i = 0; i < numOfDevice; i++) {
                String deviceName = CloudSim.getEntityName(fogDeviceIds.get(i));
                for (int j = 0; j < numOfService; j++) {
                    String moduleName = appModules.get(j).getName();
                    if (deviceName.startsWith("m")) {
                        if (appLoop1.isStartModule(moduleName)) {
//                            if (appModules1.get(k).getName().equals(moduleName)) {
                            if (k % 3 == 0)
                                v[i][j] = 1;
                            else if (k % 3 == 1) {
                                if ((CloudSim.getEntityName(fogDeviceIds.get(i + 3)).startsWith("d")
                                        || CloudSim.getEntityName(fogDeviceIds.get(i + 2)).startsWith("d")))
                                    v[i][j] = 0.8;
                                else
                                    v[i][j] = 1;
                            } else if (k % 3 == 2) {
                                if ((CloudSim.getEntityName(fogDeviceIds.get(i + 2)).startsWith("d")
                                        || CloudSim.getEntityName(fogDeviceIds.get(i + 1)).startsWith("d")))
                                    v[i][j] = 0.8;
                                else
                                    v[i][j] = 1;
                            }
                        } else if (appLoop1.hasModule(moduleName)/*appModules1.contains(moduleName)*/) {
                            v[i][j] = v[i][j - 1] - 0.2;
                        }
                    } else if (deviceName.startsWith("n")) {
                        if (appLoop2.isStartModule(moduleName)) {
                            if (k % 4 == 0)
                                v[i][j] = 1;
                            else if (k % 4 == 1) {
                                if ((CloudSim.getEntityName(fogDeviceIds.get(i + 4)).startsWith("d")
                                        || CloudSim.getEntityName(fogDeviceIds.get(i + 3)).startsWith("d")))
                                    v[i][j] = 0.8;
                                else
                                    v[i][j] = 1;
                            } else if (k % 4 == 2) {
                                if ((CloudSim.getEntityName(fogDeviceIds.get(i + 3)).startsWith("d")
                                        || CloudSim.getEntityName(fogDeviceIds.get(i + 2)).startsWith("d")))
                                    v[i][j] = 0.8;
                                else
                                    v[i][j] = 1;
                            } else if (k % 4 == 3) {
                                if ((CloudSim.getEntityName(fogDeviceIds.get(i + 2)).startsWith("d")
                                        || CloudSim.getEntityName(fogDeviceIds.get(i + 1)).startsWith("d")))
                                    v[i][j] = 0.8;
                                else
                                    v[i][j] = 1;
                            }
                        } else if (appLoop2.hasModule(moduleName)) {
                            v[i][j] = v[i][j - 1] - 0.2;
                        }
                    } else if (deviceName.startsWith("d")) {
//                        CloudSim.getEntityName(fogDeviceIds.get(i - 1)).startsWith("m")
                        v[i][j] = (v[i - 1][j] >= 0.2) ? (v[i - 1][j] - 0.2) : 0;
                        if (v[i][j] > dmax)
                            dmax = v[i][j];
                    }

                    if (deviceName.equals("proxy-server"))
                        v[i][j] = dmax - 0.2;

                    //Application1的user_interface(AppModule)只放在cloud上
                    if (deviceName.equals("cloud") && moduleName.equals("user_interface"))
                        v[i][j] = 1;
                }
            }
            vList.add(v);
            System.out.println();
        }
        System.out.println();
    }

    private void estimateEnergyConsumption() {
        List<FogDevice> fogDevices = getFogDevices();
        for (FogDevice fogDevice : fogDevices) {
            //TODO: 调用FogDevice对象的更新功耗的对象（最好自己重新创建一个新的方法）传入模块与设备的映射
            totalEnergyConsumption += fogDevice.estimateEnergyConsumption(currentModuleMap.get(fogDevice.getId()), getApplication());
            System.out.println("totalEnergyConsumption: " + totalEnergyConsumption + getApplication().getAppId());
        }
    }

    public List<Application> getApplicationList() {
        return applicationList;
    }

    public void setApplicationList(List<Application> applicationList) {
        this.applicationList = applicationList;
    }
}
