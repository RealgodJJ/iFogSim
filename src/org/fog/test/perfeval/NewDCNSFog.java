package org.fog.test.perfeval;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.sdn.overbooking.BwProvisionerOverbooking;
import org.cloudbus.cloudsim.sdn.overbooking.PeProvisionerOverbooking;
import org.fog.application.AppEdge;
import org.fog.application.AppLoop;
import org.fog.application.Application;
import org.fog.application.selectivity.FractionalSelectivity;
import org.fog.entities.*;
import org.fog.placement.Controller;
import org.fog.placement.ModuleMapping;
import org.fog.placement.ModulePlacementMapping;
import org.fog.placement.PSOModulePlacementEdgewards;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.utils.FogQuadraticPowerModel;
import org.fog.utils.FogUtils;
import org.fog.utils.TimeKeeper;
import org.fog.utils.distribution.DeterministicDistribution;

import java.math.BigDecimal;
import java.util.*;

/**
 * Simulation setup for case study 2 - Intelligent Surveillance
 *
 * @author Harshit Gupta
 */
public class NewDCNSFog {
    private static List<FogDevice> fogDevices = new ArrayList<FogDevice>();
    private static List<Sensor> sensors = new ArrayList<Sensor>();
    private static List<Actuator> actuators = new ArrayList<Actuator>();
    private static List<FogDevice> areaList = new ArrayList<>();
    private static List<FogDevice> cameraList = new ArrayList<>();
    private static int numOfCameraAreas = 2;
    private static int numOfCamerasPerCameraArea = 3;
    private static int numOfCureAreas = 2;
    private static int numOfClientsPerCureArea = 4;


    //用于暂存每个边缘节点的邻居节点和延时之间的映射
    private static Map<Integer, Double> areaNeighborLatency;
    private static Map<Integer, Double> cameraNeighborLatency;

    private static boolean CLOUD = false;

