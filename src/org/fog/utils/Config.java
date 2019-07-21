package org.fog.utils;

public class Config {
	//定量地设置一些参数
	public static final double RESOURCE_MGMT_INTERVAL = 100;
	//TODO：修改最大模拟时间（原来的最大运行时间为10000）
	public static int MAX_SIMULATION_TIME = 10000;
	public static int RESOURCE_MANAGE_INTERVAL = 100;
	public static String FOG_DEVICE_ARCH = "x86";
	public static String FOG_DEVICE_OS = "Linux";
	public static String FOG_DEVICE_VMM = "Xen";
	public static double FOG_DEVICE_TIMEZONE = 10.0;
	public static double FOG_DEVICE_COST = 3.0;
	public static double FOG_DEVICE_COST_PER_MEMORY = 0.05;
	public static double FOG_DEVICE_COST_PER_STORAGE = 0.001;
	public static double FOG_DEVICE_COST_PER_BW = 0.0;
}
