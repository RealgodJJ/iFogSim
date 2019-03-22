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
import org.fog.placement.ModulePlacementEdgewards;
import org.fog.placement.ModulePlacementMapping;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.utils.FogLinearPowerModel;
import org.fog.utils.FogUtils;
import org.fog.utils.TimeKeeper;
import org.fog.utils.distribution.DeterministicDistribution;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

public class WeatherSim {
    static List<FogDevice> fogDevices = new ArrayList<FogDevice>();
    static List<Sensor> sensors = new ArrayList<Sensor>();
    static List<Actuator> actuators = new ArrayList<Actuator>();
    static int numOfAreas = 2;
    static int numOfCalculatorPerArea = 4;

    private static boolean CLOUD = false;

    public static void main(String[] args) {

        Log.printLine("Starting DCNS...");

        try {
            Log.disable();
            int num_user = 1; // number of cloud users
            Calendar calendar = Calendar.getInstance();
            boolean trace_flag = false; // mean trace events

            CloudSim.init(num_user, calendar, trace_flag);

            String appId = "weather_calculate"; // identifier of the application

            FogBroker fogBroker = new FogBroker("fog_broker");

            //1.
            //TODO
            Application application = createApplication(appId, fogBroker.getId());
            application.setUserId(fogBroker.getId());


            //2.
            createFogDevices(fogBroker.getId(), appId);

            //3.
            ModuleMapping moduleMapping = ModuleMapping.createModuleMapping(); // initializing a module mapping
            for (FogDevice device : fogDevices) {
                if (device.getName().startsWith("w")) { // names of all Smart Cameras start with 'w'
                    moduleMapping.addModuleToDevice("weather_detector", device.getName());  // fixing 1 instance of the Motion Detector module to each Smart Camera
                }
            }
            moduleMapping.addModuleToDevice("user_interface", "cloud"); // fixing instances of User Interface module in the Cloud
            if (CLOUD) {
                // if the mode of deployment is cloud-based
                moduleMapping.addModuleToDevice("humid_detector", "cloud"); // placing all instances of Object Detector module in the Cloud
//                moduleMapping.addModuleToDevice("temp_detector", "cloud"); // placing all instances of Object Tracker module in the Cloud
            }


            //4.
            Controller controller;
            controller = new Controller("master-controller", fogDevices, sensors,
                    actuators);

            controller.submitApplication(application,
                    (CLOUD) ? (new ModulePlacementMapping(fogDevices, application, moduleMapping))
                            : (new ModulePlacementEdgewards(fogDevices, sensors, actuators, application, moduleMapping)));

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
     * @param appId
     */
    private static void createFogDevices(int userId, String appId) {
        FogDevice cloud = createFogDevice("cloud", 44800, 40000, 100, 10000, 0, 0.01, 16 * 103, 16 * 83.25);
        Log.printLine();
        cloud.setParentId(-1);
        fogDevices.add(cloud);
        FogDevice proxy = createFogDevice("proxy-server", 2800, 4000, 10000, 10000, 1, 0.0, 107.339, 83.4333);
        proxy.setParentId(cloud.getId());
        proxy.setUplinkLatency(100); // latency of connection between proxy server and cloud is 100 ms
        fogDevices.add(proxy);
        for (int i = 0; i < numOfAreas; i++) {
            addArea(i + "", userId, appId, proxy.getId());
        }
    }

    private static FogDevice addArea(String id, int userId, String appId, int parentId) {
        FogDevice router = createFogDevice("d-" + id, 2800, 4000, 10000, 10000, 1, 0.0, 107.339, 83.4333);
        fogDevices.add(router);
        router.setUplinkLatency(2); // latency of connection between router and proxy server is 2 ms
        for (int i = 0; i < numOfCalculatorPerArea; i++) {
            String mobileId = id + "-" + i;
            FogDevice edgeDevice = addDevice(mobileId, userId, appId, router.getId()); // adding a smart camera to the physical topology. Smart cameras have been modeled as fog devices as well.
            edgeDevice.setUplinkLatency(2); // latency of connection between camera and router is 2 ms
            fogDevices.add(edgeDevice);
        }
        router.setParentId(parentId);
        return router;
    }

    private static FogDevice addDevice(String id, int userId, String appId, int parentId) {
        FogDevice edgeDevice = createFogDevice("w-" + id, 500, 1000, 10000, 10000, 3, 0, 87.53, 82.44);
        edgeDevice.setParentId(parentId);
//        Sensor tempSensor = new Sensor("s-" + id, "TEMP", userId, appId, new DeterministicDistribution(5)); // inter-transmission time of camera (sensor) follows a deterministic distribution
//        sensors.add(tempSensor);
        Sensor humidSensor = new Sensor("s-" + id, "FOGDEVICE", userId, appId, new DeterministicDistribution(5)); // inter-transmission time of camera (sensor) follows a deterministic distribution
        sensors.add(humidSensor);
        Actuator calculate = new Actuator("act-" + id, userId, appId, "ACT_CALCULATE");
        actuators.add(calculate);
//        tempSensor.setGatewayDeviceId(edgeDevice.getId());
        humidSensor.setGatewayDeviceId(edgeDevice.getId());
//        tempSensor.setLatency(1.0);  // latency of connection between tempSensor and the parent edgeDevice is 1 ms
        humidSensor.setLatency(1.0);  // latency of connection between humidSensor and the parent edgeDevice is 1 ms
        calculate.setGatewayDeviceId(edgeDevice.getId());
        calculate.setLatency(1.0);  // latency of connection between PTZ Control and the parent Smart Camera is 1 ms
        return edgeDevice;
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
    private static FogDevice createFogDevice(String nodeName, long mips,
                                             int ram, long upBw, long downBw, int level, double ratePerMips, double busyPower, double idlePower) {

        List<Pe> peList = new ArrayList<Pe>();

        // 3. Create PEs and add these into a list.
        peList.add(new Pe(0, new PeProvisionerOverbooking(mips))); // need to store Pe id and MIPS Rating

        int hostId = FogUtils.generateEntityId();
        long storage = 1000000; // host storage
        int bw = 10000;

        PowerHost host = new PowerHost(
                hostId,
                new RamProvisionerSimple(ram),
                new BwProvisionerOverbooking(bw),
                storage,
                peList,
                new StreamOperatorScheduler(peList),
                new FogLinearPowerModel(busyPower, idlePower)
        );

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
                    new AppModuleAllocationPolicy(hostList), storageList, 10, upBw, downBw, 0, ratePerMips);
            fogdevice.setLevel(level);
        } catch (Exception e) {
            e.printStackTrace();
        }
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
    private static Application createApplication(String appId, int userId) {

        Application application = Application.createApplication(appId, userId);
        /*
         * Adding modules (vertices) to the application model (directed graph)
         */
        application.addAppModule("weather_detector", 10);
        application.addAppModule("humid_detector", 10);
        application.addAppModule("temp_detector", 10);
        application.addAppModule("user_interface", 10);

        /*
         * Connecting the application modules (vertices) in the application model (directed graph) with edges
         */
        application.addAppEdge("FOGDEVICE", "weather_detector", 1000,
                20000, "WEATHER_STREAM", Tuple.UP, AppEdge.SENSOR);
        application.addAppEdge("weather_detector", "humid_detector", 2000,
                2000, "HUMID_STREAM", Tuple.UP, AppEdge.MODULE);
//        application.addAppEdge("weather_detector", "temp_detector", 1000,
//                1000, "TEMP_STREAM", Tuple.UP, AppEdge.MODULE);
        application.addAppEdge("humid_detector", "user_interface", 500,
                2000, "HUMID_SHOW", Tuple.UP, AppEdge.MODULE);
//        application.addAppEdge("temp_detector", "user_interface", 250,
//                1000, "TEMP_SHOW", Tuple.UP, AppEdge.MODULE);
        application.addAppEdge("humid_detector", "PTZ_CALCULATE", 100, 28,
                100, "PTZ_PARAMS", Tuple.DOWN, AppEdge.ACTUATOR);
//        application.addAppEdge("temp_detector", "PTZ_CALCULATE", 100, 14,
//                50, "PTZ_PARAMS", Tuple.DOWN, AppEdge.ACTUATOR);

        /*
         * Defining the input-output relationships (represented by selectivity) of the application modules.
         */
        application.addTupleMapping("weather_detector", "WEATHER_STREAM", "HUMID_STREAM", new FractionalSelectivity(1.0));
//        application.addTupleMapping("weather_detector", "WEATHER_STREAM", "TEMP_STREAM", new FractionalSelectivity(1.0));
        application.addTupleMapping("humid_detector", "HUMID_STREAM", "HUMID_SHOW", new FractionalSelectivity(0.05));
//        application.addTupleMapping("temp_detector", "TEMP_STREAM", "TEMP_SHOW", new FractionalSelectivity(0.05));

        /*
         * Defining application loops (maybe incomplete loops) to monitor the latency of.
         * Here, we add two loops for monitoring : Motion Detector -> Object Detector -> Object Tracker and Object Tracker -> PTZ Control
         */
        final AppLoop loop1 = new AppLoop(new ArrayList<String>() {{
            add("weather_detector");
            add("humid_detector");
        }});
        final AppLoop loop2 = new AppLoop(new ArrayList<String>() {{
            add("weather_detector");
            add("temp_detector");
        }});
        final AppLoop loop3 = new AppLoop(new ArrayList<String>() {{
            add("humid_detector");
            add("PTZ_CALCULATE");
        }});
        final AppLoop loop4 = new AppLoop(new ArrayList<String>() {{
            add("temp_detector");
            add("PTZ_CALCULATE");
        }});
        List<AppLoop> loops = new ArrayList<AppLoop>() {{
            add(loop1);
//            add(loop2);
            add(loop3);
//            add(loop4);
        }};

        application.setLoops(loops);
        return application;
    }
}
