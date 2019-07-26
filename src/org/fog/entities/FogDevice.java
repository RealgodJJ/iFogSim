package org.fog.entities;

import java.util.*;

import org.apache.commons.math3.util.Pair;
import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.CloudSimTags;
import org.cloudbus.cloudsim.core.SimEvent;
import org.cloudbus.cloudsim.power.PowerDataCenter;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.power.models.PowerModel;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.sdn.overbooking.BwProvisionerOverbooking;
import org.cloudbus.cloudsim.sdn.overbooking.PeProvisionerOverbooking;
import org.fog.application.AppEdge;
import org.fog.application.AppLoop;
import org.fog.application.AppModule;
import org.fog.application.Application;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.utils.*;

import javax.swing.text.html.parser.Entity;

public class FogDevice extends PowerDataCenter {
    protected Queue<Tuple> waitingQueue;
    protected Queue<Tuple> northTupleQueue;
    //向下传传输的对列<tuple, childId>
    protected Queue<Pair<Tuple, Integer>> southTupleQueue;
    //TODO: 向邻居传输的对列<tuple, neighborId>
    protected Queue<Pair<Tuple, Integer>> neighborTupleQueue;

    protected List<String> activeApplications;

    protected Map<String, Application> applicationMap;
    protected Map<String, List<String>> appToModulesMap;
    protected Map<Integer, Double> childToLatencyMap;
    //TODO: 获取设备与邻近节点之间的延迟（<fogDeviceId, <neighborId, latency>>）
//    protected Map<Integer, Map<Integer, Double>> neighborToLatencyMap;
    protected Map<Integer, Double> neighborToLatencyMap;

    protected Map<Integer, Integer> cloudTrafficMap;

    protected double lockTime;

    /**
     * ID of the parent Fog Device
     */
    protected int parentId;

    protected int targetNeighborId;

    /**
     * ID of the Controller
     */
    protected int controllerId;

    //TODO：添加了相邻的边缘节点(<neighborId>)
    protected List<Integer> neighborIds;
    /**
     * IDs of the children Fog devices
     */
    protected List<Integer> childrenIds;

    protected Map<Integer, List<String>> childToOperatorsMap;

    /**
     * Flag denoting whether the link southwards from this FogDevice is busy
     */
    protected boolean isSouthLinkBusy;

    /**
     * Flag denoting whether the link northwards from this FogDevice is busy
     */
    protected boolean isNorthLinkBusy;

    //TODO: 判断周围邻居节点是否繁忙
    protected boolean isNeighborLinkBusy;

    protected double uplinkBandwidth;
    protected double downlinkBandwidth;
    //TODO: 邻居节点之间的传输带宽
    protected double neighborBandwidth;

    protected double uplinkLatency;
    //TODO: 邻居节点之间的传输延时(<neighborId, 对应的latency>)
    protected Map<Integer, Double> neighborLatency;
    protected List<Pair<Integer, Double>> associatedActuatorIds; //Pair可以返回一个键值对(<actuatorId, delay>)

    protected double energyConsumption;
    protected double lastUtilizationUpdateTime;
    protected double lastUtilization;
    protected double ratePerMips;
    protected double totalCost;
    protected Map<String, Map<String, Integer>> moduleInstanceCount;

    protected int tupleQuanityFromChildren = 0;
    protected int tupleQuanityFromParent = 0;
    protected Queue<Pair<Integer, Integer>> tupleQuantityOfNeighbors;
    private int level;

    //DCNSFog的第一种调用
    public FogDevice(String name, FogDeviceCharacteristics characteristics, VmAllocationPolicy vmAllocationPolicy,
                     List<Storage> storageList, double schedulingInterval, double uplinkBandwidth, double downlinkBandwidth,
                     double uplinkLatency, double ratePerMips, int level) throws Exception {
        super(name, characteristics, vmAllocationPolicy, storageList, schedulingInterval);
        setCharacteristics(characteristics);
        setVmAllocationPolicy(vmAllocationPolicy);
        setLastProcessTime(0.0);
        setStorageList(storageList);
        setVmList(new ArrayList<Vm>());
        setSchedulingInterval(schedulingInterval);
        setUplinkBandwidth(uplinkBandwidth);
        setDownlinkBandwidth(downlinkBandwidth);
        setUplinkLatency(uplinkLatency);
        setRatePerMips(ratePerMips);
        setLevel(level);
        setAssociatedActuatorIds(new ArrayList<Pair<Integer, Double>>());
        for (Host host : getCharacteristics().getHostList()) {
            host.setDataCenter(this);
        }
        setActiveApplications(new ArrayList<String>());
        // If this resource doesn't have any PEs then no useful at all
        if (getCharacteristics().getNumberOfPes() == 0) {
            throw new Exception(super.getName()
                    + " : Error - this entity has no PEs. Therefore, can't process any Cloudlets.");
        }
        // stores id of this class
        getCharacteristics().setId(super.getId());

        applicationMap = new HashMap<String, Application>();
        appToModulesMap = new HashMap<String, List<String>>();
        waitingQueue = new LinkedList<Tuple>();
        northTupleQueue = new LinkedList<Tuple>();
        southTupleQueue = new LinkedList<Pair<Tuple, Integer>>();
        neighborTupleQueue = new LinkedList<Pair<Tuple, Integer>>();
        setNorthLinkBusy(false);
        setSouthLinkBusy(false);
        setNeighborLinkBusy(false);

        setNeighborIds(new ArrayList<Integer>());
        setChildrenIds(new ArrayList<Integer>());
        setChildToOperatorsMap(new HashMap<Integer, List<String>>());

        this.cloudTrafficMap = new HashMap<Integer, Integer>();

        this.lockTime = 0;

        this.energyConsumption = 0;
        this.lastUtilization = 0;
        setTotalCost(0);
        setModuleInstanceCount(new HashMap<String, Map<String, Integer>>());
        setChildToLatencyMap(new HashMap<Integer, Double>());
        setNeighborToLatencyMap(new HashMap<Integer, Double>());
        this.targetNeighborId = 0;

        //TODO:添加FogDevice信息的打印
        System.out.println("name:" + name + System.lineSeparator() +
                "mips: " + characteristics.getHostList().get(0).getPeList().get(0).getPeProvisioner().getMips() + System.lineSeparator() +
                "ram: " + characteristics.getHostList().get(0).getRamProvisioner().getRam() + System.lineSeparator() +
                "upBw:" + uplinkBandwidth + System.lineSeparator() +
                "downBw:" + downlinkBandwidth + System.lineSeparator() +
                "level:" + getLevel() + System.lineSeparator() +
                "ratePerMips:" + ratePerMips + System.lineSeparator() +
                "busyPower: " + ((FogLinearPowerModel) ((PowerHost) characteristics.getHostList().get(0)).getPowerModel()).getMaxPower() + System.lineSeparator() +
                "idlePower: " + ((FogLinearPowerModel) ((PowerHost) characteristics.getHostList().get(0)).getPowerModel()).getStaticPower() +
                System.lineSeparator());
    }