    public static void main(String[] args) {

        Log.printLine("Starting DCNS...");
        Log.printLine("Starting CURE...");

        try {
            Log.disable();
            //1.初始化CloudSim工具包，它应该在创建任何实体之前调用
            int num_user = 1; // number of cloud users
            Calendar calendar = Calendar.getInstance();
            boolean trace_flag = false; // mean trace events
            CloudSim.init(num_user, calendar, trace_flag);


            //2.为应用设置名称并创建数据中心
            String appId1 = "dcns"; // identifier of the application
            String appId2 = "cure"; // identifier of the application
            FogBroker broker = new FogBroker("broker");

            //3.创建应用
            Application cameraApplication = createCameraApplication(appId1, broker.getId());
            cameraApplication.setUserId(broker.getId());
            Application clientApplication = createClientApplication(appId2, broker.getId());
            clientApplication.setUserId(broker.getId());

            //2.
            createFogDevicesForAll(broker.getId(), appId1, appId2);

            Controller controller = null;

            //3.
            ModuleMapping moduleMapping = ModuleMapping.createModuleMapping(); // initializing a module mapping
            ModuleMapping moduleMapping1 = ModuleMapping.createModuleMapping(); // initializing a module mapping
            if (!CLOUD) {
                for (FogDevice device : fogDevices) {
                    if (device.getName().startsWith("m")) { // names of all Smart Cameras start with 'm'
                        moduleMapping.addModuleToDevice("motion_detector", device.getName());  // fixing 1 instance of the Motion Detector module to each Smart Camera
//                    moduleMapping.addModuleToDevice("object_detector", device.getName());
//                    moduleMapping.addModuleToDevice("object_tracker", device.getName());
                    }

                    if (device.getName().startsWith("n")) {
                        moduleMapping1.addModuleToDevice("patient_client", device.getName());
                    }
//                    if (device.getName().startsWith("d-1") || device.getName().startsWith("d-3")) {
//                        moduleMapping1.addModuleToDevice("data_analysis", device.getName());
//                        moduleMapping1.addModuleToDevice("diagnostic_module", device.getName());
//                        moduleMapping1.addModuleToDevice("object_detector", device.getName());
//                        moduleMapping1.addModuleToDevice("object_tracker", device.getName());
//                    } else if (device.getName().startsWith("d-0") || device.getName().startsWith("d-2")) {
//                        moduleMapping.addModuleToDevice("data_analysis", device.getName());
//                        moduleMapping.addModuleToDevice("diagnostic_module", device.getName());
//                        moduleMapping.addModuleToDevice("object_detector", device.getName());
//                        moduleMapping.addModuleToDevice("object_tracker", device.getName());
//                    }
                    if (device.getName().startsWith("d")) {
                        moduleMapping1.addModuleToDevice("data_analysis", device.getName());
                        moduleMapping1.addModuleToDevice("diagnostic_module", device.getName());
                        moduleMapping.addModuleToDevice("object_detector", device.getName());
                        moduleMapping.addModuleToDevice("object_tracker", device.getName());
                    }

//                    if (device.getName().startsWith("proxy")) {
//                        moduleMapping1.addModuleToDevice("data_analysis", device.getName());
//                        moduleMapping.addModuleToDevice("object_tracker", device.getName());
//                    }
                }
            }
            moduleMapping.addModuleToDevice("user_interface", "cloud"); // fixing instances of User Interface module in the Cloud
            //TODO: 将所有的计算模块添加到云端，以保证有些任务需要在云端进行执行
//            moduleMapping.addModuleToDevice("motion_detector", "cloud");
//            moduleMapping.addModuleToDevice("object_detector", "cloud");
//            moduleMapping.addModuleToDevice("object_tracker", "cloud");
//            if (!CLOUD) {
            if (CLOUD) {
                // if the mode of deployment is cloud-based
                moduleMapping.addModuleToDevice("motion_detector", "cloud");
                moduleMapping.addModuleToDevice("object_detector", "cloud");
                moduleMapping.addModuleToDevice("object_tracker", "cloud");

                moduleMapping1.addModuleToDevice("patient_client", "cloud");
                moduleMapping1.addModuleToDevice("data_analysis", "cloud");
                moduleMapping1.addModuleToDevice("diagnostic_module", "cloud");
//                moduleMapping1.addModuleToDevice("", "cloud");
            }

            //4.
            controller = new Controller("master-controller", fogDevices, sensors, actuators);

//            controller.submitApplication(cameraApplication,
//                    (CLOUD) ? (new ModulePlacementMapping(fogDevices, cameraApplication, moduleMapping))
//                            : (new ModulePlacementEdgewards(fogDevices, sensors, actuators, cameraApplication, moduleMapping)));
//            controller.submitApplication(clientApplication,
//                    (CLOUD) ? (new ModulePlacementMapping(fogDevices, clientApplication, moduleMapping1))
//                            : (new ModulePlacementEdgewards(fogDevices, sensors, actuators, clientApplication, moduleMapping1)));

            List<Application> applicationList = new ArrayList<Application>() {{
                add(cameraApplication);
                add(clientApplication);
            }};

            controller.submitApplications(applicationList,
                    (CLOUD) ? (new ModulePlacementMapping(fogDevices, clientApplication, moduleMapping1))
                            : (new PSOModulePlacementEdgewards(fogDevices, sensors, actuators, applicationList, moduleMapping1)));

            //5.
            TimeKeeper.getInstance().setSimulationStartTime(Calendar.getInstance().getTimeInMillis());

            //6.
            CloudSim.startSimulation();

            //7.
            CloudSim.stopSimulation();

            Log.printLine("VRGame finished!");
        } catch (Exception e) {
            e.printStackTrace();
            Log.printLine("Unwanted errors happen");
        }
    }

