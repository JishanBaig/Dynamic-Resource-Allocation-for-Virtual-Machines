package org.cloudbus.cloudsim.examples.power;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.CloudletSchedulerDynamicWorkload;
import org.cloudbus.cloudsim.DatacenterBroker;
import org.cloudbus.cloudsim.DatacenterCharacteristics;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.UtilizationModelStochastic;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.VmSchedulerTimeShared;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.PowerDatacenter;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.power.PowerPe;
import org.cloudbus.cloudsim.power.PowerVmAllocationPolicySingleThreshold;
import org.cloudbus.cloudsim.power.models.PowerModelLinear;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;

public class SingleThreshold {

	/** The cloudlet list. */
	private static List<Cloudlet> cloudletList;

	/** The vm list. */
	private static List<Vm> vmList;

	private static double utilizationThreshold = 0.8;

	private static double hostsNumber = 2;
	private static double vmsNumber = 4;
	private static double cloudletsNumber = 4;

	public static void main(String[] args) {

		Log.printLine("Starting SingleThreshold example...");

		try {
			
			int num_user = 1; // number of cloud users
			Calendar calendar = Calendar.getInstance();
			boolean trace_flag = false; // mean trace GridSim events

			// Initialize the CloudSim library
			CloudSim.init(num_user, calendar, trace_flag);

			
			PowerDatacenter datacenter = createDatacenter("Datacenter_0");

			//Create Broker
			DatacenterBroker broker = createBroker();
			int brokerId = broker.getId();

			// Create one virtual machine
			vmList = createVms(brokerId);

			// submit vm list to the broker
			broker.submitVmList(vmList);

			//Create one cloudlet
			cloudletList = createCloudletList(brokerId);

			// submit cloudlet list to the broker
			broker.submitCloudletList(cloudletList);

			//Starts the simulation
			double lastClock = CloudSim.startSimulation();

			//Print results when simulation is over
			List<Cloudlet> newList = broker.getCloudletReceivedList();
			Log.printLine("Received " + newList.size() + " cloudlets");

			CloudSim.stopSimulation();

			printCloudletList(newList);

		    int totalTotalRequested = 0;
		    int totalTotalAllocated = 0;
		    ArrayList<Double> sla = new ArrayList<Double>();
		    int numberOfAllocations = 0;
			for (Entry<String, List<List<Double>>> entry : datacenter.getUnderAllocatedMips().entrySet()) {
			    List<List<Double>> underAllocatedMips = entry.getValue();
			    double totalRequested = 0;
			    double totalAllocated = 0;
		
                for (List<Double> mips : underAllocatedMips) {
			    	if (mips.get(0) != 0) {
			    		numberOfAllocations++;
			    		totalRequested += mips.get(0);
			    		totalAllocated += mips.get(1);
			    		double _sla = (mips.get(0) - mips.get(1)) / mips.get(0) * 100;
			    		if (_sla > 0) {
			    			sla.add(_sla);
			    		}
			    	}
				}
			    totalTotalRequested += totalRequested;
			    totalTotalAllocated += totalAllocated;
			}

			double averageSla = 0;
			if (sla.size() > 0) {
			    double totalSla = 0;
			    for (Double _sla : sla) {
			    	totalSla += _sla;
				}
			    averageSla = totalSla / sla.size();
			}

			Log.printLine();
			Log.printLine(String.format("Total simulation time: %.2f sec", lastClock));
			Log.printLine(String.format("Energy consumption: %.2f kWh", datacenter.getPower() / (3600 * 1000)));
			Log.printLine(String.format("Number of VM migrations: %d", datacenter.getMigrationCount()));
			Log.printLine(String.format("Number of SLA violations: %d", sla.size()));
			Log.printLine(String.format("SLA violation percentage: %.2f%%", (double) sla.size() * 100 / numberOfAllocations));
			Log.printLine(String.format("Average SLA violation: %.2f%%", averageSla));
			Log.printLine();

		} catch (Exception e) {
			e.printStackTrace();
			Log.printLine("Unwanted errors happen");
		}

		Log.printLine("SingleThreshold finished!");
	}

	
	private static List<Cloudlet> createCloudletList(int brokerId) {
		List<Cloudlet> list = new ArrayList<Cloudlet>();

		long length = 150000; // 10 min on 250 MIPS
		int pesNumber = 1;
		long fileSize = 300;
		long outputSize = 300;

		for (int i = 0; i < cloudletsNumber; i++) {
			Cloudlet cloudlet = new Cloudlet(i, length, pesNumber, fileSize, outputSize, new UtilizationModelStochastic(), new UtilizationModelStochastic(), new UtilizationModelStochastic());
			cloudlet.setUserId(brokerId);
			cloudlet.setVmId(i);
			list.add(cloudlet);
		}

		return list;
	}

	
	private static List<Vm> createVms(int brokerId) {
		List<Vm> vms = new ArrayList<Vm>();

		// VM description
		int[] mips = { 250, 500, 750, 1000 }; // MIPSRating
		int pesNumber = 1; // number of cpus
		int ram = 128; // vm memory (MB)
		long bw = 2500; // bandwidth
		long size = 2500; // image size (MB)
		String vmm = "Xen"; // VMM name

		for (int i = 0; i < vmsNumber; i++) {
			vms.add(
				new Vm(i, brokerId, mips[i % mips.length], pesNumber, ram, bw, size, vmm, new CloudletSchedulerDynamicWorkload(mips[i % mips.length], pesNumber))
			);
		}

		return vms;
	}

	
	private static PowerDatacenter createDatacenter(String name) throws Exception {
		
		List<PowerHost> hostList = new ArrayList<PowerHost>();

		double maxPower = 250; // 250W
		double staticPowerPercent = 0.7; // 70%

		int[] mips = { 1000, 2000, 3000 };
		int ram = 10000; // host memory (MB)
		long storage = 1000000; // host storage
		int bw = 100000;

		for (int i = 0; i < hostsNumber; i++) {
			
			List<PowerPe> peList = new ArrayList<PowerPe>();
			peList.add(new PowerPe(0, new PeProvisionerSimple(mips[i % mips.length]), new PowerModelLinear(maxPower, staticPowerPercent))); // need to store PowerPe id and MIPS Rating

			
			hostList.add(
				new PowerHost(
					i,
					new RamProvisionerSimple(ram),
					new BwProvisionerSimple(bw),
					storage,
					peList,
					new VmSchedulerTimeShared(peList)
				)
			);
		}

		
		String arch = "x86"; // system architecture
		String os = "Linux"; // operating system
		String vmm = "Xen";
		double time_zone = 10.0; // time zone this resource located
		double cost = 3.0; // the cost of using processing in this resource
		double costPerMem = 0.05; // the cost of using memory in this resource
		double costPerStorage = 0.001; // the cost of using storage in this resource
		double costPerBw = 0.0; // the cost of using bw in this resource

		DatacenterCharacteristics characteristics = new DatacenterCharacteristics(
				arch, os, vmm, hostList, time_zone, cost, costPerMem, costPerStorage, costPerBw);

		PowerDatacenter powerDatacenter = null;
		try {
			powerDatacenter = new PowerDatacenter(
					name,
					characteristics,
					new PowerVmAllocationPolicySingleThreshold(hostList, utilizationThreshold),
					new LinkedList<Storage>(),
					5.0);
		} catch (Exception e) {
			e.printStackTrace();
		}

		return powerDatacenter;
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

	
	private static void printCloudletList(List<Cloudlet> list) {
		int size = list.size();
		Cloudlet cloudlet;

		String indent = "\t";
		Log.printLine();
		Log.printLine("========== OUTPUT ==========");
		Log.printLine("Cloudlet ID" + indent + "STATUS" + indent
				+ "Resource ID" + indent + "VM ID" + indent + "Time" + indent
				+ "Start Time" + indent + "Finish Time");

		DecimalFormat dft = new DecimalFormat("###.##");
		for (int i = 0; i < size; i++) {
			cloudlet = list.get(i);
			Log.print(indent + cloudlet.getCloudletId());

			if (cloudlet.getCloudletStatus() == Cloudlet.SUCCESS) {
				Log.printLine(indent + "SUCCESS"
					+ indent + indent + cloudlet.getResourceId()
					+ indent + cloudlet.getVmId()
					+ indent + dft.format(cloudlet.getActualCPUTime())
					+ indent + dft.format(cloudlet.getExecStartTime())
					+ indent + indent + dft.format(cloudlet.getFinishTime())
				);
			}
		}
	}

}
