package org.fog.placement;

import java.util.*;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEntity;
import org.cloudbus.cloudsim.core.SimEvent;
import org.fog.application.AppEdge;
import org.fog.application.AppLoop;
import org.fog.application.AppModule;
import org.fog.application.Application;
import org.fog.entities.Actuator;
import org.fog.entities.FogDevice;
import org.fog.entities.Sensor;
import org.fog.utils.Config;
import org.fog.utils.FogEvents;
import org.fog.utils.FogUtils;
import org.fog.utils.NetworkUsageMonitor;
import org.fog.utils.TimeKeeper;

public class Controller extends SimEntity {

    public static boolean ONLY_CLOUD = false;

    private List<FogDevice> fogDevices;
    private List<Sensor> sensors;
    private List<Actuator> actuators;

    private Map<String, Application> applications;
    private Map<String, Integer> appLaunchDelays;

    private Map<String, ModulePlacement> appModulePlacementPolicy;

    public Controller(String name, List<FogDevice> fogDevices, List<Sensor> sensors, List<Actuator> actuators) {
        super(name);
        this.applications = new HashMap<String, Application>();
        setAppLaunchDelays(new HashMap<String, Integer>());
        setAppModulePlacementPolicy(new HashMap<String, ModulePlacement>());
        for (FogDevice fogDevice : fogDevices) {
            fogDevice.setControllerId(getId());
        }
        setFogDevices(fogDevices);
        setActuators(actuators);
        setSensors(sensors);
        connectWithLatencies();
    }

    private FogDevice getFogDeviceById(int id) {
        for (FogDevice fogDevice : getFogDevices()) {
            if (id == fogDevice.getId())
                return fogDevice;
        }
        return null;
    }

    //TODO: 设计传输连接延迟(上一级资源节点和下级资源节点之间||同级资源节点之间)
    private void connectWithLatencies() {
//        System.out.println("========= child latency ==========");
        for (FogDevice fogDevice : getFogDevices()) {
            FogDevice parent = getFogDeviceById(fogDevice.getParentId());
            if (parent == null)
                continue;
            double latency = fogDevice.getUplinkLatency();
            parent.getChildToLatencyMap().put(fogDevice.getId(), latency);
//            System.out.println(parent.getName() + "(" + fogDevice.getParentId() + ")----"
//                    + fogDevice.getName() + "(" + fogDevice.getId() + ")" + ": " + latency);
            parent.getChildrenIds().add(fogDevice.getId());
        }

        for (FogDevice fogDevice : getFogDevices()) {
            Map<Integer, Double> neighborLatency = fogDevice.getNeighborLatency();
            List<Integer> neighborIds = fogDevice.getNeighborIds();
//            System.out.println("=========" + fogDevice.getName() + "'s neighbor latency ==========");
            for (Integer neighborId : neighborIds) {
                FogDevice neighbor = getFogDeviceById(neighborId);
                double latency = neighborLatency.get(neighborId);
                fogDevice.getNeighborToLatencyMap().put(neighborId, latency);
//                System.out.println(fogDevice.getName() + "(" + fogDevice.getId() + ")----"
//                        + neighbor.getName() + "(" + neighborId + "): " + latency);
            }
        }
    }

    @Override
    public void startEntity() {
        for (String appId : applications.keySet()) {
            if (getAppLaunchDelays().get(appId) == 0)
                //任务的开端所以延时为0
                processAppSubmit(applications.get(appId));
            else
                send(getId(), getAppLaunchDelays().get(appId), FogEvents.APP_SUBMIT, applications.get(appId));
        }

        send(getId(), Config.RESOURCE_MANAGE_INTERVAL, FogEvents.CONTROLLER_RESOURCE_MANAGE);

        send(getId(), Config.MAX_SIMULATION_TIME, FogEvents.STOP_SIMULATION);

        for (FogDevice dev : getFogDevices())
            sendNow(dev.getId(), FogEvents.RESOURCE_MGMT);

    }

    @Override
    public void processEvent(SimEvent ev) {
        switch (ev.getTag()) {
            case FogEvents.APP_SUBMIT:
                processAppSubmit(ev);
                break;
            case FogEvents.TUPLE_FINISHED:
                processTupleFinished(ev);
                break;
            case FogEvents.CONTROLLER_RESOURCE_MANAGE:
                manageResources();
                break;
            case FogEvents.STOP_SIMULATION:
                CloudSim.stopSimulation();
                printTimeDetails();
                printPowerDetails();
                printCostDetails();
                printNetworkUsageDetails();
                System.exit(0);
                break;

        }
    }