    /**
     * Creates the fog devices in the physical topology of the simulation.
     *
     * @param userId
     * @param firstAppId
     */
    private static void createFogDevicesForAll(int userId, String firstAppId, String secondAppId) {
        FogDevice cloud = createFogDevice("cloud", 44800, 40000, 100, 10000, 3, 0.01, 16 * 103, 16 * 83.25);
        cloud.setParentId(-1);
        fogDevices.add(cloud);
        FogDevice proxy = createFogDevice("proxy-server", 14000, 4000, 10000, 10000, 2, 0.0, 107.339, 83.4333);
        proxy.setParentId(cloud.getId());
        proxy.setUplinkLatency(100); // latency of connection between proxy server and cloud is 100 ms
        fogDevices.add(proxy);

        long[] mips = {14000, 16000, 14000, 15000};
//        long[] mips = {20000, 20000, 20000, 20000};
//        long[] mips = {6000, 10000, 8000, 12000};
        for (int i = 0; i < numOfCameraAreas + numOfCureAreas; i++) {
            if (i % 2 == 0) {
                FogDevice cameraArea = addCameraArea(i + "", userId, firstAppId, proxy.getId(), mips[i]);
                areaList.add(cameraArea);
            } else {
                FogDevice clientArea = addClientArea(i + "", userId, secondAppId, proxy.getId(), mips[i]);
                areaList.add(clientArea);
            }
        }
//        for (int i = 0; i < numOfCameraAreas; i++) {
//            FogDevice cameraArea = addCameraArea(i + "", userId, firstAppId, proxy.getId(), mips);
//            areaList.add(cameraArea);
//        }
//
//        int mips = 20000;
//        for (int i = 0; i < numOfCureAreas; i++) {
//            FogDevice clientArea = addClientArea((numOfCameraAreas + i) + "", userId, secondAppId, proxy.getId(), mips);
//            areaList.add(clientArea);
//        }

        Map<String, List<FogDevice>> areaNeighborList = new HashMap<>();
        for (int i = 0; i < areaList.size(); i++) {
            String areaName = areaList.get(i).getName();
            List<FogDevice> neighborList = new ArrayList<>();
            List<Integer> neighborListId = new ArrayList<>();
            neighborList.addAll(areaList);
            neighborList.remove(i);
//            if (i != 0 && i != areaList.size() - 1) {
//                neighborList.add(areaList.get(i + 1));
//                neighborList.add(areaList.get(i - 1));
//            } else if (i == 0) {
//                neighborList.add(areaList.get(i + 1));
//                neighborList.add(areaList.get(areaList.size() - 1));
//            } else if (i == areaList.size() - 1) {
//                neighborList.add(areaList.get(0));
//                neighborList.add(areaList.get(i - 1));
//            }

            areaNeighborList.put(areaName, neighborList);

            for (FogDevice neighbor : neighborList) {
                neighborListId.add(neighbor.getId());
            }
            areaList.get(i).setNeighborIds(neighborListId);
        }

        for (int i = 0; i < areaList.size(); i++) {
            areaNeighborLatency = new HashMap<>();
            List<FogDevice> neighborList = areaNeighborList.get(areaList.get(i).getName());
            for (int j = 0; j < neighborList.size(); j++) {
                areaNeighborLatency.put(neighborList.get(j).getId(), 3.0);
//                areaNeighborLatency.put(neighborList.get(j).getId(), 1.0);
            }
            areaList.get(i).setNeighborLatency(areaNeighborLatency);
        }
    }