    //DCNSFog的第二种调用
    public FogDevice(String name, FogDeviceCharacteristics characteristics, VmAllocationPolicy vmAllocationPolicy,
                     List<Storage> storageList, double schedulingInterval, double uplinkBandwidth, double downlinkBandwidth,
                     double neighborBandwidth, double uplinkLatency, Map<Integer, Double> neighborLatency, double ratePerMips, int level) throws Exception {
        super(name, characteristics, vmAllocationPolicy, storageList, schedulingInterval);
        setCharacteristics(characteristics);
        setVmAllocationPolicy(vmAllocationPolicy);
        setLastProcessTime(0.0);
        setStorageList(storageList);
        setVmList(new ArrayList<Vm>());
        setSchedulingInterval(schedulingInterval);
        setUplinkBandwidth(uplinkBandwidth);
        setDownlinkBandwidth(downlinkBandwidth);
        setNeighborBandwidth(neighborBandwidth);
        setUplinkLatency(uplinkLatency);
        setNeighborLatency(neighborLatency);
        setRatePerMips(ratePerMips);
        setLevel(level);
        setAssociatedActuatorIds(new ArrayList<Pair<Integer, Double>>());
        for (Host host : getCharacteristics().getHostList()) {
            host.setDataCenter(this);
        }
        setActiveApplications(new ArrayList<String>());
        // If this resource doesn't have any PEs then no useful at all
        if (getCharacteristics().getNumberOfPes() == 0) {
            throw new Exception(super.getName()
                    + " : Error - this entity has no PEs. Therefore, can't process any Cloudlets.");
        }
        // stores id of this class
        getCharacteristics().setId(super.getId());

        applicationMap = new HashMap<String, Application>();
        appToModulesMap = new HashMap<String, List<String>>();
        waitingQueue = new LinkedList<Tuple>();
        northTupleQueue = new LinkedList<Tuple>();
        southTupleQueue = new LinkedList<Pair<Tuple, Integer>>();
        neighborTupleQueue = new LinkedList<Pair<Tuple, Integer>>();
        setNorthLinkBusy(false);
        setSouthLinkBusy(false);

        setNeighborIds(new ArrayList<Integer>());
        setChildrenIds(new ArrayList<Integer>());
        setChildToOperatorsMap(new HashMap<Integer, List<String>>());

        this.cloudTrafficMap = new HashMap<Integer, Integer>();

        this.lockTime = 0;

        this.energyConsumption = 0;
        this.lastUtilization = 0;
        setTotalCost(0);
        setModuleInstanceCount(new HashMap<String, Map<String, Integer>>());
        setChildToLatencyMap(new HashMap<Integer, Double>());
        setNeighborToLatencyMap(new HashMap<Integer, Double>());
        targetNeighborId = 0;

        //TODO:添加FogDevice信息的打印
        System.out.println("name:" + name + System.lineSeparator() +
                "mips: " + characteristics.getHostList().get(0).getPeList().get(0).getPeProvisioner().getMips() + System.lineSeparator() +
                "ram: " + characteristics.getHostList().get(0).getRamProvisioner().getRam() + System.lineSeparator() +
                "upBw:" + uplinkBandwidth + System.lineSeparator() +
                "downBw:" + downlinkBandwidth + System.lineSeparator() +
                "level:" + getLevel() + System.lineSeparator() +
                "ratePerMips:" + ratePerMips + System.lineSeparator() +
                "busyPower: " + ((FogLinearPowerModel) ((PowerHost) characteristics.getHostList().get(0)).getPowerModel()).getMaxPower() + System.lineSeparator() +
                "idlePower: " + ((FogLinearPowerModel) ((PowerHost) characteristics.getHostList().get(0)).getPowerModel()).getStaticPower() +
                System.lineSeparator());
    }


//    public FogDevice(String name, FogDeviceCharacteristics characteristics, VmAllocationPolicy vmAllocationPolicy,
//                     List<Storage> storageList, double schedulingInterval, double uplinkBandwidth, double downlinkBandwidth,
//                     double uplinkLatency, Map<Integer, Double> neighborLatency, double ratePerMips) throws Exception {
//        super(name, characteristics, vmAllocationPolicy, storageList, schedulingInterval);
//        setCharacteristics(characteristics);
//        setVmAllocationPolicy(vmAllocationPolicy);
//        setLastProcessTime(0.0);
//        setStorageList(storageList);
//        setVmList(new ArrayList<Vm>());
//        setSchedulingInterval(schedulingInterval);
//        setUplinkBandwidth(uplinkBandwidth);
//        setDownlinkBandwidth(downlinkBandwidth);
//        setUplinkLatency(uplinkLatency);
//        setNeighborLatency(neighborLatency);
//        setRatePerMips(ratePerMips);
//        setAssociatedActuatorIds(new ArrayList<Pair<Integer, Double>>());
//        for (Host host : getCharacteristics().getHostList()) {
//            host.setDataCenter(this);
//        }
//        setActiveApplications(new ArrayList<String>());
//        // If this resource doesn't have any PEs then no useful at all
//        if (getCharacteristics().getNumberOfPes() == 0) {
//            throw new Exception(super.getName()
//                    + " : Error - this entity has no PEs. Therefore, can't process any Cloudlets.");
//        }
//        // stores id of this class
//        getCharacteristics().setId(super.getId());
//
//        applicationMap = new HashMap<String, Application>();
//        appToModulesMap = new HashMap<String, List<String>>();
//        northTupleQueue = new LinkedList<Tuple>();
//        southTupleQueue = new LinkedList<Pair<Tuple, Integer>>();
//        setNorthLinkBusy(false);
//        setSouthLinkBusy(false);
//
//        setNeighborIds(new ArrayList<Integer>());
//        setChildrenIds(new ArrayList<Integer>());
//        setChildToOperatorsMap(new HashMap<Integer, List<String>>());
//
//        this.cloudTrafficMap = new HashMap<Integer, Integer>();
//
//        this.lockTime = 0;
//
//        this.energyConsumption = 0;
//        this.lastUtilization = 0;
//        setTotalCost(0);
//        setModuleInstanceCount(new HashMap<String, Map<String, Integer>>());
//        setChildToLatencyMap(new HashMap<Integer, Double>());
//
//        //TODO:添加FogDevice信息的打印
//        System.out.println("name:" + name + System.lineSeparator() +
//                "mips: " + characteristics.getHostList().get(0).getPeList().get(0).getPeProvisioner().getMips() + System.lineSeparator() +
//                "ram: " + characteristics.getHostList().get(0).getRamProvisioner().getRam() + System.lineSeparator() +
//                "upBw:" + uplinkBandwidth + System.lineSeparator() +
//                "downBw:" + downlinkBandwidth + System.lineSeparator() +
//                "level:" + level + System.lineSeparator() +
//                "ratePerMips:" + ratePerMips + System.lineSeparator() +
//                "busyPower: " + ((FogLinearPowerModel) ((PowerHost) characteristics.getHostList().get(0)).getPowerModel()).getMaxPower() + System.lineSeparator() +
//                "idlePower: " + ((FogLinearPowerModel) ((PowerHost) characteristics.getHostList().get(0)).getPowerModel()).getStaticPower() +
//                System.lineSeparator());
//    }

    @Override
    public String toString() {
        return "";
    }

    public FogDevice(
            String name, long mips, int ram,
            double uplinkBandwidth, double downlinkBandwidth, double ratePerMips, PowerModel powerModel) throws Exception {
        super(name, null, null, new LinkedList<Storage>(), 0);

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
                powerModel
        );

        List<Host> hostList = new ArrayList<Host>();
        hostList.add(host);

        setVmAllocationPolicy(new AppModuleAllocationPolicy(hostList));

        String arch = Config.FOG_DEVICE_ARCH;
        String os = Config.FOG_DEVICE_OS;
        String vmm = Config.FOG_DEVICE_VMM;
        double time_zone = Config.FOG_DEVICE_TIMEZONE;
        double cost = Config.FOG_DEVICE_COST;
        double costPerMem = Config.FOG_DEVICE_COST_PER_MEMORY;
        double costPerStorage = Config.FOG_DEVICE_COST_PER_STORAGE;
        double costPerBw = Config.FOG_DEVICE_COST_PER_BW;

        FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(
                arch, os, vmm, host, time_zone, cost, costPerMem,
                costPerStorage, costPerBw);

        setCharacteristics(characteristics);

        setLastProcessTime(0.0);
        setVmList(new ArrayList<Vm>());
        setUplinkBandwidth(uplinkBandwidth);
        setDownlinkBandwidth(downlinkBandwidth);
        setUplinkLatency(uplinkLatency);
        setAssociatedActuatorIds(new ArrayList<Pair<Integer, Double>>());
        for (Host host1 : getCharacteristics().getHostList()) {
            host1.setDataCenter(this);
        }
        setActiveApplications(new ArrayList<String>());
        if (getCharacteristics().getNumberOfPes() == 0) {
            throw new Exception(super.getName()
                    + " : Error - this entity has no PEs. Therefore, can't process any Cloudlets.");
        }


        getCharacteristics().setId(super.getId());

        applicationMap = new HashMap<String, Application>();
        appToModulesMap = new HashMap<String, List<String>>();
        waitingQueue = new LinkedList<Tuple>();
        northTupleQueue = new LinkedList<Tuple>();
        southTupleQueue = new LinkedList<Pair<Tuple, Integer>>();
        setNorthLinkBusy(false);
        setSouthLinkBusy(false);

