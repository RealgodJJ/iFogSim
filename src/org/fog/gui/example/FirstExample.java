package org.fog.gui.example;

//Cloud-Target:
//1.Create a DataCenter(Include only one Host);
//2.Create 3 Vms for the Host;
//3.Create 6 Pes for the Host;
//4.Create 4 cloudlets for Host;

//Fog-Target:
//1.Create four FogDevice under the Host;
//2.Create 3, 4, 3, 4 Vm for the FogDevice;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.provisioners.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class FirstExample {
    //The cloudlet list.
    private static List<Cloudlet> cloudletList;
    //The host list.
    private static List<Host> hostList;
    //The vm list.
    private static List<Vm> vmList;
    //The pe list.
    private static List<Pe> peList;

    public static void main(String[] args) {
//        try {
        Log.printLine("Starting FirstExample...");

        //First Step: init CloudSim tools(它应该在创建任何实体之前调用)
        int num_user = 1;                             // number of cloud users
        Calendar calendar = Calendar.getInstance();   // Calendar whose fields have been initialized with the current date and time.
        boolean trace_flag = false;                  // trace events
        CloudSim.init(num_user, calendar, trace_flag);

        //Second Step: Create a DataCenter.
        DataCenter dataCenter = getDataCenter("CloudDataCenter");

        //Third step: Create Broker
        DatacenterBroker broker = createBroker();
        int brokerId = broker.getId();

        //Fourth step: Create 3 vm
        vmList = new ArrayList<>();

        // VM description
        int vmIdList[] = {0, 1, 2};
        int mipsList[] = {1800, 700, 800};
        int pesNumber = peList.size(); // number of cpus
        int ramList[] = {512, 128, 256, 128}; // vm memory (MB)
        long bw = 1000;
        long size = 25 * 10000; // image size (MB)
        String vmm[] = {"VMM_0", "VMM_1", "VMM_2"}; // VMM name

        for (int i = 0; i < vmIdList.length; i++) {
            Vm vm = new Vm(vmIdList[i], brokerId, mipsList[i], pesNumber / vmIdList.length, ramList[i],
                    bw, size, vmm[i], new CloudletSchedulerTimeShared());
            vmList.add(vm);
        }
        broker.submitVmList(vmList);

        //Fifth Step: Create 10 Cloudlets.
        cloudletList = new ArrayList<>();
        int cloudletIdList[] = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        long cloudletLengthList[] = {400000, 300000, 500000, 200000, 400000, 100000, 400000, 300000, 100000, 200000};
        long fileSizeList[] = {256, 1024, 2048, 512, 256, 2048, 1024, 512, 1024, 128};
        long outputSizeList[] = {128, 256, 512, 512, 256, 512, 1024, 128, 256, 512};
        UtilizationModel utilizationModelRam = new UtilizationModelFull();
        UtilizationModel utilizationModelCpu = new UtilizationModelFull();
        UtilizationModel utilizationModelBw = new UtilizationModelFull();
//        try {
//            utilizationModelCpu = new UtilizationModelPlanetLabInMemory("", 0, 300);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
        boolean record = false;
        for (int i = 0; i < cloudletIdList.length; i++)
        //Id, length, pesNumber, FileSize, OutputSize, CPU, RAM, Bw
        {
            Cloudlet cloudlet = new Cloudlet(cloudletIdList[i], cloudletLengthList[i], pesNumber, fileSizeList[i],
                    outputSizeList[i], utilizationModelCpu, utilizationModelRam, utilizationModelBw, record);
            cloudlet.setUserId(brokerId);
            if ((i + 1) % vmIdList.length != 0)
                cloudlet.setVmId(vmIdList[(i + 1) % vmIdList.length - 1]);
            else
                cloudlet.setVmId(vmIdList[vmIdList.length - 1]);

            cloudletList.add(cloudlet);
            broker.submitCloudletList(cloudletList);
        }

        // Sixth step: Starts the simulation
        CloudSim.startSimulation();

        CloudSim.stopSimulation();
    }

    private static DataCenter getDataCenter(String dataCenterName) {
        //1.Create 6 Pes in PeList.
        peList = new ArrayList<>();
        int peIdList[] = {0, 1, 2, 3, 4, 5};
        int mips[] = {1800, 2000, 3000, 1500, 2500, 3500};
        for (int i = 0; i < peIdList.length; i++)
            peList.add(new Pe(peIdList[i], new PeProvisionerSimple(mips[i])));

        //2.Create a Host in cloud.
        int hostId = 0;
        int ram = 16 * 1024;
        int bw = 10000;
        int storage = 1000000;

        //The "VmSchedulerTimeShared" can be changed
        Host cloudHost = new Host(hostId, new RamProvisionerSimple(ram), new BwProvisionerSimple(bw), storage,
                peList, new VmSchedulerTimeShared(peList));
        hostList = new ArrayList<>();
        hostList.add(cloudHost);

        //3.Set the characteristics of DataCenter(DataCenterCharacteristics).
        String architecture = "x86";
        String os = "Linux";
        String vmm = "vmm_host";
        double timeZone = 10.0; // time zone this resource located(I don't care about it.)
        double costPerSecond = 3.0; // the cost of using processing in this resource
        double costPerMem = 0; // the cost of using memory in this resource(I don't care about it)
        double costPerStorage = 0; // the cost of using storage in this(I don't care about it)
        double costPerBw = 0.0; // the cost of using bw in this resource(I don't care about it)
        DataCenterCharacteristics dataCenterCharacteristics = new DataCenterCharacteristics(architecture, os, vmm,
                hostList, timeZone, costPerSecond, costPerMem, costPerStorage, costPerBw);

        //4.Create a DataCenter.
        List<Storage> hostStorageList = new ArrayList<>();
        try {
            hostStorageList.add(new HarddriveStorage("HostStorage", storage));
        } catch (ParameterException e) {
            e.printStackTrace();
        }
        double schedulingInterval = 0;
        DataCenter dataCenter = null;
        try {
            dataCenter = new DataCenter(dataCenterName, dataCenterCharacteristics,
                    new VmAllocationPolicySimple(hostList), hostStorageList, schedulingInterval);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return dataCenter;
    }

    private static DatacenterBroker createBroker() {
        DatacenterBroker broker = null;
        try {
            broker = new DatacenterBroker("Broker");
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return broker;
    }
}
