package org.cloudbus.cloudsim.sdn.example;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

public class TestForAllocation {
    private static List<Cloudlet> cloudletList;
    private static int cloudletNum = 10;
    private static List<Vm> vmList;
    private static int vmNum = 5;

    public static void main(String[] args) {
        System.out.println("Starting test.....");

        try {
            //第一步：初始化CloudSim工具包，它应该在创建任何实体之前调用
            int num_user = 1;                             // number of cloud users
            Calendar calendar = Calendar.getInstance();   // Calendar whose fields have been initialized with the current date and time.
            boolean trace_flag = false;                  // trace events
            CloudSim.init(num_user, calendar, trace_flag);

            // Second step: Create Datacenters
            // Datacenters are the resource providers in CloudSim. We need at
            // list one of them to run a CloudSim simulation

            //第二步：创建数据中心
            DataCenter dataCenter0 = createDatacenter("Datacenter_0");

            // Third step: Create Broker
            DatacenterBroker broker = createBroker();
            int brokerId = broker.getId();

            // Fourth step: Create one virtual machine
            int vmid = 0;
            int[] mipList = new int[]{278, 289, 132, 209, 286};
            long size = 10000; // image size (MB)
            int ram = 256; // vm memory (MB)
            long bw = 1000;
            int pesNumber = 1; // number of cpus
            String vmm = "Xen"; // VMM name

            vmList = new ArrayList<>();
            for (int i = 0; i < vmNum; i++) {
                vmList.add(new Vm(vmid, brokerId, mipList[i], pesNumber, ram, bw, size, vmm,
                        new CloudletSchedulerSpaceShared()));
                vmid++;
            }
            broker.submitVmList(vmList);

            //Fifth steps: Create Five Cloudlets
            int cloudletId = 0;
            long[] lengths = new long[]{19365, 49809, 32018, 44157, 16754, 18336, 20045, 31493, 30727, 31017};
            long fileSize = 300;
            long outputSize = 300;
            UtilizationModel utilizationModel = new UtilizationModelFull();

            cloudletList = new ArrayList<>();
            for (int i = 0; i < cloudletNum; i++) {
                Cloudlet cloudlet = new Cloudlet(cloudletId, lengths[i], pesNumber, fileSize, outputSize, utilizationModel,
                        utilizationModel, utilizationModel);
                cloudlet.setUserId(brokerId);
                cloudletList.add(cloudlet);

                cloudletId++;
            }
            broker.submitCloudletList(cloudletList);

            //使用贪心策略
            broker.bindCloudletToVmsSimple();

            //Six steps: 开始模拟
            CloudSim.startSimulation();

            List<Cloudlet> receivedList = broker.getCloudletReceivedList();

            //Six steps: 结束模拟
            CloudSim.stopSimulation();
            printCloudletList(receivedList);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * Creates the datacenter.
     *
     * @param name the name
     * @return the datacenter
     */
    private static DataCenter createDatacenter(String name) {

        // Here are the steps needed to create a PowerDataCenter:
        // 1. We need to create a list to store
        // our machine
        List<Host> hostList = new ArrayList<Host>();

        // 2. A Machine contains one or more PEs or CPUs/Cores.
        List<Pe> peList = new ArrayList<Pe>();

        //每秒处理1000*百万条指令
        int mips = 1000;

        // 3. Create PEs and add these into a list.
        peList.add(new Pe(0, new PeProvisionerSimple(mips))); // need to store Pe id and MIPS Rating

        // 4. Create Host with its id and list of PEs and add them to the list
        // of machines
        int hostId = 0;
        int ram = 2048; // host memory (MB)
        long storage = 1000000; // host storage
        int bw = 10000;

        hostList.add(new Host(hostId, new RamProvisionerSimple(ram), new BwProvisionerSimple(bw),
                storage, peList, new VmSchedulerTimeShared(peList))); // This is our machine

        // 5. Create a DataCenterCharacteristics object that stores the
        // properties of a data center: architecture, OS, list of
        // Machines, allocation policy: time- or space-shared, time zone
        // and its price (G$/Pe time unit).
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

        DataCenterCharacteristics characteristics = new DataCenterCharacteristics(arch, os, vmm, hostList, time_zone,
                cost, costPerMem, costPerStorage, costPerBw);

        // 6. Finally, we need to create a PowerDataCenter object.
        DataCenter dataCenter = null;
        try {
            dataCenter = new DataCenter(name, characteristics, new VmAllocationPolicySimple(hostList), storageList, 0);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return dataCenter;
    }

    // We strongly encourage users to develop their own broker policies, to
    // submit vms and cloudlets according
    // to the specific rules of the simulated scenario

    /**
     * Creates the broker.
     *
     * @return the datacenter broker
     */
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

    /**
     * Prints the Cloudlet objects.
     *
     * @param list list of Cloudlets
     */
    private static void printCloudletList(List<Cloudlet> list) {
        int size = list.size();
        Cloudlet cloudlet;

        String indent = "    ";
        Log.printLine();
        Log.printLine("========== OUTPUT ==========");
        Log.printLine("Cloudlet ID" + indent + "STATUS" + indent
                + "Data center ID" + indent + "VM ID" + indent + "Time" + indent
                + "Start Time" + indent + "Finish Time");

        DecimalFormat dft = new DecimalFormat("###.##");
        for (int i = 0; i < size; i++) {
            cloudlet = list.get(i);
            Log.print(indent + cloudlet.getCloudletId() + indent + indent);

            if (cloudlet.getCloudletStatus() == Cloudlet.SUCCESS) {
                Log.print("SUCCESS");

                Log.printLine(indent + indent + cloudlet.getResourceId()
                        + indent + indent + indent + cloudlet.getVmId()
                        + indent + indent
                        + dft.format(cloudlet.getActualCPUTime()) + indent
                        + indent + dft.format(cloudlet.getExecStartTime())
                        + indent + indent
                        + dft.format(cloudlet.getFinishTime()));
            }
        }
    }
}