    private static FogDevice addCameraArea(String id, int userId, String appId, int parentId, long mips) {
        //邻居节点之间的传输带宽为8000
        FogDevice router = createFogDevice("d-" + id, mips, 4000, 10000, 10000,
                5000, 1, 0.0, 107.339, 83.4333);
        fogDevices.add(router);
        router.setUplinkLatency(10.0); // latency of connection between router and proxy server is 2 ms
//        router.setUplinkLatency(4.0); // latency of connection between router and proxy server is 2 ms
        for (int i = 0; i < numOfCamerasPerCameraArea; i++) {
            String cameraId = id + "-" + i;
            FogDevice camera = addCamera(cameraId, userId, appId, router.getId()); // adding a smart camera to the physical topology. Smart cameras have been modeled as fog devices as well.
            camera.setUplinkLatency(2); // latency of connection between camera and router is 2 ms
            fogDevices.add(camera);
            cameraList.add(camera);
            mips += 500;
        }
        //TODO:创建一个FogDevice的Id到FogDevice的List的映射 <fogDeviceId, List<neighborFogDevice>>
        Map<String, List<FogDevice>> cameraNeighborList = new HashMap<>();
        for (int i = 0; i < cameraList.size(); i++) {
            String nodeName = cameraList.get(i).getName();
            List<FogDevice> neighborList = new ArrayList<>();
            List<Integer> neighborListId = new ArrayList<>();
            neighborList.addAll(cameraList);
            neighborList.remove(i);
            cameraNeighborList.put(nodeName, neighborList);

            for (FogDevice neighbor : neighborList) {
                neighborListId.add(neighbor.getId());
            }
            cameraList.get(i).setNeighborIds(neighborListId);
        }

        //TODO: 创建邻居节点之间的延迟设置
        for (int i = 0; i < cameraList.size(); i++) {
            cameraNeighborLatency = new HashMap<>();
            //利用当前节点名称寻找邻居节点列表，遍历邻居节点
            List<FogDevice> neighborList = cameraNeighborList.get(cameraList.get(i).getName());
            for (int j = 0; j < neighborList.size(); j++) {
                double latency = Math.random() * 2;
                BigDecimal b = new BigDecimal(latency);
//                cameraNeighborLatency.put(j, b.setScale(1, BigDecimal.ROUND_HALF_UP).doubleValue());
//                cameraNeighborLatency.put(neighborList.get(j).getId(), b.setScale(1, BigDecimal.ROUND_HALF_UP).doubleValue());
                //暂且把边缘节点之间的延迟设置为固定值0.5
                cameraNeighborLatency.put(neighborList.get(j).getId(), 1.5);
//                cameraNeighborLatency.put(neighborList.get(j).getId(), 0.5);
            }
            cameraList.get(i).setNeighborLatency(cameraNeighborLatency);
        }

//        for (String cameraId : cameraNeighborList.keySet()) {
//            System.out.println("[" + cameraId + "]:");
//            for (int j = 0; j < cameraNeighborList.size() - 1; j++) {
//                System.out.println(cameraNeighborList.get(cameraId).get(j).getName() + ": "
//                        + cameraNeighborList.get(cameraId).get(j).getNeighborLatency().get(j));
//            }
//        }
        //同一个区域添加完成邻居节点的延迟之后需要进行列表的清空
        cameraList.clear();
        router.setParentId(parentId);
        return router;
    }

    private static FogDevice addClientArea(String id, int userId, String appId, int parentId, long mips) {
        FogDevice router = createFogDevice("d-" + id, mips, 5000, 10000, 10000,
                5000, 1, 0.0, 117.339, 93.4333);
        fogDevices.add(router);
        router.setUplinkLatency(4.0);
        for (int i = 0; i < numOfClientsPerCureArea; i++) {
            String clientId = id + "-" + i;
            FogDevice client = addClient(clientId, userId, appId, router.getId());
            client.setUplinkLatency(2);
            fogDevices.add(client);
        }

        router.setParentId(parentId);
        return router;
    }

    private static FogDevice addCamera(String id, int userId, String appId, int parentId) {
        FogDevice camera = createFogDevice("m-" + id, 5000, 1000, 10000, 10000,
                5000, 0, 0, 87.53, 82.44);
        camera.setParentId(parentId);
//        Sensor sensor = new Sensor("s-" + id, "CAMERA", userId, appId, new NormalDistribution(5, 1)); // inter-transmission time of camera (sensor) follows a deterministic distribution
        Sensor sensor = new Sensor("s-" + id, "CAMERA", userId, appId, new DeterministicDistribution(1)); // inter-transmission time of camera (sensor) follows a deterministic distribution
        sensors.add(sensor);
        Actuator ptz = new Actuator("ptz-" + id, userId, appId, "PTZ_CONTROL");
        actuators.add(ptz);
        sensor.setGatewayDeviceId(camera.getId());
        sensor.setLatency(1.0);  // latency of connection between camera (sensor) and the parent Smart Camera is 1 ms
        ptz.setGatewayDeviceId(camera.getId());
        ptz.setLatency(1.0);  // latency of connection between PTZ Control and the parent Smart Camera is 1 ms
        return camera;
    }

    private static FogDevice addClient(String id, int userId, String appId, int parentId) {
        FogDevice client = createFogDevice("n-" + id, 10000, 2000, 10000, 10000,
                0, 0, 98.56, 90.43);
        client.setParentId(parentId);
        Sensor sensor = new Sensor("b-" + id, "BG_VALUE", userId, appId, new DeterministicDistribution(2));
        sensors.add(sensor);
        Actuator ct = new Actuator("ct-" + id, userId, appId, "CLIENT_TERMINAL");
        actuators.add(ct);
        sensor.setGatewayDeviceId(client.getId());
        sensor.setLatency(1.0);
        ct.setGatewayDeviceId(client.getId());
        ct.setLatency(1.0);
        return client;
    }

