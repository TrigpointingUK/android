package com.trigpointinguk.android.types;

import android.location.Location;

import com.trigpointinguk.android.R;

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
		ACTIVE	 		("AC", R.drawable.t_passive		, R.drawable.ts_passive		, "Active"),
		BERNTSEN		("BE", R.drawable.t_passive		, R.drawable.ts_passive		, "Berntsen"),
		BLOCK			("BL", R.drawable.t_passive		, R.drawable.ts_passive		, "Block"),
		BOLT			("BO", R.drawable.t_passive		, R.drawable.ts_passive		, "Bolt"),
		BRASSPLATE		("BP", R.drawable.t_passive		, R.drawable.ts_passive		, "Brass Plate"),
		BURIEDBLOCK		("BB", R.drawable.t_passive		, R.drawable.ts_passive		, "Buried Block"),
		CANNON			("CA", R.drawable.t_passive		, R.drawable.ts_passive		, "Cannon"),
		CENTRE			("CE", R.drawable.t_passive		, R.drawable.ts_passive		, "Centre"),
		CONCRETERING	("CR", R.drawable.t_passive		, R.drawable.ts_passive		, "Concrete Ring"),
		CURRYSTOOL		("CS", R.drawable.t_passive		, R.drawable.ts_passive		, "Curry Stool"),
		CUT				("CT", R.drawable.t_passive		, R.drawable.ts_passive		, "Cut"),
		DISC			("DI", R.drawable.t_passive		, R.drawable.ts_passive		, "Disc"),
		FBM				("FB", R.drawable.t_fbm			, R.drawable.ts_fbm			, "FBM"),
		FENOMARK		("FE", R.drawable.t_passive		, R.drawable.ts_passive		, "Fenomark"),
		INTERSECTED		("IN", R.drawable.t_intersected	, R.drawable.ts_intersected	, "Intersected Station"),
		MONUMENT		("MO", R.drawable.t_passive		, R.drawable.ts_passive		, "Monument"),
		OTHER			("OT", R.drawable.t_passive		, R.drawable.ts_passive		, "Other"),
		PILLAR			("PI", R.drawable.t_pillar		, R.drawable.ts_pillar		, "Pillar"),
		PIPE			("PP", R.drawable.t_passive		, R.drawable.ts_passive		, "Pipe"),
		PLATFORM		("PB", R.drawable.t_passive		, R.drawable.ts_passive		, "Platform Bolt"),
		RIVET			("RI", R.drawable.t_passive		, R.drawable.ts_passive		, "Rivet"),
		SPIDER			("SP", R.drawable.t_passive		, R.drawable.ts_passive		, "Spider"),
		SURFACEBLOCK	("SB", R.drawable.t_passive		, R.drawable.ts_passive		, "Surface Block"),
		USERADDED		("UA", R.drawable.t_passive		, R.drawable.ts_passive		, "Unknown - User Added"),
		;

		private final String   code;
		private final String   descr;	
		private final int      icon;	
		private final int      icon_h;	
		Physical (String code, int icon, int icon_h, String descr) {
			this.code   = code;
			this.icon   = icon;
			this.icon_h = icon_h;
			this.descr  = descr;
		}
		public String code() {
			return code;
		}
		public int icon() {
			return icon;
		}
		public int icon(Boolean highlight) {
			if (highlight) {
				return icon_h;
			} else {
				return icon;
			}
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
		NCE				("C", "NCE Adjustment"),
		GPS				("G", "GPS Station"),
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
		GPS		 		("S", "13th order - GPS"),
		PRIMARY	 		("1", "Primary"),
		SECONDARY		("2", "Secondary"),
		THIRDORDER		("3", "3rd order"),
		FOURTHORDER		("4", "4th order"),
		ACTIVE			("A", "Active station"),
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