    private void printNetworkUsageDetails() {
        System.out.println("Total network usage = " + NetworkUsageMonitor.getNetworkUsage() / Config.MAX_SIMULATION_TIME);
    }

    private FogDevice getCloud() {
        for (FogDevice dev : getFogDevices())
            if (dev.getName().equals("cloud"))
                return dev;
        return null;
    }

    private void printCostDetails() {
        System.out.println("Cost of execution in cloud = " + getCloud().getTotalCost());
    }

    private void printPowerDetails() {
        for (FogDevice fogDevice : getFogDevices()) {
            System.out.println(fogDevice.getName() + " : Energy Consumed = " + fogDevice.getEnergyConsumption());
        }
    }

    private String getStringForLoopId(int loopId) {
        for (String appId : getApplications().keySet()) {
            Application app = getApplications().get(appId);
            for (AppLoop loop : app.getLoops()) {
                if (loop.getLoopId() == loopId)
                    return loop.getModules().toString();
            }
        }
        return null;
    }


    private void printTimeDetails() {
        System.out.println("=========================================");
        System.out.println("============== RESULTS ==================");
        System.out.println("=========================================");
        System.out.println("EXECUTION TIME : " + (Calendar.getInstance().getTimeInMillis() - TimeKeeper.getInstance().getSimulationStartTime()));
        System.out.println("=========================================");
        System.out.println("APPLICATION LOOP DELAYS");
        System.out.println("=========================================");
        for (Integer loopId : TimeKeeper.getInstance().getLoopIdToTupleIds().keySet()) {
			/*double average = 0, count = 0;
			for(int tupleId : TimeKeeper.getInstance().getLoopIdToTupleIds().get(loopId)){
				Double startTime = 	TimeKeeper.getInstance().getEmitTimes().get(tupleId);
				Double endTime = 	TimeKeeper.getInstance().getEndTimes().get(tupleId);
				if(startTime == null || endTime == null)
					break;
				average += endTime-startTime;
				count += 1;
			}
			System.out.println(getStringForLoopId(loopId) + " ---> "+(average/count));*/
            System.out.println(getStringForLoopId(loopId) + " ---> " + TimeKeeper.getInstance().getLoopIdToCurrentAverage().get(loopId)
                    + " All task nums: " + TimeKeeper.getInstance().getLoopIdToCurrentNum().get(loopId));
        }
        System.out.println("=========================================");
        System.out.println("TUPLE CPU EXECUTION DELAY");
        System.out.println("=========================================");

        for (String tupleType : TimeKeeper.getInstance().getTupleTypeToAverageCpuTime().keySet()) {
            System.out.println(tupleType + " ---> " + TimeKeeper.getInstance().getTupleTypeToAverageCpuTime().get(tupleType)
                    + " nums: " + TimeKeeper.getInstance().getTupleTypeToExecutedTupleCount().get(tupleType));
        }

        System.out.println("=========================================");
//        System.out.println("Send to neighbor: " + Config.SEND_NEIGHBOR);
        System.out.println("Send to neighbor successful: " + Config.SEND_NEIGHBOR_SUCCESS);
        System.out.println("Send to neighbor successful(APP1): " + Config.SEND_NEIGHBOR_SUCCESS_APP1);
        System.out.println("Send to neighbor successful(APP2): " + Config.SEND_NEIGHBOR_SUCCESS_APP2);
        System.out.println("Send back: " + Config.SEND_BACK);
        System.out.println("Send back app1: " + Config.SEND_BACK_APP1);
        System.out.println("Send back app2: " + Config.SEND_BACK_APP2);
        System.out.println("WAITINGLIST: " + Config.WAITINGLIST_SIZE);
        System.out.println("WAITINGLIST_APP1: " + Config.WAITINGLIST_SIZE_APP1);
        System.out.println("WAITINGLIST_APP2: " + Config.WAITINGLIST_SIZE_APP2);
        System.out.println("TUPLE_ALL: " + Config.TUPLE_ALL);
        System.out.println("TUPLE_IN_LIMIT_TIME: " + Config.TUPLE_IN_LIMIT_TIME);

        System.out.println("EXECLIST FROM WAITINGLIST: " + Config.EXECLIST_SIZE_IN_WAITINGLIST);
//        System.out.println("EXECLIST FROM WAITINGLIST APP1: " + Config.EXECLIST_SIZE_IN_WAITINGLIST_APP1);
//        System.out.println("EXECLIST FROM WAITINGLIST APP2: " + Config.EXECLIST_SIZE_IN_WAITINGLIST_APP2);
    }