        setNeighborIds(new ArrayList<Integer>());
        setChildrenIds(new ArrayList<Integer>());
        setChildToOperatorsMap(new HashMap<Integer, List<String>>());

        this.cloudTrafficMap = new HashMap<Integer, Integer>();

        this.lockTime = 0;

        this.energyConsumption = 0;
        this.lastUtilization = 0;
        setTotalCost(0);
        setChildToLatencyMap(new HashMap<Integer, Double>());
        setNeighborToLatencyMap(new HashMap<Integer, Double>());
        setModuleInstanceCount(new HashMap<String, Map<String, Integer>>());
    }

    /**
     * Overrides this method when making a new and different type of resource. <br>
     * <b>NOTE:</b> You do not need to override method, if you use this method.
     *
     * @pre $none
     * @post $none
     */
    protected void registerOtherEntity() {

    }

    @Override
    protected void processOtherEvent(SimEvent ev) {
        switch (ev.getTag()) {
            case FogEvents.TUPLE_ARRIVAL:
                processTupleArrival(ev);
                break;
            case FogEvents.LAUNCH_MODULE:
                processModuleArrival(ev);
                break;
            case FogEvents.RELEASE_OPERATOR:
                processOperatorRelease(ev);
                break;
            case FogEvents.SENSOR_JOINED:
                processSensorJoining(ev);
                break;
            case FogEvents.SEND_PERIODIC_TUPLE:
                sendPeriodicTuple(ev);
                break;
            case FogEvents.APP_SUBMIT:
                processAppSubmit(ev);
                break;
            case FogEvents.UPDATE_NORTH_TUPLE_QUEUE:
                updateNorthTupleQueue();
                break;
            case FogEvents.UPDATE_SOUTH_TUPLE_QUEUE:
                updateSouthTupleQueue();
                break;
            case FogEvents.UPDATE_NEIGHBOR_TUPLE_QUEUE:
                updateNeighborTupleQueue();
                break;
            case FogEvents.ACTIVE_APP_UPDATE:
                updateActiveApplications(ev);
                break;
            case FogEvents.ACTUATOR_JOINED:
                processActuatorJoined(ev);
                break;
            case FogEvents.LAUNCH_MODULE_INSTANCE:
                updateModuleInstanceCount(ev);
                break;
            case FogEvents.RESOURCE_MGMT:
                manageResources(ev);
            default:
                break;
        }
    }

    /**
     * Perform miscellaneous resource management tasks
     *
     * @param ev
     */
    private void manageResources(SimEvent ev) {
        updateEnergyConsumption();
        send(getId(), Config.RESOURCE_MGMT_INTERVAL, FogEvents.RESOURCE_MGMT);
//        System.out.println("==== manageResources ====");
    }

    /**
     * Updating the number of modules of an application module on this device
     *
     * @param ev instance of SimEvent containing the module and no of instances
     */
    private void updateModuleInstanceCount(SimEvent ev) {
        ModuleLaunchConfig config = (ModuleLaunchConfig) ev.getData();
        String appId = config.getModule().getAppId();
        if (!moduleInstanceCount.containsKey(appId))
            moduleInstanceCount.put(appId, new HashMap<String, Integer>());
        moduleInstanceCount.get(appId).put(config.getModule().getName(), config.getInstanceCount());
//        System.out.println("==== updateModuleInstanceCount ====");
//        System.out.println(getName() + " Creating " + config.getInstanceCount() + " instances of module " + config.getModule().getName());
    }

    private AppModule getModuleByName(String moduleName) {
        AppModule module = null;
        for (Vm vm : getHost().getVmList()) {
            if (((AppModule) vm).getName().equals(moduleName)) {
                module = (AppModule) vm;
                break;
            }
        }
        return module;
    }

    /**
     * Sending periodic tuple for an application edge. Note that for multiple instances of a single source module, only one tuple is sent DOWN while instanceCount number of tuples are sent UP.
     *
     * @param ev SimEvent instance containing the edge to send tuple on
     */
    private void sendPeriodicTuple(SimEvent ev) {
        AppEdge edge = (AppEdge) ev.getData();
        String srcModule = edge.getSource();
        AppModule module = getModuleByName(srcModule);

        if (module == null)
            return;

        int instanceCount = module.getNumInstances();
        /*
         * Since tuples sent through a DOWN application edge are anyways broadcasted, only UP tuples are replicated
         */
        for (int i = 0; i < ((edge.getDirection() == Tuple.UP) ? instanceCount : 1); i++) {
            //System.out.println(CloudSim.clock()+" : Sending periodic tuple "+edge.getTupleType());
            Tuple tuple = applicationMap.get(module.getAppId()).createTuple(edge, getId(), module.getId());
            updateTimingsOnSending(tuple);
            sendToSelf(tuple);
        }
        send(getId(), edge.getPeriodicity(), FogEvents.SEND_PERIODIC_TUPLE, edge);
//        System.out.println("==== sendPeriodicTuple ====");
    }

    protected void processActuatorJoined(SimEvent ev) {
        int actuatorId = ev.getSource();
        double delay = (double) ev.getData();
        getAssociatedActuatorIds().add(new Pair<Integer, Double>(actuatorId, delay));
//        System.out.println("==== processActuatorJoined ====");
    }

    protected void updateActiveApplications(SimEvent ev) {
        Application app = (Application) ev.getData();
        getActiveApplications().add(app.getAppId());
//        System.out.println("==== updateActiveApplications ====");
    }

    public String getOperatorName(int vmId) {
        for (Vm vm : this.getHost().getVmList()) {
            if (vm.getId() == vmId)
                return ((AppModule) vm).getName();
        }
        return null;
    }

    /**
     * Update cloudet processing without scheduling future events.
     *
     * @return the double
     */
    protected double updateCloudetProcessingWithoutSchedulingFutureEventsForce() {
        double currentTime = CloudSim.clock();
        double minTime = Double.MAX_VALUE;
        double timeDiff = currentTime - getLastProcessTime();
        double timeFrameDatacenterEnergy = 0.0;

        for (PowerHost host : this.<PowerHost>getHostList()) {
            Log.printLine();

            double time = host.updateVmsProcessing(currentTime); // inform VMs to update processing
            if (time < minTime) {
                minTime = time;
            }

            Log.formatLine(
                    "%.2f: [Host #%d] utilization is %.2f%%",
                    currentTime,
                    host.getId(),
                    host.getUtilizationOfCpu() * 100);
        }

        if (timeDiff > 0) {
            Log.formatLine(
                    "\nEnergy consumption for the last time frame from %.2f to %.2f:",
                    getLastProcessTime(),
                    currentTime);

            for (PowerHost host : this.<PowerHost>getHostList()) {
                double previousUtilizationOfCpu = host.getPreviousUtilizationOfCpu();
                double utilizationOfCpu = host.getUtilizationOfCpu();
                double timeFrameHostEnergy = host.getEnergyLinearInterpolation(
                        previousUtilizationOfCpu,
                        utilizationOfCpu,
                        timeDiff);
                timeFrameDatacenterEnergy += timeFrameHostEnergy;

                Log.printLine();
                Log.formatLine(
                        "%.2f: [Host #%d] utilization at %.2f was %.2f%%, now is %.2f%%",
                        currentTime,
                        host.getId(),
                        getLastProcessTime(),
                        previousUtilizationOfCpu * 100,
                        utilizationOfCpu * 100);
                Log.formatLine(
                        "%.2f: [Host #%d] energy is %.2f W*sec",
                        currentTime,
                        host.getId(),
                        timeFrameHostEnergy);
            }

            Log.formatLine(
                    "\n%.2f: Data center's energy is %.2f W*sec\n",
                    currentTime,
                    timeFrameDatacenterEnergy);
        }

        setPower(getPower() + timeFrameDatacenterEnergy);

        checkCloudletCompletion();

        /** Remove completed VMs **/
        /**
         * Change made by HARSHIT GUPTA
         */
		/*for (PowerHost host : this.<PowerHost> getHostList()) {
			for (Vm vm : host.getCompletedVms()) {
				getVmAllocationPolicy().deallocateHostForVm(vm);
				getVmList().remove(vm);
				Log.printLine("VM #" + vm.getId() + " has been deallocated from host #" + host.getId());
			}
		}*/

        Log.printLine();

        setLastProcessTime(currentTime);
        return minTime;
    }

    //查看任务是否被完成
    protected void checkCloudletCompletion() {
        boolean cloudletCompleted = false;
        List<? extends Host> list = getVmAllocationPolicy().getHostList();
        for (int i = 0; i < list.size(); i++) {
            Host host = list.get(i);
            for (Vm vm : host.getVmList()) {
                while (vm.getCloudletScheduler().isFinishedCloudlets()) {
                    Cloudlet cl = vm.getCloudletScheduler().getNextFinishedCloudlet();
                    if (cl != null) {

                        cloudletCompleted = true;
                        Tuple tuple = (Tuple) cl;
                        TimeKeeper.getInstance().tupleEndedExecution(tuple);
                        Application application = getApplicationMap().get(tuple.getAppId());
                        Logger.debug(getName(), "Completed execution of tuple " + tuple.getCloudletId() + " on " + tuple.getDestModuleName());

                        //实现tuple之间的交接
                        List<Tuple> resultantTuples = application.getResultantTuples(tuple.getDestModuleName(), tuple, getId(), vm.getId(), tuple.getBeginDeviceId());
                        for (Tuple resTuple : resultantTuples) {
                            resTuple.setModuleCopyMap(new HashMap<String, Integer>(tuple.getModuleCopyMap()));
                            resTuple.getModuleCopyMap().put(((AppModule) vm).getName(), vm.getId());
                            updateTimingsOnSending(resTuple);   //在以前收到的tuple生成一个Tuple时更新时间
                            sendToSelf(resTuple);
                        }
                        sendNow(cl.getUserId(), CloudSimTags.CLOUDLET_RETURN, cl);
                    }
                }
            }
        }
        if (cloudletCompleted)
            updateAllocatedMips(null);
    }

    protected void updateTimingsOnSending(Tuple resTuple) {
        // TODO ADD CODE FOR UPDATING TIMINGS WHEN A TUPLE IS GENERATED FROM A PREVIOUSLY RECEIVED TUPLE.
        // WILL NEED TO CHECK IF A NEW LOOP STARTS AND INSERT A UNIQUE TUPLE ID TO IT.
        String srcModule = resTuple.getSrcModuleName();
        String destModule = resTuple.getDestModuleName();
        for (AppLoop loop : getApplicationMap().get(resTuple.getAppId()).getLoops()) {
            if (loop.hasEdge(srcModule, destModule) && loop.isStartModule(srcModule)) {
                int tupleId = TimeKeeper.getInstance().getUniqueId();
                resTuple.setActualTupleId(tupleId);
                if (!TimeKeeper.getInstance().getLoopIdToTupleIds().containsKey(loop.getLoopId()))
                    TimeKeeper.getInstance().getLoopIdToTupleIds().put(loop.getLoopId(), new ArrayList<Integer>());
                TimeKeeper.getInstance().getLoopIdToTupleIds().get(loop.getLoopId()).add(tupleId);
                TimeKeeper.getInstance().getEmitTimes().put(tupleId, CloudSim.clock());

                //Logger.debug(getName(), "\tSENDING\t"+tuple.getActualTupleId()+"\tSrc:"+srcModule+"\tDest:"+destModule);

            }
        }
    }

