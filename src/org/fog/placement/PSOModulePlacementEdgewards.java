package org.fog.placement;

import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.application.AppEdge;
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
    private List<List<Integer>> leafToRootPaths = new ArrayList<>();
    //TODO: 创建基本构造方法
    private ModuleMapping moduleMapping;
    private double totalEnergyConsumption;
    private int P = 20;    // 设置粒子数的范围是[10, 20]
    private int T = 40;    // 设置迭代的次数是40
    private double W = 0.9, Wmax = 0.9, Wmin = 0.4;    //设置初始惯性因子（W = (wmax - wmin) / Tmax * t）
    private int Y1 = 2, Y2 = 2; //设置加速常数
    private List<FogDevice> fogDevices = new ArrayList<>();
    private List<AppModule> appModules = new ArrayList<>();
    private int numOfService = 0, numOfDevice = 0;    // 确定总设备的数量和总模块的数量
    private List<double[][]> vList = new ArrayList<>();
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
//        for (int fogDeviceId : getCurrentModuleMap().keySet()) {
//            fogDevices.add(fogDeviceId);
//            numOfDevice++;
//        }
        List<Integer> fogDeviceIds = new ArrayList<>();
        for (FogDevice fogDevice : getFogDevices()) {
            fogDeviceIds.add(fogDevice.getId());
            numOfDevice++;
        }

        for (Application application : getApplicationList()) {
            for (AppModule appModule : application.getModules()) {
                appModules.add(appModule);
                numOfService++;
            }
        }

        //（2）将设备集合进行排序
        Collections.reverse(fogDeviceIds);

        for (int fogDeviceId : fogDeviceIds)
            fogDevices.add((FogDevice) CloudSim.getEntity(fogDeviceId));
    }

    @Override
    protected void mapModules() {
        //获取所有的从树根到树叶的设备连接路径
        leafToRootPaths = getLeafToRootPaths();

        //(1) 对粒子种群进行初始化地设置（先将设备和模块的映射放置好）
        initPopulation();

//        for (List<Integer> path : leafToRootPaths) {
////            placeModulesInPath(path);
////        }

        //TODO：以下的（2）和（3）循环执行
        //(2) 对粒子种群中的所有粒子的概率矩阵来放置设备和模块之间的映射，同时更新整体系统架构的能量
        moduleToDevicePlacementForEstimation(0);

        //(3)进行不断放置概率的更迭
        changeProbabilityFromEnergyConsumption();


        //(4)按照粒子群决定moduleMapping的样子
        initModulePlacement();
//        estimateEnergyConsumption();
    }

    private void changeProbabilityFromEnergyConsumption() {
        //TODO:
        //(1)取出当前迭代下邻居粒子的最好结果Nbk
        //(2)找出截至当前迭代下的最好的结果Pbk
        //(3)计算vk(i,j)
        //(4)
    }

    private void moduleToDevicePlacementForEstimation(int time) {
        ModuleMapping currentModuleMapping = ModuleMapping.createModuleMapping();

        //统计4层架构中每层的模块最大放置数
        int[] maxDeviceNum = new int[4];
        int[] maxDevicePlacementNum = new int[4];
        double[] totalEnergyConsumption = new double[vList.size()];   //记录12个粒子的系统能耗

        for (int k = 0; k < vList.size(); k++) {
            for (int i = 0; i < numOfService; i++) {
                updateMaxDevicePlacement(maxDeviceNum, maxDevicePlacementNum);
                Map<Integer, Double> resultOfFogDevice = new TreeMap<>();   //按资源节点位置从小到大存储<资源节点位置，概率>键值对
                Map<Integer, Boolean> canBePlace = new HashMap<>();    //用于存储每个leafToRootPath上是否布置应用模块
                for (int j = 0; j < numOfDevice; j++) {
                    resultOfFogDevice.put(j, vList.get(k)[i][j]);
                    canBePlace.put(fogDevices.get(j).getId(), true);
                }
                //保存每一轮迭代之后的<边缘设备位置，模块>映射概率（由大到小排序）
                resultOfFogDevice = sortByValueDescending(resultOfFogDevice);

                //TODO: 更新并保存当前轮次的ModuleMapping
                boolean isK0 = time == 0;
                roundCurrentModuleMapping(currentModuleMapping, maxDevicePlacementNum, i, resultOfFogDevice, canBePlace, isK0);
            }

            //TODO: 计算当前粒子下，整个系统完成单元任务的总功耗
            roundEnergyConsumption(currentModuleMapping, totalEnergyConsumption, k);
            currentModuleMapping.getModuleMapping().clear();    //清空当前粒子的currentModuleMapping
        }
        //TODO: 开始计算下一轮的vi
        //（1）寻找本轮最优的分配方案Nk
        double[] neighborEnergyConsumption = new double[vList.size()];
        for (int k = 0; k < vList.size(); k++) {
            if (k == 0)
                neighborEnergyConsumption[k] = Math.min(totalEnergyConsumption[vList.size() - 1], totalEnergyConsumption[k + 1]);
            else if (k == vList.size() - 1)
                neighborEnergyConsumption[k] = Math.min(totalEnergyConsumption[k - 1], totalEnergyConsumption[0]);
            else
                neighborEnergyConsumption[k] = Math.min(totalEnergyConsumption[k - 1], totalEnergyConsumption[k + 1]);
        }

        //（2）寻找直至本轮最优的分配方案Pk
        double minEnergyConsumption = totalEnergyConsumption[0];
        for (int k = 1; k < vList.size(); k++) {
            minEnergyConsumption = Math.min(totalEnergyConsumption[k], minEnergyConsumption);
        }

        //（3）计算下一轮vk的第t+1轮概率矩阵
        W = Wmax - (Wmax - Wmin) / T * (time + 1);
        for (int k = 0; k < vList.size(); k++) {
            for (int i = 0; i < numOfService; i++) {
                for (int j = 0; j < numOfDevice; j++) {
                    Random random = new Random();
                    double pBest = Y1 * random.nextDouble() * (minEnergyConsumption - totalEnergyConsumption[k]);
                    double nBest = Y2 * random.nextDouble() * (neighborEnergyConsumption[k] - totalEnergyConsumption[k]);
                    vList.get(k)[i][j] = W * vList.get(k)[i][j] + pBest + nBest;
                }
            }
        }
        System.out.println();
    }

    //更新每一个应用模块在每层设备上的最大放置数量
    private void updateMaxDevicePlacement(int[] maxDeviceNum, int[] maxDevicePlacementNum) {
        for (int i = 0; i < maxDeviceNum.length; i++)
            maxDeviceNum[i] = 0;

        for (FogDevice fogDevice : fogDevices) {
            String name = fogDevice.getName();
            if (!(name.startsWith("m") || name.startsWith("p")))
                maxDeviceNum[fogDevice.getLevel()]++;
            else
                maxDeviceNum[fogDevice.getLevel()] = 1;
        }

        //TODO: 此处返回模块放置概率最大的设备所需数量，返回最大概率的几个数组位置（对应每个fogDevice设备）
        //选取最大概率的几个可放置设备，设置同种模块在相邻节点之间只放置同层总设备数量的一半（向上取整）
        for (int i = 0; i < maxDeviceNum.length; i++)
            maxDevicePlacementNum[i] = (int) Math.ceil(((double) maxDeviceNum[i] + 1) / 2);
    }

    private void roundCurrentModuleMapping(ModuleMapping currentModuleMapping, int[] maxDevicePlacementNum, int i,
                                           Map<Integer, Double> resultOfFogDevice, Map<Integer, Boolean> canBePlace, boolean isK0) {
        for (Map.Entry<Integer, Double> entry : resultOfFogDevice.entrySet()) {
            System.out.println("key= " + entry.getKey() + " and value= " + entry.getValue());

            //获取当前设备、当前设备的邻居节点数量、当前设备的邻居节点Id号
            FogDevice currentFogDevice = fogDevices.get(entry.getKey());
            int neighborNum = fogDevices.get(entry.getKey()).getNeighborIds().size();

            //概率值从大到小排布，一旦低于0.5，直接退出当前应用模块的循环
            if (entry.getValue() < 0.5)
                break;

            if (canBePlace.get(currentFogDevice.getId()) && entry.getValue() >= 0.5) {
                if (neighborNum != 0 && maxDevicePlacementNum[currentFogDevice.getLevel()] != 0) {
                    //存在邻居节点的边缘节点
                    //TODO：只设置了当前节点往上的节点不能够放置该模块，从底层向上遍历所以应该会全部考虑到
                    currentModuleMapping.addModuleToDevice(appModules.get(i).getName(), currentFogDevice.getName());
                    canBePlaceInFogDevice(canBePlace, currentFogDevice, isK0);
                    maxDevicePlacementNum[currentFogDevice.getLevel()]--;
                    //TODO：邻居节点的模块放置安排
                } else if (maxDevicePlacementNum[currentFogDevice.getLevel()] != 0 &&
                        currentFogDevice.getLevel() == 0) {
                    //无邻居节点的底层节点
                    //TODO：只设置了当前节点往上的节点不能够放置该模块，从底层向上遍历所以应该会全部考虑到
                    currentModuleMapping.addModuleToDevice(appModules.get(i).getName(), currentFogDevice.getName());
                    canBePlaceInFogDevice(canBePlace, currentFogDevice, isK0);
                } else if (currentFogDevice.getLevel() == 2 || currentFogDevice.getLevel() == 3) {    //该资源节点是云服务器或是总代理
                    //TODO：只设置了当前节点往上的节点不能够放置该模块，从底层向上遍历所以应该会全部考虑到
                    currentModuleMapping.addModuleToDevice(appModules.get(i).getName(), currentFogDevice.getName());
                    canBePlaceInFogDevice(canBePlace, currentFogDevice, isK0);
                }
            }
        }
    }

    private void roundEnergyConsumption(ModuleMapping currentModuleMapping, double[] totalEnergyConsumption, int round) {
        double deviceEnergyConsumption = 0;
        for (Map.Entry<String, List<String>> deviceToModules : currentModuleMapping.getModuleMapping().entrySet()) {
            String deviceName = deviceToModules.getKey();
            List<String> moduleNames = deviceToModules.getValue();
            List<AppModule> appModules = new ArrayList<>();

            FogDevice fogDevice = (FogDevice) CloudSim.getEntity(deviceName);
            int totalMips = fogDevice.getHost().getTotalMips();

            for (String moduleName : moduleNames) {
                AppModule appModule;
                if (getApplicationList().get(0).hasThisAppModule(moduleName))
                    appModule = getApplicationList().get(0).getModuleByName(moduleName);
                else
                    appModule = getApplicationList().get(1).getModuleByName(moduleName);

                appModules.add(appModule);
            }

            //TODO：应该先把当前设备上所有模块的利用率总和全部算出来
            double utilization = 0;
            for (AppModule appModule : appModules) {
                utilization += appModule.getMips() / totalMips;
            }

            //计算当前边缘设备上所有应用模块加起来的功耗
            for (AppModule appModule : appModules) {
                List<AppEdge> appEdges;
                //获取当前moduleName之前的AppEdge
                if (getApplicationList().get(0).hasThisAppModule(appModule.getName())) {
                    appEdges = getApplicationList().get(0).getAppEdge(appModule.getName());
                } else {
                    appEdges = getApplicationList().get(1).getAppEdge(appModule.getName());
                }

                for (AppEdge appEdge : appEdges) {
                    //应用模块采用时间分配策略，则宏观上采用以下方式
                    deviceEnergyConsumption += fogDevice.getHost().getPowerModel().
                            getPower(utilization) * appEdge.getTupleCpuLength() / totalMips;
//                        //应用模块采用空间分配策略，则宏观上采用以下方式
//                        deviceEnergyConsumption += fogDevice.getHost().getPowerModel().
//                                getPower(appModule.getMips() / fogDevice.getHost().getTotalMips()) *
//                                appEdge.getTupleCpuLength() / appModule.getMips();
                }
            }
//            //计算当前边缘设备上所有应用模块加起来的功耗
//            for (String moduleName : moduleNames) {
//                AppModule appModule;
//                List<AppEdge> appEdges;
//
//                //获取当前moduleName的AppModule和其之前的AppEdge
//                if (getApplicationList().get(0).hasThisAppModule(moduleName)) {
//                    appModule = getApplicationList().get(0).getModuleByName(moduleName);
//                    appEdges = getApplicationList().get(0).getAppEdge(moduleName);
//                } else {
//                    appModule = getApplicationList().get(1).getModuleByName(moduleName);
//                    appEdges = getApplicationList().get(1).getAppEdge(moduleName);
//                }
//
//                for (AppEdge appEdge : appEdges) {
//                    //应用模块采用时间分配策略，则宏观上采用以下方式
//                    utilization = appModule.getMips() / fogDevice.getHost().getTotalMips();
//                    deviceEnergyConsumption += fogDevice.getHost().getPowerModel().
//                            getPower(utilization) * appEdge.getTupleCpuLength() / fogDevice.getHost().getTotalMips();
//                }
//            }
        }
        //设定每一轮所有粒子的预估总功耗
        totalEnergyConsumption[round] = deviceEnergyConsumption;
    }

    private void canBePlaceInFogDevice(Map<Integer, Boolean> canBePlace, FogDevice currentFogDevice, boolean isK0) {
        //（1）如果底层边缘节点已经部署该模块，则上层节点无需再次布置
        canBePlaceInParent(canBePlace, currentFogDevice);

        //（2）如果是初始化种群，则采用高层资源节点放置模块，则底层节点不放置原则
        canBePlaceInChildren(canBePlace, currentFogDevice, isK0);
    }

    private void canBePlaceInParent(Map<Integer, Boolean> canBePlace, FogDevice forParent) {
        canBePlace.put(forParent.getId(), false);
        while (forParent.getParentId() != -1) {
            forParent = (FogDevice) CloudSim.getEntity(forParent.getParentId());
            canBePlace.put(forParent.getId(), false);
        }
    }

    private void canBePlaceInChildren(Map<Integer, Boolean> canBePlace, FogDevice currentFogDevice, boolean isK0) {
        if (isK0) {
            for (int childId : currentFogDevice.getChildrenIds()) {
                canBePlace.put(childId, false);
                FogDevice childFogDevice = (FogDevice) CloudSim.getEntity(childId);
                canBePlaceInChildren(canBePlace, childFogDevice, isK0);
            }
        }
    }

    private void initPopulation() {
        AppLoop appLoop1 = getApplicationList().get(0).getLoops().get(0);
        AppLoop appLoop2 = getApplicationList().get(1).getLoops().get(0);
//        List<AppModule> appModules1 = getApplicationList().get(0).getModules();
//        List<AppModule> appModules2 = getApplicationList().get(1).getModules();

        for (int k = 0; k < P; k++) {
            double[][] v = new double[numOfService][numOfDevice];
            double dmax = 0;
            for (int i = 0; i < numOfService; i++) {
                String moduleName = appModules.get(i).getName();
                for (int j = 0; j < numOfDevice; j++) {
//                    String deviceName = CloudSim.getEntityName(fogDevices.get(j));
                    String deviceName = fogDevices.get(j).getName();
                    if (deviceName.startsWith("m-")) {
                        if (appLoop1.isStartModule(moduleName)) {
                            //判断是否为开始模块
                            if (k % 10 <= 5)
                                v[i][j] = 1;
                            else if (k % 10 >= 6 && k % 10 <= 8) {
                                v[i][j] = 0.8;
//                                if ((fogDevices.get(j + 3).getName().startsWith("d")
//                                        || fogDevices.get(j + 2).getName().startsWith("d")))
//                                    v[i][j] = 0.8;
//                                else
//                                    v[i][j] = 1;
                            } else if (k % 10 == 9) {
                                v[i][j] = 0.6;
//                                if ((fogDevices.get(j + 2).getName().startsWith("d")
//                                        || fogDevices.get(j + 1).getName().startsWith("d")))
//                                    v[i][j] = 0.8;
//                                else
//                                    v[i][j] = 1;
                            }
                        } else if (moduleName.equals(appLoop1.getNextModuleInLoop(appLoop1.getStartModule()))) {
                            //判断是否为第二模块
                            if (k % 10 <= 2)
                                v[i][j] = 1;
                            else if (k % 10 == 3 || k % 10 == 4 || k % 10 == 6 || k % 10 == 7)
                                v[i][j] = 0.8;
                            else if (k % 10 == 5 || k % 10 == 8 || k % 10 == 9)
                                v[i][j] = 0.6;
                        } else if (appLoop1.hasModule(moduleName)) {
                            if (k % 10 == 0)
                                v[i][j] = 1;
                            else if (k % 10 == 1 || k % 10 == 3 || k % 10 == 6)
                                v[i][j] = 0.8;
                            else if (k % 10 == 2 || k % 10 == 4 || k % 10 == 5 || k % 10 == 7 || k % 10 == 8 || k % 10 == 9)
                                v[i][j] = 0.6;
                        }
                    } else if (deviceName.startsWith("p-")) {
                        if (appLoop2.isStartModule(moduleName)) {
                            v[i][j] = 1;
//                            else if (k % 10 >= 1 && k % 10 <= 3) {
//                                v[i][j] = 0.8;
//                                if ((fogDevices.get(j + 4).getName().startsWith("d")
//                                        || fogDevices.get(j + 3).getName().startsWith("d")))
//                                    v[i][j] = 0.8;
//                                else
//                                    v[i][j] = 1;
//                            } else if (k % 10 == 0) {
//                                v[i][j] = 0.6;
//                                if ((fogDevices.get(j + 3).getName().startsWith("d")
//                                        || fogDevices.get(j + 2).getName().startsWith("d")))
//                                    v[i][j] = 0.8;
//                                else
//                                    v[i][j] = 1;
//                            }
                        } else if (moduleName.equals(appLoop2.getNextModuleInLoop(appLoop2.getStartModule()))) {
                            if (k % 6 >= 3)
                                v[i][j] = 1;
                            else if (k % 6 >= 1 && k % 6 <= 2)
                                v[i][j] = 0.8;
                            else if (k % 6 == 0)
                                v[i][j] = 0.6;
                        } else if (appLoop2.hasModule(moduleName)) {
                            if (k % 6 == 5)
                                v[i][j] = 1;
                            else if (k % 6 == 2 || k % 6 == 4)
                                v[i][j] = 0.8;
                            else if (k % 6 == 0 || k % 6 == 1 || k % 6 == 3)
                                v[i][j] = 0.6;
                        }
                    } else if (deviceName.startsWith("d")) {
                        v[i][j] = v[i][j - 1] == 1.0 ? v[i][j - 1] - 0.2 : v[i][j - 1] + 0.2;
//                        v[i][j] = (v[i][j - 1] >= 0.2) ? (v[i][j - 1] - 0.2) : 0;
                        if (v[i][j - 1] == 0)
                            v[i][j] = 0;
                        if (v[i][j] > dmax)
                            dmax = v[i][j];
                    }

                    if (deviceName.equals("proxy-server") && !moduleName.equals("user_interface")) {
                        if (appLoop1.isStartModule(moduleName)) {
                            if (k % 10 <= 5)
                                v[i][j] = 0.6;
                            else if (k % 10 >= 6 && k % 10 <= 8)
                                v[i][j] = 0.8;
                            else if (k % 10 == 9)
                                v[i][j] = 1;
                        } else if (moduleName.equals(appLoop1.getNextModuleInLoop(appLoop1.getStartModule()))) {
                            if (k % 10 <= 2)
                                v[i][j] = 0.6;
                            else if (k % 10 == 3 || k % 10 == 4 || k % 10 == 6 || k % 10 == 7)
                                v[i][j] = 0.8;
                            else if (k % 10 == 5 || k % 10 == 8 || k % 10 == 9)
                                v[i][j] = 1;
                        } else if (appLoop1.hasModule(moduleName)) {
                            if (k % 10 == 0)
                                v[i][j] = 0.6;
                            else if (k % 10 == 1 || k % 10 == 3 || k % 10 == 6)
                                v[i][j] = 0.8;
                            else if (k % 10 == 2 || k % 10 == 4 || k % 10 == 5 || k % 10 == 7 || k % 10 == 8 || k % 10 == 9)
                                v[i][j] = 1;
                        }

                        if (appLoop2.isStartModule(moduleName)) {
                            v[i][j] = 0.6;
                        } else if (moduleName.equals(appLoop2.getNextModuleInLoop(appLoop2.getStartModule()))) {
                            if (k % 6 >= 3)
                                v[i][j] = 0.6;
                            else if (k % 6 >= 1 && k % 6 <= 2)
                                v[i][j] = 0.8;
                            else if (k % 6 == 0)
                                v[i][j] = 1;
                        } else if (appLoop2.hasModule(moduleName)) {
                            if (k % 6 == 5)
                                v[i][j] = 0.6;
                            else if (k % 6 == 2 || k % 6 == 4)
                                v[i][j] = 0.8;
                            else if (k % 6 == 0 || k % 6 == 1 || k % 6 == 3)
                                v[i][j] = 1;
                        }
                        dmax = 0;
                    }

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

    //降序排序
    private static <K, V extends Comparable<? super V>> Map<K, V> sortByValueDescending(Map<K, V> map) {
        //将Map放入到List中，利用List的排序方法对value进行排序
        List<Map.Entry<K, V>> list = new ArrayList<>(map.entrySet());
        list.sort((o1, o2) -> o2.getValue().compareTo(o1.getValue()));

        //使用LinkedHashMap保证按照插入的顺序排列
        Map<K, V> result = new LinkedHashMap<>();
        for (Map.Entry<K, V> entry : list) {
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }
}
