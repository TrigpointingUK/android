package com.trigpointinguk.android;

import android.location.Location;

import com.trigpointinguk.android.common.LatLon;

public class Trig extends LatLon {
	/**
	 * Trigpoint class
	 */
	private static final long serialVersionUID = 1L;

	private String name;
	private String distance;
	private Double bearing;
	private int type;
	private Condition condition;
	private int found;
	
	
	// PHYSICAL TYPE of the trigpoint
	public enum Physical {
		ACTIVE	 		("AC", "Active"),
		BERNTSEN		("BE", "Berntsen"),
		BLOCK			("BL", "Block"),
		BOLT			("BO", "Bolt"),
		BURIEDBLOCK		("BB", "Buried Block"),
		CANNON			("CA", "Cannon"),
		CENTRE			("CE", "Centre"),
		CONCRETERING	("CR", "Concrete Ring"),
		CURRYSTOOL		("CS", "Curry Stool"),
		FBM				("FB", "FBM"),
		FENOMARK		("FE", "Fenomark"),
		MONUMENT		("MO", "Monument"),
		OTHER			("OT", "Other"),
		PILLAR			("PI", "Pillar"),
		RIVET			("RI", "Rivet"),
		SPIDER			("SP", "Spider"),
		SURFACEBLOCK	("SB", "Surface Block"),
		USERADDED		("UA", "Unknown - User Added"),
		;

		private final String   code;
		private final String   descr;	
		Physical (String code, String descr) {
			this.code = code;
			this.descr  = descr;
		}
		public String code() {
			return code;
		}
		public String toString() {
			return descr;
		}
		public static Physical fromCode(String code) {  
			if (code != null) {
				for (Physical c : values()) {  
					if (code.equals(c.code)) {  
						return c;  
					}  
				}  
			}
			return OTHER;
		}
	}

	// CURRENT USE of the trigpoint
	public enum Current {
		ACTIVE	 		("A", "Active"),
		PASSIVE			("P", "Passive"),
		NONE			("N", "None"),
		USERADDED		("U", "Unknown - User Added"),
		;

		private final String   code;
		private final String   descr;	
		Current (String code, String descr) {
			this.code = code;
			this.descr  = descr;
		}
		public String code() {
			return code;
		}
		public String toString() {
			return descr;
		}
		public static Current fromCode(String code) {  
			if (code != null) {
				for (Current c : values()) {  
					if (code.equals(c.code)) {  
						return c;  
					}  
				}  
			}
			return NONE;
		}
	}

	
	// HISTORIC USE of the trigpoint
	public enum Historic {
		PRIMARY	 		("1", "Primary"),
		SECONDARY		("2", "Secondary"),
		THIRDORDER		("3", "3rd order"),
		FOURTHORDER		("4", "4th order"),
		FBM				("F", "Fundamental Benchmark"),
		GREATGLEN		("G", "Great Glen Project"),
		HYDROGRAPHIC	("H", "Hydrographic Survey Pillar"),
		NONE			("N", "None"),
		OTHER			("O", "Other"),
		PASSIVE			("P", "Passive"),
		EMILY			("E", "Project Emily"),
		UNKNOWN			("Q", "Unknown"),
		USERADDED		("U", "Unknown - User Added")
		;

		private final String   code;
		private final String   descr;	
		Historic (String code, String descr) {
			this.code = code;
			this.descr  = descr;
		}
		public String code() {
			return code;
		}
		public String toString() {
			return descr;
		}
		public static Historic fromCode(String code) {  
			if (code != null) {
				for (Historic c : values()) {  
					if (code.equals(c.code)) {  
						return c;  
					}  
				}  
			}
			return UNKNOWN;
		}
	}


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
	public Condition getCondition() {
		return condition;
	}
	public void setCondition(Condition condition) {
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