//    protected int getFriendIdWithRouteTo(int targetDeviceId) {
//        for (Integer friendId : getNeighborIds()) {
//            if (targetDeviceId == friendId)
//                return friendId;
//            if (((FogDevice)CloudSim.getEntity(friendId)).getFriendIdWithRouteTo(targetDeviceId) != -1)
//                return friendId;
//        }
//        return -1;
//    }

    protected int getChildIdWithRouteTo(int targetDeviceId) {
        for (Integer childId : getChildrenIds()) {
            if (targetDeviceId == childId)
                return childId;
            if (((FogDevice) CloudSim.getEntity(childId)).getChildIdWithRouteTo(targetDeviceId) != -1)
                return childId;
        }
        return -1;
    }

    protected int getChildIdForTuple(Tuple tuple) {
        if (tuple.getDirection() == Tuple.ACTUATOR) {
            int gatewayId = ((Actuator) CloudSim.getEntity(tuple.getActuatorId())).getGatewayDeviceId();
            return getChildIdWithRouteTo(gatewayId);
        }
        return -1;
    }

    //应用程序的调度可通过覆盖此方法，实现定制策略
    protected void updateAllocatedMips(String incomingOperator) {
        getHost().getVmScheduler().deallocatePesForAllVms();
        for (final Vm vm : getHost().getVmList()) {
            //TODO: 获取当前设备上
            CloudletSchedulerSpaceShared cloudletScheduler = (CloudletSchedulerSpaceShared) vm.getCloudletScheduler();
            List<ResCloudlet> cloudletWaitingList = cloudletScheduler.getCloudletWaitingList();
            List<ResCloudlet> cloudletExecList = cloudletScheduler.getCloudletExecList();
//            System.out.println(getName() + ": " + ((AppModule) vm).getName() + ": " + vm.getMips());

            if (cloudletExecList.size() > 1) {
                System.out.println("====ExecList====");
                for (int i = 0; i < cloudletExecList.size(); i++) {
                    ResCloudlet resCloudlet = cloudletExecList.get(i);
                    System.err.println(resCloudlet.getCloudletId() + ": " + resCloudlet.getCloudletLength());
                }
            }
//
//            if (cloudletWaitingList != null) {
//                System.out.println("====WaitingList====");
//                for (int i = 0; i < cloudletWaitingList.size(); i++) {
//                    ResCloudlet resCloudlet = cloudletWaitingList.get(i);
//                    System.out.println(resCloudlet.getCloudletId() + ": " + resCloudlet.getCloudletLength());
//                }
//            }
//            if (cloudletWaitingList != null) {
//                for (int i = 0; i <cloudletWaitingList.size(); i++) {
//                    Tuple tuple = ((Tuple)cloudletWaitingList.get(i).getCloudlet());
//                    System.out.println(getName() + " " + tuple.getCloudletId() + ": " + tuple.getTupleType() + "  "
//                            + tuple.getSrcModuleName() + " -> " + tuple.getDestModuleName());
//                }
//                System.out.println();
//            }
            if (vm.getCloudletScheduler().runningCloudlets() > 0 || ((AppModule) vm).getName().equals(incomingOperator)) {
                getHost().getVmScheduler().allocatePesForVm(vm, new ArrayList<Double>() {
                    protected static final long serialVersionUID = 1L;

                    {
//                        add(vm.getMips());
                        add((double) getHost().getTotalMips());
                    }
                });
            } else {
                getHost().getVmScheduler().allocatePesForVm(vm, new ArrayList<Double>() {
                    protected static final long serialVersionUID = 1L;

                    {
                        add(0.0);
                    }
                });
            }
        }
//        System.out.println();

        updateEnergyConsumption();

    }

    private void updateEnergyConsumption() {
        double totalMipsAllocated = 0;
        for (final Vm vm : getHost().getVmList()) {
            AppModule operator = (AppModule) vm;
            operator.updateVmProcessing(CloudSim.clock(), getVmAllocationPolicy().getHost(operator).getVmScheduler()
                    .getAllocatedMipsForVm(operator));
            totalMipsAllocated += getHost().getTotalAllocatedMipsForVm(vm);
        }

        //TODO:能耗计算需要修改，加入在传输链路上的功耗
        double timeNow = CloudSim.clock();
        double currentEnergyConsumption = getEnergyConsumption();
        double newEnergyConsumption = currentEnergyConsumption + (timeNow - lastUtilizationUpdateTime)
                * getHost().getPowerModel().getPower(lastUtilization);
//        System.out.println("id: " + getHost().getId() + "\nnew energy consumption: " + newEnergyConsumption);
        setEnergyConsumption(newEnergyConsumption);

		/*if(getName().equals("d-0")){
			System.out.println("------------------------");
			System.out.println("Utilization = "+lastUtilization);
			System.out.println("Power = "+getHost().getPowerModel().getPower(lastUtilization));
			System.out.println(timeNow-lastUtilizationUpdateTime);
		}*/

        double currentCost = getTotalCost();
        double newcost = currentCost + (timeNow - lastUtilizationUpdateTime) * getRatePerMips() * lastUtilization * getHost().getTotalMips();
        setTotalCost(newcost);

        lastUtilization = Math.min(1, totalMipsAllocated / getHost().getTotalMips());
        lastUtilizationUpdateTime = timeNow;
    }

    protected void processAppSubmit(SimEvent ev) {
        Application app = (Application) ev.getData();
        applicationMap.put(app.getAppId(), app);

//        System.out.println("==== processAppSubmit ====");
    }

    protected void addChild(int childId) {
        if (CloudSim.getEntityName(childId).toLowerCase().contains("sensor"))
            return;
        if (!getChildrenIds().contains(childId) && childId != getId())
            getChildrenIds().add(childId);
        if (!getChildToOperatorsMap().containsKey(childId))
            getChildToOperatorsMap().put(childId, new ArrayList<String>());
    }

    protected void updateCloudTraffic() {
        int time = (int) CloudSim.clock() / 1000;
        if (!cloudTrafficMap.containsKey(time))
            cloudTrafficMap.put(time, 0);
        cloudTrafficMap.put(time, cloudTrafficMap.get(time) + 1);
//        System.out.println("==== updaateCloudTraffic ====");
    }

    protected void sendTupleToActuator(Tuple tuple) {
		/*for(Pair<Integer, Double> actuatorAssociation : getAssociatedActuatorIds()){
			int actuatorId = actuatorAssociation.getFirst();
			double delay = actuatorAssociation.getSecond();
			if(actuatorId == tuple.getActuatorId()){
				send(actuatorId, delay, FogEvents.TUPLE_ARRIVAL, tuple);
				return;
			}
		}
		int childId = getChildIdForTuple(tuple);
		if(childId != -1)
			sendDown(tuple, childId);*/
        for (Pair<Integer, Double> actuatorAssociation : getAssociatedActuatorIds()) {
            int actuatorId = actuatorAssociation.getFirst();
            double delay = actuatorAssociation.getSecond();
            String actuatorType = ((Actuator) CloudSim.getEntity(actuatorId)).getActuatorType();
            if (tuple.getDestModuleName().equals(actuatorType)) {
                send(actuatorId, delay, FogEvents.TUPLE_ARRIVAL, tuple);
                return;
            }
        }

        for (int childId : getChildrenIds()) {
            sendDown(tuple, childId);
        }
//        System.out.println("==== sendTupleToActuator ====");
    }

    // 接收传来的元组
    protected void processTupleArrival(SimEvent ev) {
        Tuple tuple = (Tuple) ev.getData();
//        int currentModuleNum = getHost().getVmList().size();
//        System.out.println(getName() + ": " + currentModuleNum);

//        if (currentModuleNum >= 1) {
//            waitingQueue.add(tuple);
//        } else if (!waitingQueue.isEmpty()) {
//            Tuple tupleInQueue = waitingQueue.poll();
//            waitingQueue.remove(tupleInQueue);
//        }

//        isProcessByItself(tuple);

        //TODO:遍历当前节点的所有的子节点的任务上传队列，获取该节点待处理的任务数量
//        for (int childId : getChildrenIds()) {
//            int tupleQuantityFromChild = ((FogDevice) CloudSim.getEntity(childId)).getNorthTupleQueue().size();
//            tupleQuanityFromChildren += tupleQuantityFromChild;
//        }

        //TODO:遍历邻居节点的所有的子节点的任务上传队列，获取邻居节点待处理的任务数量
//        for (int neighborId : getNeighborIds()) {
//            int tupleQuanityOfNeighbor = 0;
//            for (int childId : ((FogDevice) CloudSim.getEntity(neighborId)).getChildrenIds()) {
//                tupleQuanityOfNeighbor += ((FogDevice) CloudSim.getEntity(childId)).getNorthTupleQueue().size();
//            }
//            tupleQuantityOfNeighbors.add(new Pair<>(neighborId, tupleQuanityOfNeighbor));
//        }
//
//        int minNeighbor = tupleQuanityFromChildren;
//        int minNeighborId = Objects.requireNonNull(getTupleQuantityOfNeighbors().poll()).getFirst();
//        while (!getTupleQuantityOfNeighbors().isEmpty()) {
//            Pair<Integer, Integer> pair = getTupleQuantityOfNeighbors().poll();
//            if (tupleQuanityFromChildren > Objects.requireNonNull(pair).getSecond()) {
//                minNeighbor = Math.min(minNeighbor, pair.getSecond());
//                minNeighborId = pair.getFirst();
//            }
//        }
//
//        if (minNeighbor < tupleQuanityFromChildren) {
//            sendNeighbor(tuple, minNeighborId);
//        }

        if (getName().equals("cloud")) {
            updateCloudTraffic();
        }

		/*if(getName().equals("d-0") && tuple.getTupleType().equals("_SENSOR")){
			System.out.println(++numClients);
		}*/
        Logger.debug(getName(), "Received tuple " + tuple.getCloudletId() + " with tupleType = " + tuple.getTupleType() + "\t| Source : " +
                CloudSim.getEntityName(ev.getSource()) + "|Dest : " + CloudSim.getEntityName(ev.getDestination()));


        // 给上传的原模块给予反馈
        send(ev.getSource(), CloudSim.getMinTimeBetweenEvents(), FogEvents.TUPLE_ACK);

        if (FogUtils.appIdToGeoCoverageMap.containsKey(tuple.getAppId())) {
        }

        //判断是否将任务传递给执行器（也就是任务是否结束）
        if (tuple.getDirection() == Tuple.ACTUATOR) {
//            if (getName().startsWith("cloud")) {
//                System.out.println(getName() + " " + tuple.getSrcModuleName() + " " + tuple.getDestModuleName() + " " + tuple.getDirection());
//            } else {
//                System.out.println(getName() + " " + tuple.getSrcModuleName() + " " + tuple.getDestModuleName() + " " + tuple.getDirection());
//            }
            sendTupleToActuator(tuple);
            //System.err.println(this.getName() + ": " + tuple.getCloudletId());
            return;
        }

        if (getHost().getVmList().size() > 0) {
            final AppModule operator = (AppModule) getHost().getVmList().get(0);
            if (CloudSim.clock() > 0) {
                //取消pe单元的分配
                getHost().getVmScheduler().deallocatePesForVm(operator);
                //添加pe单元的分配
                getHost().getVmScheduler().allocatePesForVm(operator, new ArrayList<Double>() {
                    protected static final long serialVersionUID = 1L;

                    {
                        add((double) getHost().getTotalMips());
                    }
                });
            }
        }

        //判断整个应用是否处理完成
        if (getName().equals("cloud") && tuple.getDestModuleName() == null) {
            sendNow(getControllerId(), FogEvents.TUPLE_FINISHED, null);
        }

        //TODO: 添加一个向邻居节点发送tuple的情况(id号不为3的倍数时，将任务tuple发送到邻居节点，即不在本地进行处理)
//        if (tuple.getCloudletId() % 3 != 0 && tuple.getTupleType().equals("CAMERA")) {
//            targetNeighborId = neighborIds.get(tuple.getCloudletId() % 3 - 1);
//            tuple.setToNeighbor(true);
//        }

//        if (getName().startsWith("d-0")) {
//            //TODO: 此部分计算本地计算所需花费的时间
//            double tupleCloudletLength = tuple.getCloudletLength();
//            double requestCapacity = 0;
//            for (int i = 0; i < getHost().getVmList().size(); i++) {
//                requestCapacity += getHost().getVmList().get(i).getMips();
//            }
//            double capacity = getHost().getTotalMips();
//            double estimatedTime = tupleCloudletLength / requestCapacity;
//            int minEstimateTimeId = getId();
//            double minEstimateTime = estimatedTime;
//            System.out.println("===================");
//            System.out.println("FogDeviceName: " + getName());
//            System.out.println("tupleId: " + tuple.getCloudletId());
//            System.out.println("tupleType: " + tuple.getTupleType());
//            System.out.println("tupleLength: " + tuple.getCloudletLength());
//            System.out.println("requestCapacity: " + requestCapacity);
//            System.out.println("capacity: " + capacity);
//            System.out.println("estimatedTime: " + estimatedTime);
//            System.out.println("cloudletFileSize: " + tuple.getCloudletFileSize());
//            System.out.println("neighborBandwidth: " + getNeighborBandwidth());
//
//            //TODO: 此部分计算各个邻居节点完成任务的总时间(对总时间进行排序)
//            Map<Integer, Double> neighborEstimatedTime = new HashMap<>();
////        String appId = tuple.getAppId();
//
//            for (int neighborId : getNeighborIds()) {
//                double estimatedHandlerTime = tupleCloudletLength / ((FogDevice) CloudSim.getEntity(neighborId)).getHost().getTotalMips()
//                        + getNeighborLatency().get(neighborId) + tuple.getCloudletFileSize() / getNeighborBandwidth();
//                if (minEstimateTime > estimatedHandlerTime) {
//                    minEstimateTime = estimatedHandlerTime;
//                    minEstimateTimeId = neighborId;
//                }
//                neighborEstimatedTime.put(neighborId, estimatedHandlerTime);
//                System.out.println(CloudSim.getEntityName(neighborId) + ": " + estimatedHandlerTime);
//            }
//
//            System.out.println("Tuple: " + tuple.getCloudletId() + " FogDevice: " + CloudSim.getEntityName(minEstimateTimeId));
//
//            if (!Objects.equals(CloudSim.getEntityName(minEstimateTimeId), getName())) {
//                System.out.println("Transmit to " + CloudSim.getEntityName(minEstimateTimeId) + "!");
//                tuple.setToNeighbor(true);
//                targetNeighborId = minEstimateTimeId;
//            }
//        }

        if (appToModulesMap.containsKey(tuple.getAppId())) {
            //包含该元组应用的所有模块是否能够匹配该元组的目的模块
            if ((appToModulesMap.get(tuple.getAppId()).contains(tuple.getDestModuleName()) && !tuple.isToNeighbor()) ||
                    (appToModulesMap.get(tuple.getAppId()).contains(tuple.getDestModuleName()) && tuple.isFromNeighbor())) {
                int vmId = -1;
                for (Vm vm : getHost().getVmList()) {
                    if (((AppModule) vm).getName().equals(tuple.getDestModuleName()))
                        vmId = vm.getId();
                }
                if (vmId < 0
                        || (tuple.getModuleCopyMap().containsKey(tuple.getDestModuleName()) &&
                        tuple.getModuleCopyMap().get(tuple.getDestModuleName()) != vmId)) {
                    return;
                }
                tuple.setVmId(vmId);

                //Logger.error(getName(), "Executing tuple for operator " + moduleName);

                //没有到达一个循环的结尾是不会更新时间戳的，因为没有完成一个测试循环
                updateTimingsOnReceipt(tuple);

                executeTuple(ev, tuple.getDestModuleName());
            } else if (tuple.getDestModuleName() != null) {
                if (tuple.getDirection() == Tuple.DOWN) {
                    for (int childId : getChildrenIds())
                        sendDown(tuple, childId);
                } else if (tuple.getDirection() == Tuple.UP && !tuple.isToNeighbor()) {
//                    if (getName().startsWith("d-3")) {
//                        System.out.println("used  " + tuple.getDestModuleName() + tuple.getDirection());
//                    }
//                    if (getName().startsWith("d") && tuple.getDestModuleName().equals("object_tracker")) {
//                        System.out.println(getName() + tuple.getSrcModuleName() + tuple.getDestModuleName() + tuple.getDirection());
//                    }
                    sendUp(tuple);
                } else if (tuple.getDirection() == Tuple.UP && tuple.isToNeighbor()) { //如果该节点在此步骤向邻居节点发送任务tuple
                    for (int neighborId : getNeighborIds()) {
                        if (targetNeighborId == neighborId) {
                            sendNeighbor(tuple, neighborId);
                            Logger.debug(getName(), "Sending tuple " + tuple.getCloudletId() + " with tupleType = " + tuple.getTupleType() + "\t| Source : " +
                                    CloudSim.getEntityName(this.getId()) + "|Dest : " + CloudSim.getEntityName(neighborId));
                        }
                    }
                }
            } else {
                sendUp(tuple);
            }
        } else {
            if (tuple.getDirection() == Tuple.UP) {
                sendUp(tuple);
            } else if (tuple.getDirection() == Tuple.DOWN) {

                for (int childId : getChildrenIds())
                    sendDown(tuple, childId);
//                }
            }
        }
    }

