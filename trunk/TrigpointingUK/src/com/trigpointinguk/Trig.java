package com.trigpointinguk;

import android.location.Location;

public class Trig extends LatLon {
	/**
	 * Mini class for trigs in list
	 */
	private static final long serialVersionUID = 1L;
	
	private String name;
	private String distance;
	private Double bearing;
	private int type;
	private int condition;
	private int found;
	
	public static final int C_U_UNKNOWN=0;
	public static final int C_G_GOOD=1;
	public static final int C_S_SLIGTLYDAMAGED=2;
	public static final int C_D_DAMAGED=3;
	public static final int C_T_TOPPLED=4;
	public static final int C_Q_POSSIBLYMISSING=5;
	public static final int C_X_DEFINITELYMISSING=6;
	public static final int C_V_UNREACHABLEBUTVISIBLE=7;
	public static final int C_P_TOTALLYUNREACHABLE=8;
	public static final int C_N_NOTLOGGED=9;
	
	
	
	public Trig() {
	}
	
	public Trig(Double lat, Double lon) {
		super(lat, lon);
	}
	public Trig(Location loc) {
		super(loc);
	}
	public Trig(Double lat, Double lon, String name) {
		super(lat, lon);
		this.name = name;
	}
	
	
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public int getType() {
		return type;
	}
	public void setType(int type) {
		this.type = type;
	}
	public int getFound() {
		return found;
	}
	public void setFound(int found) {
		this.found = found;
	}
	public int getCondition() {
		return condition;
	}
	public void setCondition(int condition) {
		this.condition = condition;
	}
	public String getDistance() {
		return distance;
	}
	public void setDistance(String distance) {
		this.distance = distance;
	}
	public Double getBearing() {
		return bearing;
	}
	public void setBearing(Double bearing) {
		this.bearing = bearing;
	}
	
}