    /**
     * Creates a vanilla fog device
     *
     * @param nodeName    name of the device to be used in simulation
     * @param mips        MIPS
     * @param ram         RAM
     * @param upBw        uplink bandwidth
     * @param downBw      downlink bandwidth
     * @param level       hierarchy level of the device
     * @param ratePerMips cost rate per MIPS used
     * @param busyPower
     * @param idlePower
     * @return
     */
    //第一种调用方法
    private static FogDevice createFogDevice(String nodeName, long mips, int ram, long upBw, long downBw, int level,
                                             double ratePerMips, double busyPower, double idlePower) {
        List<Pe> peList = new ArrayList<Pe>();

        // 3. Create PEs and add these into a list.
        //TODO: 多创建几个Pe应该就可以使用了
//        peList.add(new Pe(0, new PeProvisionerOverbooking(mips))); // need to store Pe id and MIPS Rating
        for (int i = 0; i < 6; i++) {
            peList.add(new Pe(i, new PeProvisionerOverbooking(mips))); // need to store Pe id and MIPS Rating
        }

        int hostId = FogUtils.generateEntityId();
        long storage = 1000000; // host storage
        int bw = 10000;

        //相对于Host加入了功耗计算的模型
        PowerHost host = new PowerHost(hostId, new RamProvisionerSimple(ram), new BwProvisionerOverbooking(bw), storage,
                peList, new StreamOperatorScheduler(peList), new FogQuadraticPowerModel(busyPower, idlePower));

        //TODO: 应用模块使用空间分配策略
//        PowerHost host = new PowerHost(hostId, new RamProvisionerSimple(ram), new BwProvisionerOverbooking(bw), storage,
//                peList, new VmSchedulerSpaceShared(peList), new FogQuadraticPowerModel(busyPower, idlePower));

        List<Host> hostList = new ArrayList<Host>();
        hostList.add(host);

        String arch = "x86"; // system architecture
        String os = "Linux"; // operating system
        String vmm = "Xen";
        double time_zone = 10.0; // time zone this resource located
        double cost = 3.0; // the cost of using processing in this resource
        double costPerMem = 0.05; // the cost of using memory in this resource
        double costPerStorage = 0.001; // the cost of using storage in this
        // resource
        double costPerBw = 0.0; // the cost of using bw in this resource
        LinkedList<Storage> storageList = new LinkedList<Storage>(); // we are not adding SAN
        // devices by now

        FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(
                arch, os, vmm, host, time_zone, cost, costPerMem,
                costPerStorage, costPerBw);

        FogDevice fogdevice = null;
        try {
            fogdevice = new FogDevice(nodeName, characteristics,
                    new AppModuleAllocationPolicy(hostList), storageList, 10, upBw, downBw,
                    0, ratePerMips, level);
        } catch (Exception e) {
            e.printStackTrace();
        }

//        fogdevice.setLevel(level);
        return fogdevice;
    }