//    //TODO: 是否有设备本身来处理这个tuple
//    private boolean isProcessByItself(Tuple tuple) {
//        boolean canBe = false;
//        //获取执行tuple的appModule
//        AppModule module = getModuleByName(tuple.getDestModuleName());
//        //Vm vmModule = new Vm();
//        for (final Vm vm : getHost().getVmList()) {                //寻找该module
//            if (((AppModule) vm).getName().equals(tuple.getDestModuleName())) {
//                int num = vm.getCloudletScheduler().runningCloudlets();
//                if (vm.getCloudletScheduler().runningCloudlets() < 1) {
//                    return true;
//                }
//                double networkDelay = tuple.getCloudletFileSize() / getNeighborBandwidth();
//                double capacity = vm.getCloudletScheduler().getVmCapacity(getVmAllocationPolicy().getHost(module).getVmScheduler()
//                        .getAllocatedMipsForVm(module));
//                double totalCapacity = getVmAllocationPolicy().getHost(module).getVmScheduler().getTotalAllocatedMipsForVm(module);
//                double predictTime = tuple.getCloudletLength() / capacity;
//                System.out.println(getName() + "   moduleName:" + tuple.getDestModuleName() + "   capacity:  " + capacity + "   totalCapacity:" + totalCapacity + "  num:" + num + "  predictTime" + predictTime);
//                if (predictTime > 5 * (networkDelay + getNeighborLatency())) {
//                    return false;
//                }
//                return true;
//            }
//        }
//        //vmModule.getCloudletScheduler().get
//
//        return canBe;
//    }

    protected void updateTimingsOnReceipt(Tuple tuple) {
        Application app = getApplicationMap().get(tuple.getAppId());
        String srcModule = tuple.getSrcModuleName();
        String destModule = tuple.getDestModuleName();
        List<AppLoop> loops = app.getLoops();
        for (AppLoop loop : loops) {
            //在测试循环中且循环的尾部是目的模块
            if (loop.hasEdge(srcModule, destModule) && loop.isEndModule(destModule)) {
                Double startTime = TimeKeeper.getInstance().getEmitTimes().get(tuple.getActualTupleId());
                if (startTime == null)
                    break;
                if (!TimeKeeper.getInstance().getLoopIdToCurrentAverage().containsKey(loop.getLoopId())) {
                    TimeKeeper.getInstance().getLoopIdToCurrentAverage().put(loop.getLoopId(), 0.0);
                    TimeKeeper.getInstance().getLoopIdToCurrentNum().put(loop.getLoopId(), 0);
                }
                double currentAverage = TimeKeeper.getInstance().getLoopIdToCurrentAverage().get(loop.getLoopId());
                int currentCount = TimeKeeper.getInstance().getLoopIdToCurrentNum().get(loop.getLoopId());
                double delay = CloudSim.clock() - TimeKeeper.getInstance().getEmitTimes().get(tuple.getActualTupleId());
                TimeKeeper.getInstance().getEmitTimes().remove(tuple.getActualTupleId());
                double newAverage = (currentAverage * currentCount + delay) / (currentCount + 1);
                TimeKeeper.getInstance().getLoopIdToCurrentAverage().put(loop.getLoopId(), newAverage);
                TimeKeeper.getInstance().getLoopIdToCurrentNum().put(loop.getLoopId(), currentCount + 1);
                break;
            }
        }
    }

    protected void processSensorJoining(SimEvent ev) {
        send(ev.getSource(), CloudSim.getMinTimeBetweenEvents(), FogEvents.TUPLE_ACK);
//        System.out.println("==== processSensorJoining ====");
    }

    //处理从传感器或是其他雾设备传来的元组
    //（与功率模型（例如PowerModelLinear）相关联，它包含元组处理逻辑，其中相关的功耗模型用于根据资源利用率的变化更新设备功耗。）
    protected void executeTuple(SimEvent ev, String moduleName) {
        Tuple tuple = (Tuple) ev.getData();
        Logger.debug(getName(), "Executing tuple " + tuple.getCloudletId() + " on module " + moduleName);

//        if (getName().startsWith("cloud")) {
//            System.out.println(getName() + " " + tuple.getSrcModuleName() + " " + tuple.getDestModuleName() + " " + tuple.getDirection());
//        } else {
//            System.out.println(getName() + " " + tuple.getSrcModuleName() + " " + tuple.getDestModuleName() + " " + tuple.getDirection());
//        }

        AppModule module = getModuleByName(moduleName);


        //TODO: 如果任务是向上传递，同时任务不传递给邻居节点则执行以下操作
        if (tuple.getDirection() == Tuple.UP && tuple.isFromNeighbor()) {
            sendBackToOrignal(tuple, tuple.getBeginDeviceId());
        } else if (tuple.getDirection() == Tuple.UP && !tuple.isToNeighbor()) {
            String srcModule = tuple.getSrcModuleName();
            if (!module.getDownInstanceIdsMaps().containsKey(srcModule))
                module.getDownInstanceIdsMaps().put(srcModule, new ArrayList<Integer>());
            if (!module.getDownInstanceIdsMaps().get(srcModule).contains(tuple.getSourceModuleId()))
                module.getDownInstanceIdsMaps().get(srcModule).add(tuple.getSourceModuleId());

            int instances = -1;
            for (String _moduleName : module.getDownInstanceIdsMaps().keySet()) {
                instances = Math.max(module.getDownInstanceIdsMaps().get(_moduleName).size(), instances);
            }
            module.setNumInstances(instances);
        } else if (tuple.getDirection() == Tuple.UP && tuple.isToNeighbor()) {
            //TODO: 如果任务是向上传递但任务传递给邻居节点则执行以下操作
            //TODO：tuple发送到周围的邻接边缘节点中执行
            String srcModule = tuple.getSrcModuleName();
            if (!module.getNeighborInstanceIdsMaps().containsKey(srcModule))
                module.getNeighborInstanceIdsMaps().put(srcModule, new ArrayList<>());
            if (!module.getNeighborInstanceIdsMaps().get(srcModule).contains(tuple.getSourceModuleId()))
                module.getNeighborInstanceIdsMaps().get(srcModule).add(tuple.getSourceModuleId());

            int instances = -1;
            for (String _moduleName : module.getNeighborInstanceIdsMaps().keySet()) {
                instances = Math.max(module.getNeighborInstanceIdsMaps().get(_moduleName).size(), instances);
            }
            module.setNumInstances(instances);
        }

        TimeKeeper.getInstance().tupleStartedExecution(tuple);
        updateAllocatedMips(moduleName);
        processCloudletSubmit(ev, false);
        updateAllocatedMips(moduleName);
		/*for(Vm vm : getHost().getVmList()){
			Logger.error(getName(), "MIPS allocated to "+((AppModule)vm).getName()+" = "+getHost().getTotalAllocatedMipsForVm(vm));
		}*/
    }

    protected void processModuleArrival(SimEvent ev) {
        AppModule module = (AppModule) ev.getData();
        String appId = module.getAppId();
        if (!appToModulesMap.containsKey(appId)) {
            appToModulesMap.put(appId, new ArrayList<String>());
        }
        appToModulesMap.get(appId).add(module.getName());
        processVmCreate(ev, false);
        if (module.isBeingInstantiated()) {
            module.setBeingInstantiated(false);
        }

        initializePeriodicTuples(module);

        module.updateVmProcessing(CloudSim.clock(), getVmAllocationPolicy().getHost(module).getVmScheduler()
                .getAllocatedMipsForVm(module));
//        System.out.println("==== processModuleArrival ====");
    }

    private void initializePeriodicTuples(AppModule module) {
        String appId = module.getAppId();
        Application app = getApplicationMap().get(appId);
        List<AppEdge> periodicEdges = app.getPeriodicEdges(module.getName());
        for (AppEdge edge : periodicEdges) {
            send(getId(), edge.getPeriodicity(), FogEvents.SEND_PERIODIC_TUPLE, edge);
        }
    }

    protected void processOperatorRelease(SimEvent ev) {
        this.processVmMigrate(ev, false);
//        System.out.println("==== processOperatorRelease ====");
    }

    //如果队列不空，从队伍中取出任务执行
    protected void updateNorthTupleQueue() {
        if (!getNorthTupleQueue().isEmpty()) {
            Tuple tuple = getNorthTupleQueue().poll();
            sendUpFreeLink(tuple);
//            System.out.println("NorthTupleQueueSize: " + getNorthTupleQueue().size());
        } else {
            setNorthLinkBusy(false);
        }
//        System.out.println("==== updateNorthTupleQueue ====");
    }

    protected void sendUpFreeLink(Tuple tuple) {
        double networkDelay = tuple.getCloudletFileSize() / getUplinkBandwidth();
        setNorthLinkBusy(true);
        send(getId(), networkDelay, FogEvents.UPDATE_NORTH_TUPLE_QUEUE);
        send(parentId, networkDelay + getUplinkLatency(), FogEvents.TUPLE_ARRIVAL, tuple);
        NetworkUsageMonitor.sendingTuple(getUplinkLatency(), tuple.getCloudletFileSize());
    }

    protected void sendUp(Tuple tuple) {
        if (parentId > 0) {
            if (!isNorthLinkBusy()) {
                sendUpFreeLink(tuple);
            } else {
                northTupleQueue.add(tuple);
            }
        }
    }

    protected void updateSouthTupleQueue() {
        if (!getSouthTupleQueue().isEmpty()) {
            Pair<Tuple, Integer> pair = getSouthTupleQueue().poll();
            sendDownFreeLink(pair.getFirst(), pair.getSecond());
        } else {
            setSouthLinkBusy(false);
        }
//        System.out.println("==== updateSouthTupleQueue ====");
    }

    protected void sendDownFreeLink(Tuple tuple, int childId) {
        double networkDelay = tuple.getCloudletFileSize() / getDownlinkBandwidth();
        Logger.debug(getName(), "Sending tuple " + tuple.getCloudletId() + " with tupleType = " + tuple.getTupleType() + " DOWN");
        //TODO: 只把tuple传输给一个south子节点，为什么就说"南连接"处于繁忙阶段
        setSouthLinkBusy(true);
        double latency = getChildToLatencyMap().get(childId);
        send(getId(), networkDelay, FogEvents.UPDATE_SOUTH_TUPLE_QUEUE);
        send(childId, networkDelay + latency, FogEvents.TUPLE_ARRIVAL, tuple);
        NetworkUsageMonitor.sendingTuple(latency, tuple.getCloudletFileSize());
    }

    protected void sendDown(Tuple tuple, int childId) {
        if (getChildrenIds().contains(childId)) {
            if (!isSouthLinkBusy()) {
                sendDownFreeLink(tuple, childId);
            } else {
                southTupleQueue.add(new Pair<Tuple, Integer>(tuple, childId));
            }
        }
    }

    protected void updateNeighborTupleQueue() {
        if (!getSouthTupleQueue().isEmpty()) {
            Pair<Tuple, Integer> pair = getSouthTupleQueue().poll();
            sendNeighborFreeLink(pair.getFirst(), pair.getSecond());
        } else {
            setNeighborLinkBusy(false);
        }
//        System.out.println("==== updateNeighborTupleQueue ====");
    }

    protected void sendNeighborFreeLink(Tuple tuple, int neighborId) {
        double networkDelay = tuple.getCloudletFileSize() / getNeighborBandwidth();
        setNeighborLinkBusy(true);
        double latency = getNeighborToLatencyMap().get(neighborId);
        send(getId(), networkDelay, FogEvents.UPDATE_NEIGHBOR_TUPLE_QUEUE);
        send(neighborId, networkDelay + latency, FogEvents.TUPLE_ARRIVAL, tuple);
        tuple.setFromNeighbor(true);
        NetworkUsageMonitor.sendingTuple(latency, tuple.getCloudletFileSize());
    }

    //TODO：发送给邻居节点
    protected void sendNeighbor(Tuple tuple, int neighborId) {
        if (getNeighborIds().contains(neighborId)) {
            if (!isNeighborLinkBusy()) {
                sendNeighborFreeLink(tuple, neighborId);
            } else {
                tuple.setFromNeighbor(true);
                neighborTupleQueue.add(new Pair<>(tuple, neighborId));
            }
        }
    }

    protected void sendBackFreeLink(Tuple tuple, int originalId) {
        double networkDelay = tuple.getCloudletFileSize() / getNeighborBandwidth();
        setNeighborLinkBusy(true);
        double latency = getNeighborToLatencyMap().get(originalId);
        send(getId(), networkDelay, FogEvents.UPDATE_NEIGHBOR_TUPLE_QUEUE);
        send(originalId, networkDelay + latency, FogEvents.TUPLE_ARRIVAL, tuple);
//        tuple.setFromNeighbor(true);
        NetworkUsageMonitor.sendingTuple(latency, tuple.getCloudletFileSize());
    }

    //TODO: 完成任务后发送回初始节点
    protected void sendBackToOrignal(Tuple tuple, int originalId) {
        if (getNeighborIds().contains(originalId)) {
            if (!isNeighborLinkBusy()) {
                sendBackFreeLink(tuple, originalId);
            } else {
                neighborTupleQueue.add(new Pair<>(tuple, originalId));
            }
        }
    }

    protected void sendToSelf(Tuple tuple) {
        send(getId(), CloudSim.getMinTimeBetweenEvents(), FogEvents.TUPLE_ARRIVAL, tuple);
    }

    public PowerHost getHost() {
        return (PowerHost) getHostList().get(0);
    }

    public int getParentId() {
        return parentId;
    }

    public void setParentId(int parentId) {
        this.parentId = parentId;
    }

    public List<Integer> getNeighborIds() {
        return neighborIds;
    }

    public void setNeighborIds(List<Integer> neighborIds) {
        this.neighborIds = neighborIds;
    }

    public List<Integer> getChildrenIds() {
        return childrenIds;
    }

    public void setChildrenIds(List<Integer> childrenIds) {
        this.childrenIds = childrenIds;
    }

    public double getUplinkBandwidth() {
        return uplinkBandwidth;
    }

    public void setUplinkBandwidth(double uplinkBandwidth) {
        this.uplinkBandwidth = uplinkBandwidth;
    }

    public double getUplinkLatency() {
        return uplinkLatency;
    }

    public void setUplinkLatency(double uplinkLatency) {
        this.uplinkLatency = uplinkLatency;
    }

    public void setNeighborLatency(Map<Integer, Double> neighborLatency) {
        this.neighborLatency = neighborLatency;
    }

    public Map<Integer, Double> getNeighborLatency() {
        return neighborLatency;
    }

    public boolean isSouthLinkBusy() {
        return isSouthLinkBusy;
    }

    public void setSouthLinkBusy(boolean isSouthLinkBusy) {
        this.isSouthLinkBusy = isSouthLinkBusy;
    }

    public boolean isNorthLinkBusy() {
        return isNorthLinkBusy;
    }

    public void setNorthLinkBusy(boolean isNorthLinkBusy) {
        this.isNorthLinkBusy = isNorthLinkBusy;
    }

    public boolean isNeighborLinkBusy() {
        return isNeighborLinkBusy;
    }

    public void setNeighborLinkBusy(boolean isNeighborLinkBusy) {
        this.isNeighborLinkBusy = isNeighborLinkBusy;
    }

    public int getControllerId() {
        return controllerId;
    }

    public void setControllerId(int controllerId) {
        this.controllerId = controllerId;
    }

    public List<String> getActiveApplications() {
        return activeApplications;
    }

    public void setActiveApplications(List<String> activeApplications) {
        this.activeApplications = activeApplications;
    }

    public Map<Integer, List<String>> getChildToOperatorsMap() {
        return childToOperatorsMap;
    }

    public void setChildToOperatorsMap(Map<Integer, List<String>> childToOperatorsMap) {
        this.childToOperatorsMap = childToOperatorsMap;
    }

    public Map<String, Application> getApplicationMap() {
        return applicationMap;
    }

    public void setApplicationMap(Map<String, Application> applicationMap) {
        this.applicationMap = applicationMap;
    }

    public Queue<Tuple> getNorthTupleQueue() {
        return northTupleQueue;
    }

    public void setNorthTupleQueue(Queue<Tuple> northTupleQueue) {
        this.northTupleQueue = northTupleQueue;
    }

    public Queue<Pair<Tuple, Integer>> getSouthTupleQueue() {
        return southTupleQueue;
    }

    public void setSouthTupleQueue(Queue<Pair<Tuple, Integer>> southTupleQueue) {
        this.southTupleQueue = southTupleQueue;
    }

    public double getDownlinkBandwidth() {
        return downlinkBandwidth;
    }

    public void setDownlinkBandwidth(double downlinkBandwidth) {
        this.downlinkBandwidth = downlinkBandwidth;
    }

    public double getNeighborBandwidth() {
        return neighborBandwidth;
    }

    public void setNeighborBandwidth(double neighborBandwidth) {
        this.neighborBandwidth = neighborBandwidth;
    }

    public List<Pair<Integer, Double>> getAssociatedActuatorIds() {
        return associatedActuatorIds;
    }

    public void setAssociatedActuatorIds(List<Pair<Integer, Double>> associatedActuatorIds) {
        this.associatedActuatorIds = associatedActuatorIds;
    }

    public double getEnergyConsumption() {
        return energyConsumption;
    }

    public void setEnergyConsumption(double energyConsumption) {
        this.energyConsumption = energyConsumption;
    }

    public Map<Integer, Double> getChildToLatencyMap() {
        return childToLatencyMap;
    }

    public void setChildToLatencyMap(Map<Integer, Double> childToLatencyMap) {
        this.childToLatencyMap = childToLatencyMap;
    }