    protected void manageResources() {
        send(getId(), Config.RESOURCE_MANAGE_INTERVAL, FogEvents.CONTROLLER_RESOURCE_MANAGE);
    }

    private void processTupleFinished(SimEvent ev) {
    }

    @Override
    public void shutdownEntity() {
    }

    public void submitApplication(Application application, int delay, ModulePlacement modulePlacement) {
        FogUtils.appIdToGeoCoverageMap.put(application.getAppId(), application.getGeoCoverage());
        getApplications().put(application.getAppId(), application);
        getAppLaunchDelays().put(application.getAppId(), delay);
        getAppModulePlacementPolicy().put(application.getAppId(), modulePlacement);

        //将传感器和app关联
        for (Sensor sensor : sensors) {
            sensor.setApp(getApplications().get(sensor.getAppId()));
        }
        //将执行器和app关联
        for (Actuator ac : actuators) {
            ac.setApp(getApplications().get(ac.getAppId()));
        }

        //注册在这次应用中执行器接收的数据流类型和对应的所有执行器编号形成的队列
        for (AppEdge edge : application.getEdges()) {
            if (edge.getEdgeType() == AppEdge.ACTUATOR) {
                String moduleName = edge.getSource();
                for (Actuator actuator : getActuators()) {
                    if (actuator.getActuatorType().equalsIgnoreCase(edge.getDestination()))
                        application.getModuleByName(moduleName).subscribeActuator(actuator.getId(), edge.getTupleType());
                }
            }
        }
    }

    public void submitApplication(Application application, ModulePlacement modulePlacement) {
        submitApplication(application, 0, modulePlacement);
    }

    //TODO: 一次提交一组应用（加循环）
    public void submitApplications(List<Application> applications, ModulePlacement modulePlacement) {
        for (Application application : applications) {
            submitApplication(application, 0, modulePlacement);
        }
    }


    private void processAppSubmit(SimEvent ev) {
        Application app = (Application) ev.getData();
        processAppSubmit(app);
    }

    //处理整体应用的开端
    private void processAppSubmit(Application application) {
        System.out.println(CloudSim.clock() + " Submitted application " + application.getAppId());
        FogUtils.appIdToGeoCoverageMap.put(application.getAppId(), application.getGeoCoverage());
        getApplications().put(application.getAppId(), application);

        ModulePlacement modulePlacement = getAppModulePlacementPolicy().get(application.getAppId());
        for (FogDevice fogDevice : fogDevices) {
            sendNow(fogDevice.getId(), FogEvents.ACTIVE_APP_UPDATE, application);
        }

        Map<Integer, List<AppModule>> deviceToModuleMap = modulePlacement.getDeviceToModuleMap();
        for (Integer deviceId : deviceToModuleMap.keySet()) {
            for (AppModule module : deviceToModuleMap.get(deviceId)) {
                sendNow(deviceId, FogEvents.APP_SUBMIT, application);
                sendNow(deviceId, FogEvents.LAUNCH_MODULE, module);
            }
        }
    }

    public List<FogDevice> getFogDevices() {
        return fogDevices;
    }

    public void setFogDevices(List<FogDevice> fogDevices) {
        this.fogDevices = fogDevices;
    }

    public Map<String, Integer> getAppLaunchDelays() {
        return appLaunchDelays;
    }

    public void setAppLaunchDelays(Map<String, Integer> appLaunchDelays) {
        this.appLaunchDelays = appLaunchDelays;
    }

    public Map<String, Application> getApplications() {
        return applications;
    }

    public void setApplications(Map<String, Application> applications) {
        this.applications = applications;
    }

    public List<Sensor> getSensors() {
        return sensors;
    }

    public void setSensors(List<Sensor> sensors) {
        for (Sensor sensor : sensors)
            sensor.setControllerId(getId());
        this.sensors = sensors;
    }

    public List<Actuator> getActuators() {
        return actuators;
    }

    public void setActuators(List<Actuator> actuators) {
        this.actuators = actuators;
    }

    public Map<String, ModulePlacement> getAppModulePlacementPolicy() {
        return appModulePlacementPolicy;
    }

    public void setAppModulePlacementPolicy(Map<String, ModulePlacement> appModulePlacementPolicy) {
        this.appModulePlacementPolicy = appModulePlacementPolicy;
    }
}