    //第二种调用方法（同层创建邻居节点）
    private static FogDevice createFogDevice(String nodeName, long mips, int ram, long upBw, long downBw,
                                             long neighborBw, int level, double ratePerMips, double busyPower,
                                             double idlePower) {
        List<Pe> peList = new ArrayList<Pe>();

        // 3. Create PEs and add these into a list.
//        peList.add(new Pe(0, new PeProvisionerOverbooking(mips))); // need to store Pe id and MIPS Rating
        for (int i = 0; i < 6; i++) {
            peList.add(new Pe(i, new PeProvisionerOverbooking(mips))); // need to store Pe id and MIPS Rating
        }

        int hostId = FogUtils.generateEntityId();
        long storage = 1000000; // host storage
        int bw = 10000;

        //相对于Host加入了功耗计算的模型
        PowerHost host = new PowerHost(hostId, new RamProvisionerSimple(ram), new BwProvisionerOverbooking(bw), storage,
                peList, new StreamOperatorScheduler(peList), new FogQuadraticPowerModel(busyPower, idlePower));

        //TODO: 应用模块使用空间分配策略
//        PowerHost host = new PowerHost(hostId, new RamProvisionerSimple(ram), new BwProvisionerOverbooking(bw), storage,
//                peList, new VmSchedulerSpaceShared(peList), new FogQuadraticPowerModel(busyPower, idlePower));


        List<Host> hostList = new ArrayList<Host>();
        hostList.add(host);

        String arch = "x86"; // system architecture
        String os = "Linux"; // operating system
        String vmm = "Xen";
        double time_zone = 10.0; // time zone this resource located
        double cost = 3.0; // the cost of using processing in this resource
        double costPerMem = 0.05; // the cost of using memory in this resource
        double costPerStorage = 0.001; // the cost of using storage in this
        // resource
        double costPerBw = 0.0; // the cost of using bw in this resource
        LinkedList<Storage> storageList = new LinkedList<Storage>(); // we are not adding SAN
        // devices by now

        FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(
                arch, os, vmm, host, time_zone, cost, costPerMem,
                costPerStorage, costPerBw);

        FogDevice fogdevice = null;
        //TODO：添加邻居节点之间的延迟
        Map<Integer, Double> myNeighborLatency = new HashMap<>();
        try {
            fogdevice = new FogDevice(nodeName, characteristics,
                    new AppModuleAllocationPolicy(hostList), storageList, 10, upBw, downBw, neighborBw,
                    0, myNeighborLatency, ratePerMips, level);
        } catch (Exception e) {
            e.printStackTrace();
        }

//        fogdevice.setLevel(level);
        return fogdevice;
    }

    /**
     * Function to create the Intelligent Surveillance application in the DDF model.
     *
     * @param appId  unique identifier of the application
     * @param userId identifier of the user of the application
     * @return
     */
    @SuppressWarnings({"serial"})
    private static Application createCameraApplication(String appId, int userId) {

        Application cameraApplication = Application.createApplication(appId, userId);

        /*
         * Adding modules (vertices) to the application model (directed graph)
         */
        //TODO: 在这里面改addAppModule的另一种方法
//        cameraApplication.addAppModule("motion_detector", 10);
        cameraApplication.addAppModule("motion_detector", 10, 1000 * 2, 10000, 1000);
//        cameraApplication.addAppModule("object_detector", 10);
        cameraApplication.addAppModule("object_detector", 10, 2000 * 2, 10000, 1000);
//        cameraApplication.addAppModule("object_tracker", 10);
        cameraApplication.addAppModule("object_tracker", 10, 1000 * 2, 10000, 1000);
//        cameraApplication.addAppModule("user_interface", 10);
        cameraApplication.addAppModule("user_interface", 10, 28 * 2, 10000, 1000);

        /*
         * Connecting the application modules (vertices) in the application model (directed graph) with edges
         */
        cameraApplication.addAppEdge("CAMERA", "motion_detector", 1000,
                20000, "CAMERA", Tuple.UP, AppEdge.SENSOR); // adding edge from CAMERA (sensor) to Motion Detector module carrying tuples of type CAMERA
        cameraApplication.addAppEdge("motion_detector", "object_detector", 2000,
                2000, "MOTION_VIDEO_STREAM", Tuple.UP, AppEdge.MODULE); // adding edge from Motion Detector to Object Detector module carrying tuples of type MOTION_VIDEO_STREAM
        cameraApplication.addAppEdge("object_detector", "user_interface", 500,
                2000, "DETECTED_OBJECT", Tuple.UP, AppEdge.MODULE); // adding edge from Object Detector to User Interface module carrying tuples of type DETECTED_OBJECT
        cameraApplication.addAppEdge("object_detector", "object_tracker", 1000,
                100, "OBJECT_LOCATION", Tuple.UP, AppEdge.MODULE); // adding edge from Object Detector to Object Tracker module carrying tuples of type OBJECT_LOCATION
        cameraApplication.addAppEdge("object_tracker", "PTZ_CONTROL", 100, 28,
                100, "PTZ_PARAMS", Tuple.DOWN, AppEdge.ACTUATOR); // adding edge from Object Tracker to PTZ CONTROL (actuator) carrying tuples of type PTZ_PARAMS

        /*
         * Defining the input-output relationships (represented by selectivity) of the application modules.
         */
        // 1.0 tuples of type MOTION_VIDEO_STREAM are emitted by Motion Detector module per incoming tuple of type CAMERA
        cameraApplication.addTupleMapping("motion_detector", "CAMERA", "MOTION_VIDEO_STREAM", new FractionalSelectivity(1.0));
        // 1.0 tuples of type OBJECT_LOCATION are emitted by Object Detector module per incoming tuple of type MOTION_VIDEO_STREAM
        cameraApplication.addTupleMapping("object_detector", "MOTION_VIDEO_STREAM", "OBJECT_LOCATION", new FractionalSelectivity(1.0));
        // 0.05 tuples of type MOTION_VIDEO_STREAM are emitted by Object Detector module per incoming tuple of type MOTION_VIDEO_STREAM
        cameraApplication.addTupleMapping("object_detector", "MOTION_VIDEO_STREAM", "DETECTED_OBJECT", new FractionalSelectivity(0.05));

        /*
         * Defining application loops (maybe incomplete loops) to monitor the latency of.
         * Here, we add two loops for monitoring : Motion Detector -> Object Detector -> Object Tracker and Object Tracker -> PTZ Control
         */
        final AppLoop cameraLoop1 = new AppLoop(new ArrayList<String>() {{
            add("motion_detector");
            add("object_detector");
            add("object_tracker");
        }});
        final AppLoop cameraLoop2 = new AppLoop(new ArrayList<String>() {{
            add("object_tracker");
            add("PTZ_CONTROL");
        }});
        List<AppLoop> cameraLoops = new ArrayList<AppLoop>() {{
            add(cameraLoop1);
            add(cameraLoop2);
        }};

        cameraApplication.setLoops(cameraLoops);
        return cameraApplication;
    }