//    public Map<Integer, Map<Integer, Double>> getNeighborToLatencyMap() {
//        return neighborToLatencyMap;
//    }
//
//    public void setNeighborToLatencyMap(Map<Integer, Map<Integer, Double>> neighborToLatencyMap) {
//        this.neighborToLatencyMap = neighborToLatencyMap;
//    }

    public void setNeighborToLatencyMap(Map<Integer, Double> neighborToLatencyMap) {
        this.neighborToLatencyMap = neighborToLatencyMap;
    }

    public Map<Integer, Double> getNeighborToLatencyMap() {
        return neighborToLatencyMap;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public double getRatePerMips() {
        return ratePerMips;
    }

    public void setRatePerMips(double ratePerMips) {
        this.ratePerMips = ratePerMips;
    }

    public double getTotalCost() {
        return totalCost;
    }

    public void setTotalCost(double totalCost) {
        this.totalCost = totalCost;
    }

    public Map<String, Map<String, Integer>> getModuleInstanceCount() {
        return moduleInstanceCount;
    }

    public void setModuleInstanceCount(
            Map<String, Map<String, Integer>> moduleInstanceCount) {
        this.moduleInstanceCount = moduleInstanceCount;
    }

    public int getTupleQuanityFromChildren() {
        return tupleQuanityFromChildren;
    }

    public void setTupleQuanityFromChildren(int tupleQuanityFromChildren) {
        this.tupleQuanityFromChildren = tupleQuanityFromChildren;
    }

    public int getTupleQuanityFromParent() {
        return tupleQuanityFromParent;
    }

    public void setTupleQuanityFromParent(int tupleQuanityFromParent) {
        this.tupleQuanityFromParent = tupleQuanityFromParent;
    }

    public Queue<Pair<Integer, Integer>> getTupleQuantityOfNeighbors() {
        return tupleQuantityOfNeighbors;
    }

    public void setTupleQuantityOfNeighbors(Queue<Pair<Integer, Integer>> tupleQuantityOfNeighbors) {
        this.tupleQuantityOfNeighbors = tupleQuantityOfNeighbors;
    }
}