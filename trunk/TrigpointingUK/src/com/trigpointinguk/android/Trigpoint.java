package com.trigpointinguk.android;

import com.trigpointinguk.android.common.LatLon;

import android.location.Location;

public class Trigpoint extends LatLon {
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
	
	public static final int CONDITION_U_UNKNOWN               =  0;
	public static final int CONDITION_G_GOOD                  =  1;
	public static final int CONDITION_S_SLIGTLYDAMAGED        =  2;
	public static final int CONDITION_D_DAMAGED               =  3;
	public static final int CONDITION_T_TOPPLED               =  4;
	public static final int CONDITION_Q_POSSIBLYMISSING       =  5;
	public static final int CONDITION_X_DEFINITELYMISSING     =  6;
	public static final int CONDITION_V_UNREACHABLEBUTVISIBLE =  7;
	public static final int CONDITION_P_TOTALLYUNREACHABLE    =  8;
	public static final int CONDITION_N_NOTLOGGED             =  9;
	 
	public static final int CURRENT_UNKNOWN           =  0;
	public static final int CURRENT_PASSIVE           =  1;
	public static final int CURRENT_ACTIVE            =  2;
	public static final int CURRENT_NONE              =  3;
	
	public static final int HISTORIC_3RD_ORDER        =  0;
	public static final int HISTORIC_4TH_ORDER        =  1;
	public static final int HISTORIC_ACTIVE           =  2;
	public static final int HISTORIC_FBM              =  3;
	public static final int HISTORIC_GREAT_GLEN       =  4;
	public static final int HISTORIC_HYDROGRAPHIC     =  5;
	public static final int HISTORIC_NONE             =  6;
	public static final int HISTORIC_OTHER            =  7;
	public static final int HISTORIC_PASSIVE          =  8;
	public static final int HISTORIC_PRIMARY          =  9;
	public static final int HISTORIC_EMILY            = 10;
	public static final int HISTORIC_SECONDARY        = 11;
	public static final int HISTORIC_UNKNOWN          = 12;
	public static final int HISTORIC_USER_ADDED       = 13;
	
	public static final int PHYSICAL_ACTIVE           =  0;
	public static final int PHYSICAL_BERNTSEN         =  1;
	public static final int PHYSICAL_BLOCK            =  2;
	public static final int PHYSICAL_BOLT             =  3;
	public static final int PHYSICAL_BURIED_BLOCK     =  4;
	public static final int PHYSICAL_CANNON           =  5;
	public static final int PHYSICAL_CENTRE           =  6;
	public static final int PHYSICAL_CONCRETE_RING    =  7;
	public static final int PHYSICAL_CURRY_STOOL      =  8;
	public static final int PHYSICAL_FBM              =  9;
	public static final int PHYSICAL_FENOMARK         = 10;
	public static final int PHYSICAL_MONUMENT         = 11;
	public static final int PHYSICAL_OTHER            = 12;
	public static final int PHYSICAL_PILLAR           = 13;
	public static final int PHYSICAL_RIVER            = 14;
	public static final int PHYSICAL_SPIDER           = 15;
	public static final int PHYSICAL_SURFACE_BLOCK    = 16;
	public static final int PHYSICAL_USER_ADDED       = 17;

	
	public Trigpoint() {
	}
	
	public Trigpoint(Double lat, Double lon) {
		super(lat, lon);
	}
	public Trigpoint(Location loc) {
		super(loc);
	}
	public Trigpoint(Double lat, Double lon, String name) {
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