    @SuppressWarnings({"serial"})
    private static Application createClientApplication(String appId, int userId) {
        Application clientApplication = Application.createApplication(appId, userId);

//        clientApplication.addAppModule("patient_client", 10);
        clientApplication.addAppModule("patient_client", 10, 5000 * 2, 10000, 1000);
//        clientApplication.addAppModule("data_analysis", 10);
        clientApplication.addAppModule("data_analysis", 10, 4000 * 2, 10000, 1000);
//        clientApplication.addAppModule("diagnostic_module", 10);
        clientApplication.addAppModule("diagnostic_module", 10, 1000 * 2, 10000, 1000);

        clientApplication.addAppEdge("BG_VALUE", "patient_client", 3000,
                3000, "BG_VALUE", Tuple.UP, AppEdge.SENSOR);
        clientApplication.addAppEdge("patient_client", "data_analysis", 4000,
                10000, "PATIENT_DATA", Tuple.UP, AppEdge.MODULE);
        clientApplication.addAppEdge("data_analysis", "diagnostic_module", 1000,
                5000, "SYMPTOMS_INFO", Tuple.UP, AppEdge.MODULE);
        clientApplication.addAppEdge("diagnostic_module", "patient_client", 2000,
                500, "DIAGNOSTIC_RESULT", Tuple.DOWN, AppEdge.MODULE);
        clientApplication.addAppEdge("patient_client", "CLIENT_TERMINAL", 500,
                100, 200, "VISUAL_RESULT", Tuple.DOWN, AppEdge.ACTUATOR);

        clientApplication.addTupleMapping("patient_client", "BG_VALUE", "PATIENT_DATA", new FractionalSelectivity(1.0));
        clientApplication.addTupleMapping("data_analysis", "PATIENT_DATA", "SYMPTOMS_INFO", new FractionalSelectivity(1.0));
        clientApplication.addTupleMapping("diagnostic_module", "SYMPTOMS_INFO", "DIAGNOSTIC_RESULT", new FractionalSelectivity(1.0));
        clientApplication.addTupleMapping("patient_client", "DIAGNOSTIC_RESULT", "VISUAL_RESULT", new FractionalSelectivity(0.5));

        final AppLoop clientLoop1 = new AppLoop(new ArrayList<String>() {{
            add("patient_client");
            add("data_analysis");
            add("diagnostic_module");
            add("patient_client");
        }});
        final AppLoop clientLoop2 = new AppLoop(new ArrayList<String>() {{
            add("patient_client");
            add("CLIENT_TERMINAL");
        }});
        List<AppLoop> clientLoops = new ArrayList<AppLoop>() {{
            add(clientLoop1);
            add(clientLoop2);
        }};

        clientApplication.setLoops(clientLoops);
        return clientApplication;
    }
}